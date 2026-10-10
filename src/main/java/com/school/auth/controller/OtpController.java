package com.school.auth.controller;

import com.school.auth.otp.OtpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    /**
     * Sends an OTP to the phone number. Used both for login (grant_type=urn:ietf:params:oauth:grant-type:otp)
     * and for verifying the phone number during onboarding. The response is identical whether or not the
     * number belongs to a registered user, so it cannot be used to enumerate accounts.
     */
    @PostMapping("/send")
    public ResponseEntity<OtpSentResponse> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.sendOtp(request.phone());
        return ResponseEntity.accepted().body(new OtpSentResponse("If the number is valid, an OTP has been sent."));
    }
}

record SendOtpRequest(
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$", message = "Invalid phone number")
    String phone
) {}

record OtpSentResponse(String message) {}
