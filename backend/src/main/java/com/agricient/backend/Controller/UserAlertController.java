package com.agricient.backend.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.UserAlert;
import com.agricient.backend.Repository.UserAlertRepository;
import com.agricient.backend.Service.AuthService;

@RestController
public class UserAlertController {

    private final UserAlertRepository userAlerts;
    private final AuthService auth;

    public UserAlertController(UserAlertRepository userAlerts, AuthService auth) {
        this.userAlerts = userAlerts;
        this.auth = auth;
    }

    @GetMapping("/api/alerts/mine")
    public List<UserAlert> mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.require(authorization);
        return userAlerts.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @GetMapping("/api/alerts/mine/unread-count")
    public Map<String, Long> unread(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AppUser user = auth.require(authorization);
        return Map.of("unread", userAlerts.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @PostMapping("/api/alerts/{id}/read")
    public UserAlert markRead(@RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        AppUser user = auth.require(authorization);
        UserAlert alert = userAlerts.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found."));
        alert.markRead();
        return userAlerts.save(alert);
    }
}