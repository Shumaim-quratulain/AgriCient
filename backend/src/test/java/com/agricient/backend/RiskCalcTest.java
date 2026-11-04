package com.agricient.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.agricient.backend.Model.WeatherReading;
import com.agricient.backend.Service.RiskCalc;

class RiskCalcTest {

    // Values measured in Python on 18 Oct 2026 (risk.py)
    @Test
    void riskMatchesPythonForKozhikode() {
        assertEquals(0.8258, RiskCalc.risk(27.0792, 16, 19.5), 0.0001);
    }

    @Test
    void riskMatchesPythonForPollachi() {
        assertEquals(0.5623, RiskCalc.risk(26.6125, 13, 6.1), 0.0001);
    }

    @Test
    void riskMatchesPythonForTiptur() {
        assertEquals(0.3967, RiskCalc.risk(24.175, 8, 2.0), 0.0001);
    }

    @Test
    void coldDayUsesLowTemperaturePart() {
        // wet 0, rain 0, temp outside 22-28 -> only 0.2 * 0.3
        assertEquals(0.06, RiskCalc.risk(15.0, 0, 0.0), 0.0001);
    }

    private static List<WeatherReading> day(int dayOfMonth, int hours, double temp, int humidity, double rain) {
        List<WeatherReading> rows = new ArrayList<>();
        for (int h = 0; h < hours; h++) {
            rows.add(new WeatherReading("Test", 0, 0,
                    LocalDateTime.of(2026, 10, dayOfMonth, h, 0), temp, humidity, rain, 5.0));
        }
        return rows;
    }

    @Test
    void groupsTwentyFourHoursIntoOneDay() {
        // 24 hours of 91 % humidity and 0.5 mm rain at 25 C
        var days = RiskCalc.toDays(day(18, 24, 25.0, 91, 0.5));
        assertEquals(1, days.size());
        assertEquals(25.0, days.get(0).tMean(), 1e-9);
        assertEquals(24, days.get(0).rh90Hours());
        assertEquals(12.0, days.get(0).rainSum(), 1e-9);
    }

    @Test
    void skipsAnIncompleteDay() {
        var rows = new ArrayList<>(day(18, 24, 25.0, 91, 0.5));
        rows.addAll(day(19, 13, 25.0, 91, 0.5));          // today, only 13 hours so far
        var days = RiskCalc.toDays(rows);
        assertEquals(1, days.size());
        assertEquals(18, days.get(0).date().getDayOfMonth());
    }

    private static RiskCalc.Day d(int dayOfMonth, double t, int wet, double rain) {
        return new RiskCalc.Day(java.time.LocalDate.of(2026, 10, dayOfMonth), t, wet, rain, RiskCalc.risk(t, wet, rain));
    }

    // Kozhikode 12-18 Oct 2026: daily values measured in Python from the database
    private static List<RiskCalc.Day> kozhikodeWeek() {
        return List.of(
                d(12, 28.5, 7, 1.1), d(13, 29.120833, 5, 0.0), d(14, 28.383333, 8, 2.8),
                d(15, 27.579167, 12, 29.4), d(16, 27.104167, 10, 15.4),
                d(17, 26.766667, 12, 11.5), d(18, 27.079167, 16, 19.5));
    }

    @Test
    void elevenInputsMatchPythonForKozhikode() {
        float[] f = RiskCalc.features(kozhikodeWeek());
        // risk, lag1, lag2, t_mean, rh90, rain, rain_3d, rain_7d, rh90_3d, rh90_7d, month
        float[] expected = { 0.8258f, 0.6225f, 0.6393f, 27.0792f, 16f, 19.5f, 46.4f, 79.7f, 38f, 70f, 10f };
        assertEquals(11, f.length);
        for (int i = 0; i < 11; i++) {
            assertEquals(expected[i], f[i], 0.001, "input #" + (i + 1));
        }
    }

    @Test
    void refusesWhenFewerThanSevenDays() {
        var six = kozhikodeWeek().subList(1, 7);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> RiskCalc.features(six));
    }

    @Test
    void refusesWhenADayIsMissingInTheMiddle() {
        var gap = new ArrayList<>(kozhikodeWeek());
        gap.remove(3);                       // drop 15 Oct
        gap.add(0, d(11, 28.0, 5, 0.0));     // add an older day so the list still has 7 entries
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> RiskCalc.features(gap));
    }
}
