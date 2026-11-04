package com.agricient.backend.Controller;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.agricient.backend.ImagePreprocessor;
import com.agricient.backend.Prediction;
import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.DiagnoseResponse;
import com.agricient.backend.Model.Diagnosis;
import com.agricient.backend.Repository.DiagnosisRepository;
import com.agricient.backend.Repository.LandRepository;
import com.agricient.backend.Service.AlertService;
import com.agricient.backend.Service.AuthService;
import com.agricient.backend.Service.ModelService;

import ai.onnxruntime.OrtException;

@RestController
public class DiagnoseController {

    private final DiagnosisRepository repository;
    private final ImagePreprocessor preprocessor;
    private final ModelService modelService;
    private final AuthService auth;
    private final LandRepository lands;
    private final AlertService alerts;

    public DiagnoseController(ImagePreprocessor preprocessor, ModelService modelService,
        DiagnosisRepository repository, AuthService auth, LandRepository lands, AlertService alerts) {
            this.preprocessor = preprocessor;
            this.modelService = modelService;
            this.repository = repository;
            this.auth = auth;
            this.lands = lands;
            this.alerts = alerts;
    }

    @GetMapping("/api/crops")
    public List<String> crops() {
        return modelService.crops();
    }

    @GetMapping("/api/diagnoses")
    public List<Diagnosis> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(auth.require(authorization).getId());
    }

    @DeleteMapping("/api/diagnoses")
    @Transactional
    public void clearHistory(@RequestHeader(value = "Authorization", required = false) String authorization) {
        repository.deleteByOwnerId(auth.require(authorization).getId());
    }

    @PostMapping("/api/diagnose")
    public DiagnoseResponse diagnose(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "crop", required = false) String crop,
            @RequestParam(value = "landId", required = false) Long landId)
            throws IOException, OrtException {
        AppUser user = auth.require(authorization);
        // a land id that is not the caller's own is treated as no land at all
        Long ownLandId = landId == null ? null
                : lands.findByIdAndOwnerId(landId, user.getId()).map(l -> l.getId()).orElse(null);
        if (landId != null && ownLandId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Land not found.");
        }
        BufferedImage image = preprocessor.resize(file.getBytes());
        float[][][][] input = preprocessor.toArray(image);
        float[] logits = modelService.run(input);

        String mode = (crop == null || crop.isBlank()) ? "auto" : "crop";
        float[] probs = mode.equals("crop")
                ? modelService.softmaxWithinCrop(logits, crop)
                : modelService.softmax(logits);

        List<Prediction> top5 = modelService.top5(probs);
        float best = top5.get(0).confidence();
        if (modelService.isNotCoconut(top5.get(0).label())) {
            repository.save(new Diagnosis(file.getOriginalFilename(), "NotCoconut", best, user.getId(), ownLandId));
            return new DiagnoseResponse(mode, crop, null, best, "NOT COCONUT", -1f, top5);
        }
        float rawSafety = mode.equals("crop")
        ? modelService.safetyFor(probs, crop)
        : (modelService.hasHealthyClass(top5.get(0).label()) ? modelService.safetyAll(probs) : -1f);
float safety = rawSafety < 0 ? -1f : rawSafety * 100;

        // 0.80 for coconut crop mode was measured on 677 held-out photos; 0.60 elsewhere is unmeasured
        boolean coconutMode = mode.equals("crop") && crop.trim().toLowerCase().contains("coconut");
        float minConfidence = coconutMode ? 0.80f : 0.60f;
        String risk = best < minConfidence ? "UNKNOWN" : (safety < 0 ? "NOT AVAILABLE" : modelService.riskLevel(safety));
        String disease = mode.equals("crop") ? modelService.diseaseOf(top5.get(0).label()) : null;

        repository.save(new Diagnosis(file.getOriginalFilename(), top5.get(0).label(), best, user.getId(), ownLandId));
        if (ownLandId != null) {
            alerts.evaluateFarmerSoon(user.getId());   // a new photo can raise or confirm an alert straight away
        }
        return new DiagnoseResponse(mode, crop, disease, best, risk, safety, top5);
    }
}