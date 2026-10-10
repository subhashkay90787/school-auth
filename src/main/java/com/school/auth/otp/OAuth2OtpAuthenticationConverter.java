package com.school.auth.otp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class OAuth2OtpAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 1. Verify grant_type matches custom OTP grant type
        String grantType = request.getParameter("grant_type");
        if (!OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE.getValue().equals(grantType)) {
            return null;
        }

        // 2. Extract phone and otp request parameters
        String phone = request.getParameter("phone");
        String otp = request.getParameter("otp");

        if (!StringUtils.hasText(phone) || !StringUtils.hasText(otp)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // 3. Extract client principal (authenticated via public client / client credentials)
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (clientPrincipal == null) {
            return null;
        }

        // 4. Capture any additional parameters
        Map<String, Object> additionalParameters = new HashMap<>();
        request.getParameterMap().forEach((key, value) -> {
            if (!key.equals("grant_type") && !key.equals("phone") && !key.equals("otp")) {
                additionalParameters.put(key, value[0]);
            }
        });

        return new OAuth2OtpAuthenticationToken(clientPrincipal, phone, otp, additionalParameters);
    }
}