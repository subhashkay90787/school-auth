package com.school.auth.otp;

public interface SmsSender {

    void send(String phoneNumber, String message);
}
