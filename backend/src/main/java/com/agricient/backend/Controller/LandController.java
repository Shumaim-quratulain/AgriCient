package com.agricient.backend.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.Land;
import com.agricient.backend.Repository.LandRepository;
import com.agricient.backend.Service.AlertService;
import com.agricient.backend.Service.AuthService;

@RestController
public class LandController {

    public record LandRequest(String name, String village, Double areaAcres,
            Double latitude, Double longitude, Double accuracyMeters) {
    }

    private final LandRepository lands;
    private final AuthService auth;
    private final AlertService alerts;

    public LandController(LandRepository lands, AuthService auth, AlertService alerts) {
        this.lands = lands;
        this.auth = auth;
        this.alerts = alerts;
    }

    @GetMapping("/api/lands")
    public List<Land> mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.require(authorization);
        return lands.findByOwnerIdOrderByCreatedAtDesc(user.getId());
    }

    @PostMapping("/api/lands")
    public Land add(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LandRequest r) {
        AppUser user = auth.require(authorization);
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty() || name.length() > 60) {
            throw bad("Enter a land name (up to 60 characters).");
        }
        if (r.latitude() == null || r.longitude() == null
                || r.latitude() < -90 || r.latitude() > 90 || r.longitude() < -180 || r.longitude() > 180) {
            throw bad("Enter a valid latitude and longitude.");
        }
        if (r.areaAcres() != null && (r.areaAcres() <= 0 || r.areaAcres() > 100000)) {
            throw bad("Enter a valid area in acres.");
        }
        String village = r.village() == null || r.village().isBlank() ? null : r.village().trim();
        if (village != null && village.length() > 80) {
            throw bad("Village name is too long.");
        }
        Land saved = lands.save(new Land(user.getId(), name, village, r.areaAcres(),
                r.latitude(), r.longitude(), r.accuracyMeters()));
        alerts.evaluateFarmerSoon(user.getId());
        return saved;
    }

    @DeleteMapping("/api/lands/{id}")
    public Map<String, String> remove(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        AppUser user = auth.require(authorization);
        // looked up by owner too: someone else's land id gives 404, never a delete
        Land land = lands.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Land not found."));
        lands.delete(land);
        return Map.of("status", "deleted");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
