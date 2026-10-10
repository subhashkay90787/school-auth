package com.school.auth.otp;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Collections;
import java.util.Map;

public class OAuth2OtpAuthenticationToken extends AbstractAuthenticationToken {

    public static final AuthorizationGrantType OTP_GRANT_TYPE =
            new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:otp");

    private final Authentication clientPrincipal;
    private final String phoneNumber;
    private final String otp;
    private final Map<String, Object> additionalParameters;

    public OAuth2OtpAuthenticationToken(
            Authentication clientPrincipal,
            String phoneNumber,
            String otp,
            Map<String, Object> additionalParameters) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.additionalParameters = additionalParameters;
        setAuthenticated(false);
    }

    @Override
    public Object getPrincipal() {
        return this.clientPrincipal;
    }

    @Override
    public Object getCredentials() {
        return this.otp;
    }

    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    public Map<String, Object> getAdditionalParameters() {
        return this.additionalParameters;
    }
}