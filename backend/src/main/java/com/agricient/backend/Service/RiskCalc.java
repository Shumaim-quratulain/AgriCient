package com.agricient.backend.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.agricient.backend.Model.WeatherReading;

/**
 * Plain calculations for the coconut risk score. No database, no model.
 * Mirrors ai-services/forecaster/data/features.py and risk.py.
 */
public class RiskCalc {

    // Same constants as risk.py
    static final double WET_WEIGHT = 0.5;
    static final double RAIN_WEIGHT = 0.3;
    static final double TEMP_WEIGHT = 0.2;
    static final double RAIN_CAP_MM = 20;
    static final double TEMP_LOW = 22;
    static final double TEMP_HIGH = 28;

    /** One calendar day summarised from 24 hourly readings. */
    public record Day(LocalDate date, double tMean, int rh90Hours, double rainSum, double risk) {
    }

    /** Risk score from 0 to 1 for one day (part 3). */
    public static double risk(double tMean, int rh90Hours, double rainSum) {
        double wet = rh90Hours / 24.0;
        double rain = Math.min(rainSum / RAIN_CAP_MM, 1.0);
        double temp = (tMean >= TEMP_LOW && tMean <= TEMP_HIGH) ? 1.0 : 0.3;
        return WET_WEIGHT * wet + RAIN_WEIGHT * rain + TEMP_WEIGHT * temp;
    }

    /** Hourly rows to daily rows. Days without exactly 24 hours are left out, never guessed. */
    public static List<Day> toDays(List<WeatherReading> hourly) {
        Map<LocalDate, List<WeatherReading>> byDay = new TreeMap<>();
        for (WeatherReading r : hourly) {
            byDay.computeIfAbsent(r.getReadingTime().toLocalDate(), k -> new ArrayList<>()).add(r);
        }

        List<Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<WeatherReading>> entry : byDay.entrySet()) {
            List<WeatherReading> rows = entry.getValue();
            if (rows.size() != 24) {
                continue;
            }
            double tSum = 0;
            int tCount = 0;
            int rh90 = 0;
            double rain = 0;
            for (WeatherReading r : rows) {
                if (r.getTemperature() != null) {      // a missing value is skipped, not counted as 0
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
            days.add(new Day(entry.getKey(), tMean, rh90, rain, risk(tMean, rh90, rain)));
        }
        return days;
    }
    public static final int WINDOW = 7;

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
    double rain3 = 0;
    double rain7 = 0;
    int wet3 = 0;
    int wet7 = 0;
    for (int i = 0; i < WINDOW; i++) {
        Day d = w.get(i);
        rain7 += d.rainSum();
        wet7 += d.rh90Hours();
        if (i >= WINDOW - 3) {
            rain3 += d.rainSum();
            wet3 += d.rh90Hours();
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
        wet3,
        wet7,
        today.date().getMonthValue()
    };
}
}
