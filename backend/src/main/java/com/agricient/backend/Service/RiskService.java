package com.agricient.backend.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agricient.backend.Model.WeatherReading;
import com.agricient.backend.Repository.WeatherRepository;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;

@Service
public class RiskService {

    private final WeatherRepository repository;
    private final WeatherService weather;
    private OrtEnvironment env;
    public static final int[] HORIZONS = { 3, 7, 14 };

    private final Map<Integer, OrtSession> sessions = new LinkedHashMap<>();

    public RiskService(WeatherRepository repository, WeatherService weather) {
        this.repository = repository;
        this.weather = weather;
    }

    public record Forecast(String site, LocalDate lastDay, double riskToday,
            LocalDate predictedFor, double predictedRisk) {
    }

    /** One look-ahead: the risk predicted for a date this many days after the last full day of weather. */
    public record Outlook(int days, LocalDate predictedFor, double predictedRisk) {
    }

    @PostConstruct
    public void load() throws OrtException, java.io.IOException {
        env = OrtEnvironment.getEnvironment();
        for (int days : HORIZONS) {
            // the 3-day model keeps its original file name
            String file = days == 3 ? "risk_model.onnx" : "risk_model_" + days + ".onnx";
            sessions.put(days, env.createSession(ModelFiles.extract(file).toString()));
        }
    }

    public double predict(float[] features) throws OrtException {
        return predict(features, 3);
    }

    public double predict(float[] features, int days) throws OrtException {
        OrtSession session = sessions.get(days);
        if (session == null) {
            throw new IllegalArgumentException("no model for " + days + " days");
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, new float[][] { features });
                OrtSession.Result result = session.run(Map.of("features", tensor))) {
            float[][] out = (float[][]) result.get(0).getValue();
            return out[0][0];
        }
    }

    /** Forecast for any coordinates: fetch recent weather for that point if needed, then run the same model. */
    public Forecast forecastForPoint(double lat, double lon) throws OrtException {
        String key = WeatherService.placeKey(lat, lon);
        weather.ensureRecent(key, lat, lon);
        return forecastFor(key);
    }

    public Forecast forecastFor(String site) throws OrtException {
        var latest = repository.findFirstByLocationOrderByReadingTimeDesc(site);
        if (latest.isEmpty()) {
            throw new IllegalStateException("no weather stored for " + site);
        }
        LocalDate end = latest.get().getReadingTime().toLocalDate();
        LocalDateTime from = end.minusDays(10).atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusSeconds(1);

        List<WeatherReading> hourly = repository.findByLocationAndReadingTimeBetween(site, from, to);
        List<RiskCalc.Day> days = RiskCalc.toDays(hourly);
        float[] features = RiskCalc.features(days);

        RiskCalc.Day today = days.get(days.size() - 1);
        double predicted = predict(features);
        return new Forecast(site, today.date(), today.risk(), today.date().plusDays(3), predicted);
    }

    /** The 3, 7 and 14 day outlooks for one place. The features are built once and shared by all three models. */
    public List<Outlook> outlookFor(String site) throws OrtException {
        var latest = repository.findFirstByLocationOrderByReadingTimeDesc(site);
        if (latest.isEmpty()) {
            throw new IllegalStateException("no weather stored for " + site);
        }
        LocalDate end = latest.get().getReadingTime().toLocalDate();
        LocalDateTime from = end.minusDays(10).atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusSeconds(1);

        List<RiskCalc.Day> days = RiskCalc.toDays(repository.findByLocationAndReadingTimeBetween(site, from, to));
        float[] features = RiskCalc.features(days);
        LocalDate today = days.get(days.size() - 1).date();

        List<Outlook> out = new java.util.ArrayList<>();
        for (int h : HORIZONS) {
            out.add(new Outlook(h, today.plusDays(h), predict(features, h)));
        }
        return out;
    }

    /** Outlooks for any coordinates: make sure recent weather is stored, then run all three models. */
    public List<Outlook> outlookForPoint(double lat, double lon) throws OrtException {
        String key = WeatherService.placeKey(lat, lon);
        weather.ensureRecent(key, lat, lon);
        return outlookFor(key);
    }
}