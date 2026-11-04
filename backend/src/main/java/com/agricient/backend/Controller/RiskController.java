package com.agricient.backend.Controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.Land;
import com.agricient.backend.Repository.LandRepository;
import com.agricient.backend.Service.AlertService;
import com.agricient.backend.Service.AuthService;
import com.agricient.backend.Service.RiskService;
import com.agricient.backend.Service.WeatherService;

import ai.onnxruntime.OrtException;

@RestController
public class RiskController {

    private static final Logger log = LoggerFactory.getLogger(RiskController.class);

    private final RiskService riskService;
    private final AuthService auth;
    private final LandRepository lands;

    public RiskController(RiskService riskService, AuthService auth, LandRepository lands) {
        this.riskService = riskService;
        this.auth = auth;
        this.lands = lands;
    }

    public record PlaceRisk(String name, String kind, Long landId, double latitude, double longitude,
            LocalDate predictedFor, Double predictedRisk, Double riskToday, String level, String error,
            List<RiskService.Outlook> outlook) {
    }

    /** Weather risk for the signed-in farmer's home location and each of their own lands. */
    @GetMapping("/api/risk/mine")
    public List<PlaceRisk> mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.require(authorization);
        List<PlaceRisk> out = new ArrayList<>();
        // places that round to the same weather point are fetched and forecast only once
        Map<String, Object> cache = new HashMap<>();

        if (user.getLatitude() != null && user.getLongitude() != null) {
            String name = user.getLocality() != null ? user.getLocality() : "My home location";
            out.add(place(cache, name, "home", null, user.getLatitude(), user.getLongitude()));
        }
        for (Land land : lands.findByOwnerIdOrderByCreatedAtDesc(user.getId())) {
            out.add(place(cache, land.getName(), "land", land.getId(), land.getLatitude(), land.getLongitude()));
        }
        return out;
    }

    /** The 3, 7 and 14 day outlooks; if they fail the 3-day forecast is still shown. */
    private List<RiskService.Outlook> outlookFor(double lat, double lon) {
        try {
            return riskService.outlookForPoint(lat, lon);
        } catch (Exception e) {
            log.warn("outlook failed: {}", e.getMessage());
            return List.of();
        }
    }

    private PlaceRisk place(Map<String, Object> cache, String name, String kind, Long landId, double lat, double lon) {
        String key = com.agricient.backend.Service.WeatherService.placeKey(lat, lon);
        Object result = cache.computeIfAbsent(key, k -> {
            try {
                return riskService.forecastForPoint(lat, lon);
            } catch (Exception e) {
                log.warn("risk for {} failed: {}", k, e.getMessage());
                return e;
            }
        });
        if (result instanceof RiskService.Forecast f) {
            return new PlaceRisk(name, kind, landId, lat, lon, f.predictedFor(), f.predictedRisk(), f.riskToday(),
                    AlertService.level(f.predictedRisk(), false), null, outlookFor(lat, lon));
        }
        return new PlaceRisk(name, kind, landId, lat, lon, null, null, null, null,
                "Weather for this place could not be loaded right now. Please try again later.", List.of());
    }

    @GetMapping("/api/risk/coconut")
    public List<RiskService.Forecast> coconut() throws OrtException {
        List<RiskService.Forecast> out = new ArrayList<>();
        for (String site : WeatherService.COCONUT_SITES) {
            out.add(riskService.forecastFor(site));
        }
        return out;
    }
}