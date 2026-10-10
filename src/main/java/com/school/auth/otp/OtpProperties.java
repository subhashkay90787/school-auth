package com.school.auth.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        int length,
        Duration ttl,
        Duration resendCooldown,
        int maxAttempts
) {}
