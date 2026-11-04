package com.agricient.backend.Service;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agricient.backend.Model.PushSubscription;
import com.agricient.backend.Repository.PushSubscriptionRepository;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/**
 * Sends a Web Push message to every device a farmer has subscribed. This works even when the page is closed.
 * The VAPID keys come from environment variables. Without them push is switched off and nothing else is affected.
 */
@Service
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final PushSubscriptionRepository subscriptions;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private PushService service;

    public PushSender(PushSubscriptionRepository subscriptions,
            @Value("${push.public-key:}") String publicKey,
            @Value("${push.private-key:}") String privateKey,
            @Value("${push.subject:mailto:admin@example.com}") String subject) {
        this.subscriptions = subscriptions;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
        if (enabled()) {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                this.service = new PushService(publicKey, privateKey, subject);
            } catch (Exception e) {
                log.warn("push notifications switched off: {}", e.getMessage());
                this.service = null;
            }
        } else {
            log.info("push notifications are off (no VAPID keys set)");
        }
    }

    public boolean enabled() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public String publicKey() {
        return service == null ? "" : publicKey;
    }

    /** Returns how many devices accepted the message. Never throws. */
    public int sendToUser(Long userId, String title, String body, String url) {
        if (service == null) {
            return 0;
        }
        List<PushSubscription> subs = subscriptions.findByUserId(userId);
        int sent = 0;
        String payload = "{\"title\":" + quote(title) + ",\"body\":" + quote(body) + ",\"url\":" + quote(url) + "}";
        for (PushSubscription s : subs) {
            try {
                var response = service.send(new Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(),
                        payload.getBytes(StandardCharsets.UTF_8)));
                int code = response.getStatusLine().getStatusCode();
                if (code / 100 == 2) {
                    sent++;
                } else if (code == 404 || code == 410) {
                    subscriptions.delete(s);   // the device unsubscribed or the browser data was cleared
                } else {
                    log.warn("push to user {} returned HTTP {}", userId, code);
                }
            } catch (Throwable e) {
                // Throwable on purpose: a missing library class is an Error, and it must never leave a notification stuck
                log.warn("push to user {} failed: {}", userId, e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> {
                    if (c < 0x20) {
                        out.append(' ');
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
