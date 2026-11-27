package com.school.auth.otp;

public interface OtpSender {

    OtpChannel channel();

    /** destination is a normalized phone number (WHATSAPP) or a lower-cased email (EMAIL). */
    void send(String destination, String code, long ttlMinutes);
}
