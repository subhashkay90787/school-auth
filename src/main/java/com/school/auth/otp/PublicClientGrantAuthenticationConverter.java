package com.school.auth.otp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * Spring Authorization Server only authenticates public clients (no secret) on the PKCE
 * authorization_code flow. This converter lets a public client (the mobile app) identify itself
 * with just client_id for the OTP grant (the OTP is the user credential) and the refresh_token
 * grant (refresh tokens are rotated on every use).
 */
public class PublicClientGrantAuthenticationConverter implements AuthenticationConverter {

    static final Set<String> SUPPORTED_GRANT_TYPES = Set.of(
            OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE.getValue(),
            AuthorizationGrantType.REFRESH_TOKEN.getValue());

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!SUPPORTED_GRANT_TYPES.contains(grantType)) {
            return null;
        }
        // Confidential clients are handled by the default client_secret_* converters
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) {
            return null;
        }

        String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length != 1 || !StringUtils.hasText(clientIds[0])) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        return new OAuth2ClientAuthenticationToken(
                clientIds[0],
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(OAuth2ParameterNames.GRANT_TYPE, grantType));
    }
}
