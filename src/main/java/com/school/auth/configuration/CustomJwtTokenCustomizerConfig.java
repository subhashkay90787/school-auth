package com.school.auth.configuration;

import com.school.auth.dto.TokenPayloadDto;
import com.school.auth.entity.UserEntity;
import com.school.auth.repository.UserRepository;
import com.school.auth.service.SchoolGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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

@Configuration
@RequiredArgsConstructor
public class CustomJwtTokenCustomizerConfig {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final SchoolGrantService schoolGrantService;
    private final JsonMapper jsonMapper;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            // Target only ACCESS_TOKEN generation
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication principal = context.getPrincipal();

                // 1. Fetch user entity dynamically from the database (invite-only: unknown users are rejected)
                String email = resolveEmail(principal);
                UserEntity userEntity = userRepository.findByEmailIgnoreCase(email)
                        .filter(UserEntity::isActiveFlag)
                        .orElseThrow(() -> accessDenied("No active account is registered for " + email));

                // 2. Map database entity to your DTO
                TokenPayloadDto.UserDetails userDetails = new TokenPayloadDto.UserDetails(
                        userEntity.getUserId(),
                        userEntity.getFirstName(),
                        userEntity.getLastName(),
                        userEntity.getEmail(),
                        userEntity.getPhoneNumber(),
                        userEntity.getAlternatePhoneNumber(),
                        "STAFF", // Or derive user category from database if stored
                        userEntity.isActiveFlag()
                );

                // 3. Platform-wide roles come from the database, not from the login provider.
                //    Mutable ArrayList on purpose: List.of() is rejected when the stored claims are read back.
                List<String> globalRoles = new ArrayList<>();
                if (userEntity.isSuperUser()) {
                    globalRoles.add(AuthorizationServerConfig.SUPER_USER_AUTHORITY);
                }

                // 4. Fetch multi-school access and feature grants dynamically
                List<TokenPayloadDto.SchoolAccess> schoolAccessList = schoolGrantService.getSchoolGrantsForUser(userEntity.getUserId());

                // Every non-super user must belong to at least one active school. This is re-checked against the
                // database on every issuance and refresh, so disabling a school blocks its users immediately.
                if (!userEntity.isSuperUser() && schoolAccessList.isEmpty()) {
                    throw accessDenied("No active school access: your school has been disabled or your access was removed");
                }

                // 5. Inject claims. DTOs are converted to plain maps (honouring @JsonProperty names) because
                //    claims are also persisted by JdbcOAuth2AuthorizationService, which only deserializes JDK types.
                context.getClaims().claims(claims -> {
                    claims.put("user_id", userEntity.getUserId());
                    claims.put("user_details", jsonMapper.convertValue(userDetails, MAP_TYPE));
                    claims.put("roles", globalRoles);
                    claims.put("school_access", jsonMapper.convertValue(schoolAccessList, LIST_OF_MAPS_TYPE));
                });
            }
        };
    }

    /**
     * OTP logins use the email as the principal name. Social logins expose the provider's subject id as the
     * name, so the email is read from the provider attributes instead.
     */
    private static String resolveEmail(Authentication principal) {
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
            return email.toString();
        }
        return principal.getName();
    }

    private static OAuth2AuthenticationException accessDenied(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, description, null));
    }
}
