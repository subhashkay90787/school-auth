package com.school.auth.otp;

/** How an OTP reaches the user. SMS is deliberately not offered. */
public enum OtpChannel {
    WHATSAPP("WHATSAPP_OTP"),
    EMAIL("EMAIL_OTP");

    /** Value stored in user_session.auth_method / login_attempt.method. */
    public final String authMethod;

    OtpChannel(String authMethod) {
        this.authMethod = authMethod;
    }
}
