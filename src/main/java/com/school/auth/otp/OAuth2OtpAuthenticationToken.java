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
    private final OtpChannel channel;
    private final String identifier;
    private final String otp;
    private final Map<String, Object> additionalParameters;

    /** Set by the provider once the session row exists; read by the token customizer (claim "sid"). */
    private String authorizationId;

    public OAuth2OtpAuthenticationToken(
            Authentication clientPrincipal,
            OtpChannel channel,
            String identifier,
            String otp,
            Map<String, Object> additionalParameters) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.channel = channel;
        this.identifier = identifier;
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

    public OtpChannel getChannel() {
        return channel;
    }

    /** Phone number (WHATSAPP) or email (EMAIL), as sent by the client. */
    public String getIdentifier() {
        return identifier;
    }

    public Map<String, Object> getAdditionalParameters() {
        return this.additionalParameters;
    }

    public String getAuthorizationId() {
        return authorizationId;
    }

    void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }

    public String parameter(String name) {
        Object value = additionalParameters.get(name);
        return value == null ? null : value.toString();
    }
}
