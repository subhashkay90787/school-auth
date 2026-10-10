package com.school.auth.otp;

public class OtpRateLimitedException extends RuntimeException {

    public OtpRateLimitedException(String message) {
        super(message);
    }
}
