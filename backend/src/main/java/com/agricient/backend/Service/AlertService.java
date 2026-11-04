package com.agricient.backend.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.agricient.backend.Model.PlaceForecast;
import com.agricient.backend.Repository.PlaceForecastRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.agricient.backend.Model.Alert;
import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.Land;
import com.agricient.backend.Model.Notification;
import com.agricient.backend.Model.UserAlert;
import com.agricient.backend.Model.Diagnosis;
import com.agricient.backend.Repository.AlertRepository;
import com.agricient.backend.Repository.AppUserRepository;
import com.agricient.backend.Repository.LandRepository;
import com.agricient.backend.Repository.NotificationRepository;
import com.agricient.backend.Repository.UserAlertRepository;
import com.agricient.backend.Repository.DiagnosisRepository;

import ai.onnxruntime.OrtException;

@Service
public class AlertService {
    private final PlaceForecastRepository placeForecasts;
    public static final double WARNING_AT = 0.60;
    public static final double WATCH_AT = 0.50;
    public static final float LEAF_MIN_CONFIDENCE = 0.80f;
    public static final int LEAF_DAYS = 7;

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final RiskService riskService;
    private final AlertRepository alerts;
    private final DiagnosisRepository diagnoses;
    private final UserAlertRepository userAlerts;
    private final AppUserRepository users;
    private final LandRepository lands;
    private final NotificationRepository notifications;
    private final SmsSender smsSender;
    private final PushSender pushSender;
    private final AreaService areas;

    public AlertService(RiskService riskService, AlertRepository alerts,
            DiagnosisRepository diagnoses, UserAlertRepository userAlerts,
            AppUserRepository users, LandRepository lands,
            PlaceForecastRepository placeForecasts,
            NotificationRepository notifications, SmsSender smsSender, PushSender pushSender,
            AreaService areas) {
        this.riskService = riskService;
        this.alerts = alerts;
        this.diagnoses = diagnoses;
        this.userAlerts = userAlerts;
        this.users = users;
        this.lands = lands;
        this.placeForecasts = placeForecasts;
        this.notifications = notifications;
        this.smsSender = smsSender;
        this.pushSender = pushSender;
        this.areas = areas;
    }

    public static String level(double predictedRisk, boolean leafConfirmed) {
        if (predictedRisk >= WARNING_AT) {
            return "WARNING";
        }
        if (predictedRisk >= WATCH_AT) {
            return leafConfirmed ? "WARNING" : "WATCH";
        }
        return "NONE";
    }

