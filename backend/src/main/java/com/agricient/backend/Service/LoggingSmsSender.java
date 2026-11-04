package com.agricient.backend.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.agricient.backend.Model.Notification;

/** Stand-in used until a real SMS provider is set up. It sends nothing and says so. */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public Result send(String phone, String message) {
        // only the last 3 digits are logged, so phone numbers do not end up in log files
        String tail = phone.length() > 3 ? phone.substring(phone.length() - 3) : "***";
        log.info("SIMULATED SMS to ***{}: {}", tail, message);
        return new Result(false, Notification.SIMULATED, null);
    }
}