package com.agricient.backend.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.agricient.backend.Model.Notification;

/**
 * Sends real texts through Twilio. Used only when sms.provider=twilio.
 * It logs in with a Twilio API key (a key id and a secret that can be revoked on its own), and the account id
 * only appears in the web address. All values are read from environment variables, never stored in the code.
 */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);

    private final String accountSid;
    private final String apiKeySid;
    private final String apiKeySecret;
    private final String from;
    private final String trialTemplate;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public TwilioSmsSender(@Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.api-key-sid:}") String apiKeySid,
            @Value("${twilio.api-key-secret:}") String apiKeySecret,
            @Value("${twilio.from:}") String from,
            @Value("${twilio.trial-template:}") String trialTemplate) {
        if (accountSid.isBlank() || apiKeySid.isBlank() || apiKeySecret.isBlank() || from.isBlank()) {
            throw new IllegalStateException("sms.provider=twilio needs TWILIO_ACCOUNT_SID, TWILIO_API_KEY_SID, "
                    + "TWILIO_API_KEY_SECRET and TWILIO_FROM to be set");
        }
        this.accountSid = accountSid;
        this.apiKeySid = apiKeySid;
        this.apiKeySecret = apiKeySecret;
        this.from = from;
        this.trialTemplate = trialTemplate == null ? "" : trialTemplate.trim();
    }

    @Override
    public Result send(String phone, String message) {
        String tail = phone.length() > 3 ? phone.substring(phone.length() - 3) : "***";
        try {
            // Twilio expects a form-encoded POST with To, From and Body. HTTP Basic login uses the API key id and secret
            // A Twilio trial account refuses custom wording and only accepts the name of one of its own templates.
            // When twilio.trial-template is set, that template name is sent instead of our message.
            String text = trialTemplate.isEmpty() ? message : trialTemplate;
            String form = "To=" + enc(phone) + "&From=" + enc(from) + "&Body=" + enc(text);
            String login = Base64.getEncoder()
                    .encodeToString((apiKeySid + ":" + apiKeySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + login)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode body = mapper.readTree(response.body());
            if (response.statusCode() / 100 == 2) {
                // "queued" means Twilio accepted the message; it has not yet reached the phone
                log.info("SMS accepted by Twilio for ***{} (status {})", tail, body.path("status").asText());
                String note = trialTemplate.isEmpty() ? null
                        : "Sent as Twilio trial template " + trialTemplate + "; our own wording needs an upgraded account";
                return new Result(true, Notification.SENT, note);
            }
            String reason = "Twilio " + response.statusCode() + ": " + body.path("message").asText("rejected");
            log.warn("SMS to ***{} failed: {}", tail, reason);
            return new Result(false, Notification.FAILED, cut(reason));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, Notification.FAILED, "interrupted");
        } catch (Throwable e) {
            log.warn("SMS to ***{} failed: {}", tail, e.getClass().getSimpleName());
            return new Result(false, Notification.FAILED, cut(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String cut(String text) {
        return text.length() > 190 ? text.substring(0, 190) : text;   // the error column holds 200 characters
    }
}
