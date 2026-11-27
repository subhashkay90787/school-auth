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
import java.util.Set;

/**
 * grant_type=urn:ietf:params:oauth:grant-type:otp with either {@code phone} (WhatsApp OTP) or {@code email}
 * (email OTP), plus {@code otp}. Optional device context: device_id, device_name, platform, app_version,
 * school_id (branch chosen at sign-in).
 */
public class OAuth2OtpAuthenticationConverter implements AuthenticationConverter {

    private static final Set<String> CONSUMED = Set.of("grant_type", "phone", "email", "otp");

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter("grant_type");
        if (!OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE.getValue().equals(grantType)) {
            return null;
        }

        String phone = request.getParameter("phone");
        String email = request.getParameter("email");
        String otp = request.getParameter("otp");
        if (StringUtils.hasText(phone) == StringUtils.hasText(email) || !StringUtils.hasText(otp)) {
            // exactly one of phone / email is required
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (clientPrincipal == null) {
            return null;
        }

        Map<String, Object> additionalParameters = new HashMap<>();
        request.getParameterMap().forEach((key, value) -> {
            if (!CONSUMED.contains(key)) {
                additionalParameters.put(key, value[0]);
            }
        });
        additionalParameters.put("ip_address", request.getRemoteAddr());
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            additionalParameters.put("user_agent", userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        }

        OtpChannel channel = StringUtils.hasText(phone) ? OtpChannel.WHATSAPP : OtpChannel.EMAIL;
        return new OAuth2OtpAuthenticationToken(clientPrincipal, channel, StringUtils.hasText(phone) ? phone : email,
                otp, additionalParameters);
    }
}