    /** A recent, confident coconut photo taken on this specific land, or null. */
    private Diagnosis recentLeafForLand(Long userId, Long landId) {
        List<Diagnosis> recent = diagnoses
                .findByOwnerIdAndLandIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, landId, LocalDateTime.now().minusDays(LEAF_DAYS));
        for (Diagnosis d : recent) {
            if (d.getTopLabel().startsWith("Coconut___") && d.getConfidence() >= LEAF_MIN_CONFIDENCE) {
                return d;
            }
        }
        return null;
    }

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


    public List<Alert> evaluateCoconut() throws OrtException {
        Diagnosis leaf = recentLeaf();
        List<Alert> result = new ArrayList<>();
        for (String site : WeatherService.COCONUT_SITES) {
            RiskService.Forecast f = riskService.forecastFor(site);
            String level = level(f.predictedRisk(), leaf != null);

            String reason = String.format("predicted risk %.2f for %s", f.predictedRisk(), f.predictedFor());
            if (leaf != null && f.predictedRisk() >= WATCH_AT && f.predictedRisk() < WARNING_AT) {
                reason += "; upgraded by leaf photo " + leaf.getTopLabel();
            }
            String leafLabel = leaf == null ? null : leaf.getTopLabel();
            Float leafConf = leaf == null ? null : leaf.getConfidence();

            Alert alert = alerts.findBySiteAndForecastFor(site, f.predictedFor()).orElse(null);
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

    /** One place a farmer cares about: their home location or one of their lands. */
    private record Place(String name, Long landId, double lat, double lon) {
    }

    public int evaluateAllFarmers() {
        int saved = 0;
        for (AppUser user : users.findAll()) {
            saved += evaluateFarmer(user);
        }
        alertOfficersSafely();
        return saved;
    }

    /** Checks one farmer's home and lands straight away and saves any alerts (and the text, if they agreed). */
    public int evaluateFarmer(AppUser user) {
        if (AppUser.OFFICER.equals(user.getRole())) {
            return 0;   // officers are told about areas, not about a farm of their own
        }
        int saved = 0;
        {
            List<Place> places = new ArrayList<>();
            if (user.getLatitude() != null && user.getLongitude() != null) {
                String home = user.getLocality() != null ? user.getLocality() : "Home";
                places.add(new Place(home, null, user.getLatitude(), user.getLongitude()));
            }
            for (Land land : lands.findByOwnerIdOrderByCreatedAtDesc(user.getId())) {
                places.add(new Place(land.getName(), land.getId(), land.getLatitude(), land.getLongitude()));
            }

            for (Place place : places) {
                try {
                    if (saveAlertIfNeeded(user, place)) {
                        saved++;
                    }
                } catch (Exception e) {
                    // one place failing (for example no weather) must not stop everyone else
                    log.warn("alert check failed for user {} place {}: {}", user.getId(), place.name(), e.getMessage());
                }
            }
        }
        return saved;
    }

    private final java.util.concurrent.ExecutorService background = java.util.concurrent.Executors.newFixedThreadPool(2);

    /** Runs the check for one farmer without making the web request wait for weather to download. */
    /** Gives a new officer the warnings that already exist, without waiting for the next scheduled check. */
    public void evaluateOfficerSoon(Long userId) {
        background.submit(this::alertOfficersSafely);
    }

    public void evaluateFarmerSoon(Long userId) {
        background.submit(() -> {
            try {
                users.findById(userId).ifPresent(u -> log.info("instant alert check for user {}: {} alerts", userId, evaluateFarmer(u)));
                alertOfficersSafely();
            } catch (Exception e) {
                log.warn("instant alert check failed for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private boolean saveAlertIfNeeded(AppUser user, Place place) throws OrtException {
        RiskService.Forecast f = riskService.forecastForPoint(place.lat(), place.lon());
        String key = WeatherService.placeKey(place.lat(), place.lon());
        rememberForecast(key, f);   // saved for every place, including low risk, for the officer map
        Diagnosis leaf = place.landId() == null ? null : recentLeafForLand(user.getId(), place.landId());
        String level = level(f.predictedRisk(), leaf != null);
        if (level.equals("NONE") && leaf != null) {
            level = "WATCH";   // calm weather, but a confident photo on this land is still worth a heads-up
        }
        if (level.equals("NONE")) {
            return false;
        }
        String reason = String.format("Weather risk %.2f for %s: humid, rainy or warm conditions favour coconut diseases. "
                + "Inspect your palms and ask an agricultural officer if you see symptoms.",
                f.predictedRisk(), f.predictedFor());
        if (leaf != null) {
            String disease = leaf.getTopLabel().replace("Coconut___", "").replace('_', ' ');
            reason += String.format(" Your recent photo of this land looked like %s (%.0f%% model confidence); "
                    + "this is a possible match, not a confirmed diagnosis.", disease, leaf.getConfidence() * 100);
        }

        UserAlert alert = userAlerts
                .findByUserIdAndPlaceKeyAndForecastFor(user.getId(), key, f.predictedFor())
                .orElse(null);
        if (alert == null) {
            alert = new UserAlert(user.getId(), key, place.name(), place.landId(),
                    f.predictedFor(), level, f.predictedRisk(), reason);
        } else {
            alert.update(level, f.predictedRisk(), reason);
        }
        alert = userAlerts.save(alert);   // a new alert gets its id only once saved
        pushIfNeeded(user, alert, String.format(
                "High coconut disease risk at %s (%.2f) for %s. Open the app for details.",
                alert.getPlaceName(), alert.getPredictedRisk(), alert.getForecastFor()));
        textIfNeeded(user, alert);
        return true;
    }

    private void alertOfficersSafely() {
        try {
            int n = alertOfficers();
            if (n > 0) {
                log.info("officer alerts saved or updated: {}", n);
            }
        } catch (Exception e) {
            log.warn("officer alerts failed: {}", e.getMessage());
        }
    }

    /**
     * Tells every officer about each area whose forecast is high risk: an in-app alert plus a push notification.
     * It carries only the area name, the risk and counts of farmers and lands, never a farmer's name or exact pin.
     */
    public synchronized int alertOfficers() {
        List<AppUser> officers = users.findAll().stream()
                .filter(u -> AppUser.OFFICER.equals(u.getRole())).toList();
        if (officers.isEmpty()) {
            return 0;
        }
        java.util.Map<String, AreaService.Stats> stats = areas.stats();
        int saved = 0;
        for (PlaceForecast f : placeForecasts.findAll()) {
            if (!"WARNING".equals(level(f.getPredictedRisk(), false))) {
                continue;
            }
            AreaService.Stats st = stats.get(f.getPlaceKey());
            if (st == null || st.farmers() == 0) {
                continue;
            }
            String name = st.name() != null ? st.name() : "Area near " + f.getLatitude() + ", " + f.getLongitude();
            if (name.length() > 80) {
                name = name.substring(0, 80);
            }
            String reason = String.format("High weather risk (%.2f) forecast for %s in this area, where %d farmer(s) "
                    + "and %d land(s) are registered. This is a weather-based risk score, not a confirmed outbreak. "
                    + "Consider advising farmers or planning a visit.",
                    f.getPredictedRisk(), f.getPredictedFor(), st.farmers(), st.lands());
            String pushMessage = String.format("High coconut disease risk in %s (%.2f) for %s. %d farmer(s) registered there.",
                    name, f.getPredictedRisk(), f.getPredictedFor(), st.farmers());
            for (AppUser officer : officers) {
                try {
                    UserAlert alert = userAlerts.findByUserIdAndPlaceKeyAndForecastFor(
                            officer.getId(), f.getPlaceKey(), f.getPredictedFor()).orElse(null);
                    if (alert == null) {
                        alert = new UserAlert(officer.getId(), f.getPlaceKey(), name, null,
                                f.getPredictedFor(), "WARNING", f.getPredictedRisk(), reason);
                    } else {
                        alert.update("WARNING", f.getPredictedRisk(), reason);
                    }
                    alert = userAlerts.save(alert);
                    pushIfNeeded(officer, alert, pushMessage);
                    saved++;
                } catch (Exception e) {
                    log.warn("officer alert failed for officer {} area {}: {}", officer.getId(), f.getPlaceKey(), e.getMessage());
                }
            }
        }
        return saved;
    }

    /** Sends one phone or browser notification per WARNING alert, even if the page is closed. */
    private void pushIfNeeded(AppUser user, UserAlert alert, String message) {
        if (!"WARNING".equals(alert.getLevel()) || !pushSender.enabled()) {
            return;
        }
        if (notifications.existsByAlertIdAndChannel(alert.getId(), "PUSH")) {
            return;   // one push per alert
        }
        Notification n = notifications.save(new Notification(user.getId(), alert.getId(), "PUSH", message));
        int devices = pushSender.sendToUser(user.getId(), "AgriCient weather alert", message, "/#myAlerts");
        n.markDone(devices > 0 ? Notification.SENT : Notification.FAILED,
                devices > 0 ? null : "no subscribed device accepted the message");
        notifications.save(n);
    }

    /** Sends one text for a WARNING alert, only to a farmer who gave a number and agreed. */
    private void textIfNeeded(AppUser user, UserAlert alert) {
        if (!"WARNING".equals(alert.getLevel()) || !user.isSmsConsent() || user.getPhone() == null) {
            return;
        }
        if (notifications.existsByAlertIdAndChannel(alert.getId(), "SMS")) {
            return;   // one text per alert, however often the check runs
        }
        String message = String.format("AgriCient: high coconut disease risk at %s (%.2f) for %s. "
                + "Inspect your palms. Open the app for details.",
                alert.getPlaceName(), alert.getPredictedRisk(), alert.getForecastFor());
        Notification n = notifications.save(new Notification(user.getId(), alert.getId(), "SMS", message));
        SmsSender.Result r = smsSender.send(user.getPhone(), message);
        n.markDone(r.status(), r.error());
        notifications.save(n);
    }
    private void rememberForecast(String key, RiskService.Forecast f) {
        String[] parts = key.substring(4).split(",");          // "geo:13.26,76.48" -> ["13.26","76.48"]
        PlaceForecast row = placeForecasts.findById(key)
                .orElseGet(() -> new PlaceForecast(key, Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
        row.update(f.predictedFor(), f.predictedRisk(), f.riskToday());
        placeForecasts.save(row);
    }
    public List<Alert> all() {
        return alerts.findAllByOrderByCreatedAtDesc();
    }

    @Scheduled(cron = "0 20 * * * *")
    public void scheduled() {
        try {
            log.info("scheduled alert evaluation: {} sites", evaluateCoconut().size());
        } catch (Exception e) {
            log.warn("scheduled alert evaluation failed: {}", e.getMessage());
        }
    }


    // every 30 minutes, at :00 and :30, so a change in the forecast reaches farmers within half an hour
    @Scheduled(cron = "0 0,30 * * * *")
    public void scheduledFarmerAlerts() {
    try {
        log.info("scheduled farmer alert evaluation: {} alerts saved or updated", evaluateAllFarmers());
    } catch (Exception e) {
        log.warn("scheduled farmer alert evaluation failed: {}", e.getMessage());
    }
}
}