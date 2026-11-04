package com.agricient.backend.Controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.agricient.backend.Model.AppUser;
import com.agricient.backend.Model.PushSubscription;
import com.agricient.backend.Repository.PushSubscriptionRepository;
import com.agricient.backend.Service.AuthService;
import com.agricient.backend.Service.PushSender;

@RestController
public class PushController {

    public record SubscribeRequest(String endpoint, String p256dh, String auth) {
    }

    private final PushSubscriptionRepository subscriptions;
    private final PushSender sender;
    private final AuthService auth;

    public PushController(PushSubscriptionRepository subscriptions, PushSender sender, AuthService auth) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.auth = auth;
    }

    /** The public key the browser needs to subscribe. It is not a secret. Empty when push is off. */
    @GetMapping("/api/push/key")
    public Map<String, Object> key() {
        return Map.of("enabled", sender.enabled() && !sender.publicKey().isEmpty(), "publicKey", sender.publicKey());
    }

    @PostMapping("/api/push/subscribe")
    @Transactional
    public Map<String, String> subscribe(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SubscribeRequest r) {
        AppUser user = auth.require(authorization);
        if (r.endpoint() == null || !r.endpoint().startsWith("https://") || r.endpoint().length() > 1000
                || r.p256dh() == null || r.p256dh().isBlank() || r.p256dh().length() > 200
                || r.auth() == null || r.auth().isBlank() || r.auth().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid push subscription.");
        }
        String hash = sha256(r.endpoint());
        // the same device may re-subscribe, possibly under another account, so the old row is replaced
        subscriptions.findByEndpointHash(hash).ifPresent(subscriptions::delete);
        subscriptions.flush();
        subscriptions.save(new PushSubscription(user.getId(), hash, r.endpoint(), r.p256dh(), r.auth()));
        return Map.of("status", "subscribed");
    }

    @PostMapping("/api/push/unsubscribe")
    @Transactional
    public Map<String, String> unsubscribe(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SubscribeRequest r) {
        AppUser user = auth.require(authorization);
        if (r.endpoint() != null) {
            subscriptions.deleteByEndpointHashAndUserId(sha256(r.endpoint()), user.getId());
        }
        return Map.of("status", "unsubscribed");
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
