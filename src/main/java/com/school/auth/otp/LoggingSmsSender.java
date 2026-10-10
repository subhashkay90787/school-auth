package com.school.auth.otp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Development-only sender that writes the message to the application log.
 * Replace with a real SMS gateway (Twilio, MSG91, AWS SNS, ...) before going live.
 */
@Slf4j
@Component
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phoneNumber, String message) {
        log.warn("[DEV SMS] to={} message=\"{}\"", phoneNumber, message);
    }
}
