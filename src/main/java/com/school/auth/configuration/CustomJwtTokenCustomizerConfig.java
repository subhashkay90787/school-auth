package com.school.auth.configuration;

import com.school.auth.dto.TokenPayloadDto;
import com.school.auth.entity.UserEntity;
import com.school.auth.repository.UserRepository;
import com.school.auth.service.SchoolGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class CustomJwtTokenCustomizerConfig {

    private final UserRepository userRepository;
    private final SchoolGrantService schoolGrantService;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            // Target only ACCESS_TOKEN generation
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication principal = context.getPrincipal();

                // Assuming principal.getName() returns the unique email or user ID used for authentication
                String identifier = principal.getName();

                // 1. Fetch user entity dynamically from the database
                UserEntity userEntity = userRepository.findByEmail(identifier)
                        .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database: " + identifier));

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

                // 3. Extract global Spring Security roles/authorities
                List<String> globalRoles = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList());

                // 4. Fetch multi-school access and feature grants dynamically
                List<TokenPayloadDto.SchoolAccess> schoolAccessList = schoolGrantService.getSchoolGrantsForUser(userEntity.getUserId());

                // 5. Inject exact claims into the JWT ClaimsSet
                context.getClaims().claims(claims -> {
                    claims.put("user_details", userDetails);
                    claims.put("roles", globalRoles);
                    claims.put("school_access", schoolAccessList);
                });
            }
        };
    }
}