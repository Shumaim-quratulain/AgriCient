package com.agricient.backend.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;


import com.agricient.backend.Prediction;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import jakarta.annotation.PostConstruct;

@Service
public class ModelService {

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> labels;
    @PostConstruct
    public void load() throws OrtException, IOException {
        env = OrtEnvironment.getEnvironment();
        Path modelPath = ModelFiles.extract("plantvillage_mobilenet_v3_small.onnx");
        session = env.createSession(modelPath.toString());
        labels = ModelFiles.readLines("plantvillage_classes.txt");
    }
    public List<String> crops() {
    TreeSet<String> names = new TreeSet<>();
    for (String label : labels) {
        names.add(label.split("___")[0]);
    }
    return new ArrayList<>(names);
    }

    public boolean isNotCoconut(String label) {
        return label.equals("NotCoconut");
    }

    public String diseaseOf(String label) {
        String[] parts = label.split("___");
        return parts.length > 1 ? parts[1] : label;
    }

    // Free text from the farmer, so match loosely: ignore case and allow a part of the name
    // ("tomato" matches "Tomato", "corn" matches "Corn_(maize)")
    private boolean cropMatches(String label, String typed) {
        String wanted = typed.trim().toLowerCase();
        String actual = label.split("___")[0].toLowerCase();
        return actual.equals(wanted) || actual.contains(wanted);
    }

    public float[] softmaxWithinCrop(float[] logits, String crop) {
        float[] masked = new float[logits.length];
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) {
            if (cropMatches(labels.get(i), crop)) {
                masked[i] = logits[i];
                max = Math.max(max, logits[i]);
            } else {
                masked[i] = Float.NEGATIVE_INFINITY;
            }
        }
        if (max == Float.NEGATIVE_INFINITY) {
            return masked;
        }
        float sum = 0;
        for (int i = 0; i < masked.length; i++) {
            masked[i] = masked[i] == Float.NEGATIVE_INFINITY ? 0f : (float) Math.exp(masked[i] - max);
            sum += masked[i];
        }
        for (int i = 0; i < masked.length; i++) {
            masked[i] = masked[i] / sum;
        }
        return masked;
    }

    public float safetyFor(float[] probs, String crop) {
    float sum = 0;
    boolean hasHealthy = false;
    for (int i = 0; i < probs.length; i++) {
        String label = labels.get(i);
        if (cropMatches(label, crop) && isHealthy(label)) {
            sum += probs[i];
            hasHealthy = true;
        }
    }
    return hasHealthy ? sum : -1f;
}
public boolean hasHealthyClass(String label) {
    String crop = label.split("___")[0];
    return labels.stream().anyMatch(l -> l.startsWith(crop + "___") && isHealthy(l));
}
public boolean isHealthy(String label) {
    String disease = label.split("___")[1].toLowerCase();
    return disease.contains("healthy") && !disease.contains("unhealthy") && !disease.contains("not_healthy");
}
    public String describe() {
        return "inputs=" + session.getInputNames() + " outputs=" + session.getOutputNames();
    }

    public float[] run(float[][][][] input) throws OrtException {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input);
            OrtSession.Result result = session.run(Map.of("image", tensor))) {
            float[][] logits = (float[][]) result.get(0).getValue();
            return logits[0];
        }
    }
    public float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            max = Math.max(max, v);
        }
        float[] probs = new float[logits.length];
        float sum = 0;
        for (int i = 0; i < logits.length; i++) {
            probs[i] = (float) Math.exp(logits[i] - max);
            sum += probs[i];
        }
        for (int i = 0; i < probs.length; i++) {
            probs[i] = probs[i] / sum;
        }
        return probs;
    }
    public List<Prediction> top5(float[] probs) {
        Integer[] indexes = new Integer[probs.length];
        for (int i = 0; i < probs.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, (a, b) -> Float.compare(probs[b], probs[a]));

        List<Prediction> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int index = indexes[i];
            result.add(new Prediction(labels.get(index), probs[index]));
        }
        return result;
    }
            public float safetyAll(float[] probs) {
        float sum = 0;
        for (int i = 0; i < probs.length; i++) {
            if (isHealthy(labels.get(i))) {
                sum += probs[i];
            }
        }
        return sum;
    }

    public String riskLevel(float safetyPercent) {
        if (safetyPercent >= 80) {
            return "SAFE";
        }
        if (safetyPercent >= 50) {
            return "AT RISK";
        }
        return "HIGH RISK";
    }
}
