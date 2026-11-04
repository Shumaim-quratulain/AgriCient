package com.agricient.backend.Controller;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Repository.AppUserRepository;
import com.agricient.backend.Service.AlertService;
import com.agricient.backend.Service.AuthService;

@RestController
public class AuthController {

    public record RegisterRequest(String name, String login, String password, String locality,
            Double latitude, Double longitude, String inviteCode) {
    }

    public record LoginRequest(String login, String password) {
    }

    public record SmsRequest(String phone, boolean consent) {
    }

    // a mobile number: optional +, then 10 to 15 digits
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");

    private final AuthService auth;
    private final AppUserRepository users;
    private final AlertService alerts;

    public AuthController(AuthService auth, AppUserRepository users, AlertService alerts) {
        this.auth = auth;
        this.users = users;
        this.alerts = alerts;
    }

    @PostMapping("/api/auth/register")
    public AuthService.Login register(@RequestBody RegisterRequest r, jakarta.servlet.http.HttpServletRequest request) {
        AppUser user = auth.registerFarmer(r.name(), r.login(), r.password(), r.locality(),
                r.latitude(), r.longitude(), r.inviteCode(), request.getRemoteAddr());
        if (AppUser.FARMER.equals(user.getRole())) {
            alerts.evaluateFarmerSoon(user.getId());
        } else {
            alerts.evaluateOfficerSoon(user.getId());
        }
        return auth.login(user.getLogin(), r.password());
    }

    /** Tells the page whether to show the officer invite code field. Nothing secret is revealed. */
    @GetMapping("/api/auth/options")
    public Map<String, Boolean> options() {
        return Map.of("officerSignup", auth.officerSignupEnabled());
    }

    @PostMapping("/api/auth/login")
    public AuthService.Login login(@RequestBody LoginRequest r) {
        return auth.login(r.login(), r.password());
    }

    @GetMapping("/api/auth/me")
    public AppUser me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.require(authorization);
    }

    /** The signed-in farmer sets or clears their SMS number and consent. Only their own account can change. */
    @PostMapping("/api/auth/sms")
    public AppUser sms(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SmsRequest r) {
        AppUser user = auth.require(authorization);
        String phone = r.phone() == null ? "" : r.phone().replaceAll("[\\s-]", "");
        if (phone.isEmpty()) {
            user.updateSms(null, false);
        } else {
            if (!PHONE.matcher(phone).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Enter a mobile number with 10 to 15 digits, for example +919812345678.");
            }
            user.updateSms(phone, r.consent());
        }
        AppUser saved = users.save(user);
        alerts.evaluateFarmerSoon(saved.getId());   // a new number can receive an already-open warning
        return saved;
    }

    @PostMapping("/api/auth/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            auth.logout(authorization.substring(7).trim());
        }
        return Map.of("status", "signed out");
    }
}
