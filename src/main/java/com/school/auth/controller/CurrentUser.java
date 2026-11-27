package com.school.auth.controller;

import com.school.auth.configuration.AuthorizationServerConfig;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * The caller, as asserted by the signed access token (never by client-supplied headers).
 */
record CurrentUser(Long userId, boolean superUser, UUID sessionId) {

    static CurrentUser from(JwtAuthenticationToken authentication) {
        Object userIdClaim = authentication.getToken().getClaims().get("user_id");
        if (userIdClaim == null) {
            throw new SecurityException("This endpoint needs a user access token.");
        }
        Long userId = Long.valueOf(userIdClaim.toString());
        boolean superUser = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AuthorizationServerConfig.SUPER_USER_AUTHORITY::equals);
        String sid = authentication.getToken().getClaimAsString("sid");
        return new CurrentUser(userId, superUser, sid == null ? null : UUID.fromString(sid));
    }
}
