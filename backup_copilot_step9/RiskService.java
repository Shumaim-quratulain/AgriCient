package com.agricient.backend.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.agricient.backend.Model.WeatherReading;
import com.agricient.backend.Repository.WeatherRepository;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;

/**
 * Coconut disease-weather risk. Rebuilds the 11 features exactly as
 * ai-services/forecaster/data/{features,risk,lags}.py does, then asks risk_model.onnx
 * for the risk three days after the last complete day.
 */
@Service
public class RiskService {

    // Same constants as risk.py
    private static final double WET_WEIGHT = 0.5;
    private static final double RAIN_WEIGHT = 0.3;
    private static final double TEMP_WEIGHT = 0.2;
    private static final double RAIN_CAP_MM = 20;
    private static final double TEMP_LOW = 22;
    private static final double TEMP_HIGH = 28;

    /** Days of history the features need: the 7-day sums. */
    private static final int WINDOW = 7;

    private final WeatherRepository repository;
    private OrtEnvironment env;
    private OrtSession session;

    public RiskService(WeatherRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void load() throws OrtException {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(Path.of("src/main/resources/risk_model.onnx").toString());
    }

    /** One calendar day, summarised from 24 hourly readings (features.py). */
    public record Day(LocalDate date, double tMean, int rh90Hours, double rainSum, double risk) {
    }

    public record Forecast(String site, LocalDate lastDay, double riskToday,
            float[] features, double predictedRisk, LocalDate predictedFor) {
    }

    public static double risk(double tMean, int rh90Hours, double rainSum) {
        double wet = rh90Hours / 24.0;
        double rain = Math.min(rainSum / RAIN_CAP_MM, 1.0);
        double temp = (tMean >= TEMP_LOW && tMean <= TEMP_HIGH) ? 1.0 : 0.3;
        return WET_WEIGHT * wet + RAIN_WEIGHT * rain + TEMP_WEIGHT * temp;
    }

    /** Group hourly rows into days. Days without all 24 hours are left out, never guessed. */
    public static List<Day> toDays(List<WeatherReading> hourly) {
        Map<LocalDate, List<WeatherReading>> byDay = new TreeMap<>();
        for (WeatherReading r : hourly) {
            byDay.computeIfAbsent(r.getReadingTime().toLocalDate(), k -> new ArrayList<>()).add(r);
        }
        List<Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<WeatherReading>> e : byDay.entrySet()) {
            List<WeatherReading> rows = e.getValue();
            if (rows.size() != 24) {
                continue;
            }
            double tSum = 0;
            int tCount = 0;
            int rh90 = 0;
            double rain = 0;
            for (WeatherReading r : rows) {
                if (r.getTemperature() != null) {      // pandas mean/sum skip missing values
                    tSum += r.getTemperature();
                    tCount++;
                }
                if (r.getHumidity() != null && r.getHumidity() >= 90) {
                    rh90++;
                }
                if (r.getPrecipitation() != null) {
                    rain += r.getPrecipitation();
                }
            }
            if (tCount == 0) {
                continue;
            }
            double tMean = tSum / tCount;
            days.add(new Day(e.getKey(), tMean, rh90, rain, risk(tMean, rh90, rain)));
        }
        return days;
    }

    /**
     * The 11 features for the last day of the list, in the order of lags.py FEATURES.
     * Needs WINDOW consecutive complete days ending at that day.
     */
    public static float[] features(List<Day> days) {
        if (days.size() < WINDOW) {
            throw new IllegalStateException("need " + WINDOW + " complete days, have " + days.size());
        }
        List<Day> w = days.subList(days.size() - WINDOW, days.size());
        for (int i = 1; i < w.size(); i++) {
            if (!w.get(i).date().equals(w.get(i - 1).date().plusDays(1))) {
                throw new IllegalStateException("gap in weather days before " + w.get(i).date());
            }
        }
        Day today = w.get(WINDOW - 1);
        double rain3 = 0, rain7 = 0;
        int rh3 = 0, rh7 = 0;
        for (int i = 0; i < WINDOW; i++) {
            Day d = w.get(i);
            rain7 += d.rainSum();
            rh7 += d.rh90Hours();
            if (i >= WINDOW - 3) {
                rain3 += d.rainSum();
                rh3 += d.rh90Hours();
            }
        }
        return new float[] {
            (float) today.risk(),
            (float) w.get(WINDOW - 2).risk(),
            (float) w.get(WINDOW - 3).risk(),
            (float) today.tMean(),
            today.rh90Hours(),
            (float) today.rainSum(),
            (float) rain3,
            (float) rain7,
            rh3,
            rh7,
            today.date().getMonthValue()
        };
    }

    public double predict(float[] features) throws OrtException {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, new float[][] { features });
                OrtSession.Result result = session.run(Map.of("features", tensor))) {
            float[][] out = (float[][]) result.get(0).getValue();
            return out[0][0];
        }
    }

    public Forecast forecast(String site) throws OrtException, IOException {
        var latest = repository.findFirstByLocationOrderByReadingTimeDesc(site);
        if (latest.isEmpty()) {
            throw new IllegalStateException("no weather stored for " + site);
        }
        LocalDate end = latest.get().getReadingTime().toLocalDate();
        LocalDateTime from = end.minusDays(WINDOW + 3).atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusSeconds(1);
        List<Day> days = toDays(repository.findByLocationAndReadingTimeBetween(site, from, to));
        float[] f = features(days);
        Day today = days.get(days.size() - 1);
        double predicted = predict(f);
        return new Forecast(site, today.date(), today.risk(), f, predicted, today.date().plusDays(3));
    }
}
