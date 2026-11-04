package com.agricient.backend.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.agricient.backend.Model.Alert;
import com.agricient.backend.Model.Diagnosis;
import com.agricient.backend.Repository.AlertRepository;
import com.agricient.backend.Repository.DiagnosisRepository;

/**
 * Turns the weather risk forecast plus recent leaf photos into one alert per site.
 *
 * Rules (thresholds are design choices, not tuned on outcomes):
 *   predicted risk >= 0.60            -> WARNING
 *   predicted risk >= 0.50            -> WATCH
 *   WATCH and a confident coconut leaf diagnosis in the last 7 days -> WARNING
 *   otherwise                         -> NONE
 */
@Service
public class AlertService {

    public static final double WARNING_AT = 0.60;
    public static final double WATCH_AT = 0.50;
    public static final float LEAF_MIN_CONFIDENCE = 0.80f;
    public static final int LEAF_DAYS = 7;

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final RiskService riskService;
    private final AlertRepository alerts;
    private final DiagnosisRepository diagnoses;

    public AlertService(RiskService riskService, AlertRepository alerts,
            DiagnosisRepository diagnoses) {
        this.riskService = riskService;
        this.alerts = alerts;
        this.diagnoses = diagnoses;
    }

    /** Pure rule, no database: easy to test. leafConfirmed only matters in the WATCH band. */
    public static String level(double predictedRisk, boolean leafConfirmed) {
        if (predictedRisk >= WARNING_AT) {
            return "WARNING";
        }
        if (predictedRisk >= WATCH_AT) {
            return leafConfirmed ? "WARNING" : "WATCH";
        }
        return "NONE";
    }

    /** Newest confident coconut diagnosis from the last LEAF_DAYS days, if any. */
    private Diagnosis recentLeaf() {
        List<Diagnosis> recent = diagnoses
                .findByTopLabelStartingWithAndCreatedAtAfterOrderByCreatedAtDesc(
                        "Coconut___", LocalDateTime.now().minusDays(LEAF_DAYS));
        for (Diagnosis d : recent) {
            if (d.getConfidence() >= LEAF_MIN_CONFIDENCE) {
                return d;
            }
        }
        return null;
    }

    /** Evaluate every coconut site and save/update one alert per site and forecast day. */
    public List<Alert> evaluateCoconut() throws Exception {
        Diagnosis leaf = recentLeaf();
        List<Alert> result = new ArrayList<>();
        for (String site : WeatherService.COCONUT_SITES) {
            RiskService.Forecast f = riskService.forecast(site);
            String level = level(f.predictedRisk(), leaf != null);

            String reason = String.format(
                    "predicted risk %.2f for %s (rule: >=%.2f WARNING, >=%.2f WATCH)",
                    f.predictedRisk(), f.predictedFor(), WARNING_AT, WATCH_AT);
            if (leaf != null && f.predictedRisk() >= WATCH_AT && f.predictedRisk() < WARNING_AT) {
                reason += "; upgraded by leaf photo " + leaf.getTopLabel();
            }

            String leafLabel = leaf == null ? null : leaf.getTopLabel();
            Float leafConf = leaf == null ? null : leaf.getConfidence();

            Alert alert = alerts.findBySiteAndForecastFor(site, f.predictedFor())
                    .orElse(null);
            if (alert == null) {
                alert = new Alert(site, f.predictedFor(), level, f.predictedRisk(),
                        leafLabel, leafConf, reason);
            } else {
                alert.update(level, f.predictedRisk(), leafLabel, leafConf, reason);
            }
            result.add(alerts.save(alert));
        }
        return result;
    }

    public List<Alert> all() {
        return alerts.findAllByOrderByCreatedAtDesc();
    }

    /** After the weather refresh (:10), re-evaluate at :20. */
    @Scheduled(cron = "0 20 * * * *")
    public void scheduled() {
        try {
            List<Alert> done = evaluateCoconut();
            log.info("scheduled alert evaluation: {} sites", done.size());
        } catch (Exception e) {
            log.warn("scheduled alert evaluation failed: {}", e.getMessage());
        }
    }
}
