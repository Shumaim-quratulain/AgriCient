package com.agricient.backend.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.AuthSession;
import com.agricient.backend.Repository.AppUserRepository;
import com.agricient.backend.Repository.AuthSessionRepository;

@Service
public class AuthService {

    private static final int SESSION_DAYS = 14;

    private final AppUserRepository users;
    private final AuthSessionRepository sessions;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    private static final int MAX_BAD_CODES = 5;
    private static final long CODE_LOCK_MINUTES = 15;

    private final String officerCode;
    // wrong invite codes per caller, so the code cannot be guessed by trying many times
    private final Map<String, int[]> badCodes = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, AuthSessionRepository sessions,
            @Value("${officer.invite-code:}") String officerCode) {
        this.users = users;
        this.sessions = sessions;
        this.officerCode = officerCode == null ? "" : officerCode.trim();
    }

    /** True when an invite code is set, so the registration page knows to show the field. */
    public boolean officerSignupEnabled() {
        return !officerCode.isEmpty();
    }

    /** Returns true for a correct code, false for no code, and rejects a wrong or guessed one. */
    private boolean checkInviteCode(String code, String caller) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (!officerSignupEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Officer sign-up is not switched on.");
        }
        long now = System.currentTimeMillis();
        Long until = lockedUntil.get(caller);
        if (until != null && until > now) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong codes. Please wait " + CODE_LOCK_MINUTES + " minutes and try again.");
        }
        boolean ok = MessageDigest.isEqual(code.trim().getBytes(StandardCharsets.UTF_8),
                officerCode.getBytes(StandardCharsets.UTF_8));   // constant-time comparison
        if (ok) {
            badCodes.remove(caller);
            return true;
        }
        int[] count = badCodes.computeIfAbsent(caller, k -> new int[1]);
        if (++count[0] >= MAX_BAD_CODES) {
            lockedUntil.put(caller, now + CODE_LOCK_MINUTES * 60_000);
            badCodes.remove(caller);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That officer invite code is not correct.");
    }

    public record Login(String token, AppUser user) {
    }

    /** Anyone can register as a farmer. Officer accounts are never created through this public call. */
    /** Registers a farmer, or an officer when the correct invite code is given. */
    public AppUser registerFarmer(String name, String login, String password, String locality,
            Double latitude, Double longitude, String inviteCode, String caller) {
        String cleanName = name == null ? "" : name.trim();
        String cleanLogin = login == null ? "" : login.trim().toLowerCase();
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw bad("Enter your name (up to 80 characters).");
        }
        if (cleanLogin.length() < 5 || cleanLogin.length() > 120) {
            throw bad("Enter a valid email address or phone number.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw bad("Password must be 8 to 72 characters.");
        }
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw bad("Choose your location: use your current location or enter valid coordinates.");
        }
        if (users.findByLogin(cleanLogin).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email or phone is already registered.");
        }
        boolean officer = checkInviteCode(inviteCode, caller);
        String loc = locality == null || locality.isBlank() ? null : locality.trim();
        if (loc != null && loc.length() > 80) {
            throw bad("Place name is too long (up to 80 characters).");
        }
        return users.save(new AppUser(cleanName, cleanLogin, encoder.encode(password),
                officer ? AppUser.OFFICER : AppUser.FARMER, loc, latitude, longitude));
    }

    public Login login(String login, String password) {
        String cleanLogin = login == null ? "" : login.trim().toLowerCase();
        Optional<AppUser> user = users.findByLogin(cleanLogin);
        // This tells the visitor when no account exists. That is friendlier, but it also lets anyone check
        // whether an email or phone number is registered.
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No account found for this email or phone. Create a farmer account first.");
        }
        if (password == null || !encoder.matches(password, user.get().getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong password. Please try again.");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        sessions.save(new AuthSession(hash(token), user.get().getId(), LocalDateTime.now().plusDays(SESSION_DAYS)));
        return new Login(token, user.get());
    }

    public void logout(String token) {
        if (token != null) {
            sessions.deleteById(hash(token));
        }
    }

    /** The signed-in user for an "Authorization: Bearer ..." header, or a 401. */
    public AppUser require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        }
        String h = hash(authorization.substring(7).trim());
        AuthSession s = sessions.findById(h)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
        if (s.getExpiresAt().isBefore(LocalDateTime.now())) {
            sessions.deleteById(h);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again.");
        }
        return users.findById(s.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }

    public AppUser requireOfficer(String authorization) {
        AppUser u = require(authorization);
        if (!AppUser.OFFICER.equals(u.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Officer access only.");
        }
        return u;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
