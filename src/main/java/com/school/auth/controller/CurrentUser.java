package com.school.auth.controller;

import com.school.auth.configuration.AuthorizationServerConfig;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The caller, as asserted by the signed access token (never by client-supplied headers).
 */
record CurrentUser(Long userId, boolean superUser) {

    static CurrentUser from(JwtAuthenticationToken authentication) {
        Long userId = Long.valueOf(authentication.getToken().getClaimAsString("user_id"));
        boolean superUser = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AuthorizationServerConfig.SUPER_USER_AUTHORITY::equals);
        return new CurrentUser(userId, superUser);
    }
}
