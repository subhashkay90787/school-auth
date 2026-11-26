package com.school.auth.controller;

import com.school.auth.otp.OtpChannel;
import com.school.auth.otp.OtpService;
import com.school.auth.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final SessionService sessionService;

    /**
     * Sends an OTP over WhatsApp (phone) or email. Used for login (grant_type=urn:ietf:params:oauth:grant-type:otp
     * with the same phone or email) and for verifying a phone number during onboarding. The response is the same
     * whether or not the identifier belongs to a registered user, so it cannot be used to enumerate accounts.
     */
    @PostMapping("/send")
    public ResponseEntity<OtpSentResponse> sendOtp(@Valid @RequestBody SendOtpRequest request, HttpServletRequest http) {
        boolean byPhone = StringUtils.hasText(request.phone());
        if (byPhone == StringUtils.hasText(request.email())) {
            throw new IllegalArgumentException("Give exactly one of phone or email.");
        }
        OtpChannel channel = byPhone ? OtpChannel.WHATSAPP : OtpChannel.EMAIL;
        String identifier = byPhone ? request.phone() : request.email();
        otpService.sendOtp(channel, identifier);
        sessionService.recordAttempt(OtpService.normalize(channel, identifier), channel.authMethod, "OTP_SENT",
                null, null, http.getRemoteAddr(), null);
        return ResponseEntity.accepted().body(new OtpSentResponse(channel.name(),
                "If the " + (byPhone ? "number" : "email") + " is registered, a code has been sent."));
    }
}

record SendOtpRequest(
        @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$", message = "Invalid phone number") String phone,
        @Email(message = "Invalid email") String email) {}

record OtpSentResponse(String channel, String message) {}
