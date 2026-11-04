package com.agricient.backend.Controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.agricient.backend.Model.PlaceForecast;
import com.agricient.backend.Model.UserAlert;
import com.agricient.backend.Repository.PlaceForecastRepository;
import com.agricient.backend.Repository.UserAlertRepository;
import com.agricient.backend.Service.AlertService;
import com.agricient.backend.Service.AreaService;
import com.agricient.backend.Service.AuthService;

@RestController
public class OfficerController {

    public record Area(String placeKey, String name, double latitude, double longitude,
            LocalDate predictedFor, double predictedRisk, String level,
            int farmers, int lands, int openAlerts, LocalDateTime updatedAt) {
    }

    private final AuthService auth;
    private final PlaceForecastRepository forecasts;
    private final AreaService areas;
    private final UserAlertRepository userAlerts;

    public OfficerController(AuthService auth, PlaceForecastRepository forecasts,
            AreaService areas, UserAlertRepository userAlerts) {
        this.auth = auth;
        this.forecasts = forecasts;
        this.areas = areas;
        this.userAlerts = userAlerts;
    }

    @GetMapping("/api/officer/areas")
    public List<Area> areas(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireOfficer(authorization);

        Map<String, AreaService.Stats> stats = areas.stats();
        Set<Long> farmerIds = areas.farmerIds();

        // unread alerts that were sent to farmers (an officer's own alerts are not counted here)
        Map<String, Integer> openByArea = new HashMap<>();
        for (UserAlert a : userAlerts.findAll()) {
            if (a.getReadAt() == null && farmerIds.contains(a.getUserId())) {
                openByArea.merge(a.getPlaceKey(), 1, Integer::sum);
            }
        }

        List<Area> out = new ArrayList<>();
        for (PlaceForecast f : forecasts.findAll()) {
            String key = f.getPlaceKey();
            AreaService.Stats st = stats.get(key);
            if (st == null || st.farmers() == 0) {
                continue;   // no farmer lives or farms here any more
            }
            String name = st.name() != null ? st.name() : "Area near " + f.getLatitude() + ", " + f.getLongitude();
            out.add(new Area(key, name, f.getLatitude(), f.getLongitude(), f.getPredictedFor(), f.getPredictedRisk(),
                    AlertService.level(f.getPredictedRisk(), false), st.farmers(), st.lands(),
                    openByArea.getOrDefault(key, 0), f.getUpdatedAt()));
        }
        out.sort((a, b) -> Double.compare(b.predictedRisk(), a.predictedRisk()));
        return out;
    }
}
