package com.school.auth.configuration;

import com.school.auth.dto.TokenPayloadDto;
import com.school.auth.entity.UserEntity;
import com.school.auth.entity.UserSessionEntity;
import com.school.auth.otp.OAuth2OtpAuthenticationToken;
import com.school.auth.repository.UserRepository;
import com.school.auth.repository.UserSessionRepository;
import com.school.auth.service.SessionService;
import com.school.auth.service.TokenClaimsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Access-token claims for user tokens:
 * <pre>
 *   user_id, sid (session id), user_details, roles (platform: ["SUPER_USER"]),
 *   school_access: [{school_id, ..., roles, base_roles, permissions, enabled_features, perm_version}]
 * </pre>
 * Everything is re-read from the database on every issuance and refresh, so deactivating a user, suspending a
 * branch or changing a role mapping takes effect at the next refresh. Client-credentials tokens (internal
 * service clients) carry no user claims.
 */
@Configuration
@RequiredArgsConstructor
public class CustomJwtTokenCustomizerConfig {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final TokenClaimsService tokenClaimsService;
    private final JsonMapper jsonMapper;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                    || AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                return;
            }
            String clientId = context.getRegisteredClient().getClientId();

            // 1. The user (invite-only: unknown or deactivated users are rejected)
            UserEntity user = resolveUser(context.getPrincipal())
                    .filter(UserEntity::isActiveFlag)
                    .orElseThrow(() -> accessDenied("No active account is registered for this sign-in"));

            // 2. Dedicated apps must be ACTIVE to issue tokens at all
            tokenClaimsService.dedicatedAppStatus(clientId)
                    .filter(status -> !"ACTIVE".equals(status))
                    .ifPresent(status -> {
                        throw accessDenied("This app is not available right now (" + status + ")");
                    });

            // 3. Session: created by the OTP grant; touched (or created, for social sign-in) otherwise
            UserSessionEntity session = resolveSession(context, user, clientId);

            // 4. Platform roles come from the database, not from the login provider.
            //    Mutable lists on purpose: List.of() is rejected when stored claims are read back.
            List<String> globalRoles = new ArrayList<>();
            if (user.isSuperUser()) {
                globalRoles.add(AuthorizationServerConfig.SUPER_USER_AUTHORITY);
            }

            // 5. Branch access with roles, base roles, permissions and features
            List<TokenPayloadDto.SchoolAccess> schoolAccess = tokenClaimsService.schoolAccess(user.getUserId(), clientId);
            if (!user.isSuperUser() && schoolAccess.isEmpty()) {
                throw accessDenied("No active school access: your branch is not live, was suspended, or your access was removed");
            }

            TokenPayloadDto.UserDetails details = new TokenPayloadDto.UserDetails(
                    user.getUserId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                    user.getPhoneNumber(), user.getAlternatePhoneNumber(), user.getUserCategory(), user.isActiveFlag());

            // 6. DTOs become plain maps (honouring @JsonProperty names): claims are also persisted by
            //    JdbcOAuth2AuthorizationService, which only deserializes JDK types.
            context.getClaims().claims(claims -> {
                claims.put("user_id", user.getUserId());
                if (session != null) {
                    claims.put("sid", session.getSessionId().toString());
                }
                claims.put("user_details", jsonMapper.convertValue(details, MAP_TYPE));
                claims.put("roles", globalRoles);
                claims.put("school_access", jsonMapper.convertValue(schoolAccess, LIST_OF_MAPS_TYPE));
            });
        };
    }

    private UserSessionEntity resolveSession(JwtEncodingContext context, UserEntity user, String clientId) {
        if (context.getAuthorizationGrant() instanceof OAuth2OtpAuthenticationToken otp && otp.getAuthorizationId() != null) {
            return sessionRepository.findByRefreshTokenFamilyId(otp.getAuthorizationId()).orElse(null);
        }
        if (context.getAuthorization() != null) {
            try {
                return sessionService.touchOrOpen(user.getUserId(), clientId, context.getAuthorization().getId());
            } catch (IllegalStateException ended) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, ended.getMessage(), null));
            }
        }
        return null;
    }

    /**
     * OTP sign-ins use the user id as the principal name (older authorizations used the email). Social logins
     * expose the provider's subject id, so the email is read from the provider attributes instead.
     */
    private java.util.Optional<UserEntity> resolveUser(Authentication principal) {
        if (principal instanceof OAuth2AuthenticationToken oauth2) {
            Map<String, Object> attributes = oauth2.getPrincipal().getAttributes();
            if (Boolean.FALSE.equals(attributes.get("email_verified"))) {
                throw accessDenied("The email address on this " + oauth2.getAuthorizedClientRegistrationId() + " account is not verified");
            }
            Object email = attributes.get("email");
            if (email == null) {
                // Microsoft Entra ID only sends "email" when configured as an optional claim
                email = attributes.get("preferred_username");
            }
            if (email == null) {
                throw accessDenied("The " + oauth2.getAuthorizedClientRegistrationId() + " account did not provide an email address");
            }
            return userRepository.findByEmailIgnoreCase(email.toString());
        }
        String name = principal.getName();
        if (name != null && name.chars().allMatch(Character::isDigit)) {
            return userRepository.findById(Long.valueOf(name));
        }
        return userRepository.findByEmailIgnoreCase(name);
    }

    private static OAuth2AuthenticationException accessDenied(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, description, null));
    }
}
