# Project Structure
src/main/resources/application.properties


# pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.school</groupId>
	<artifactId>auth</artifactId>
	<version>0.0.1-SNAPSHOT</version>


	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-authorization-server</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-client</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-authorization-server-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-client-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<executions>
					<execution>
						<id>default-compile</id>
						<phase>compile</phase>
						<goals>
							<goal>compile</goal>
						</goals>
						<configuration>
							<annotationProcessorPaths>
								<path>
									<groupId>org.projectlombok</groupId>
									<artifactId>lombok</artifactId>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</execution>
					<execution>
						<id>default-testCompile</id>
						<phase>test-compile</phase>
						<goals>
							<goal>testCompile</goal>
						</goals>
						<configuration>
							<annotationProcessorPaths>
								<path>
									<groupId>org.projectlombok</groupId>
									<artifactId>lombok</artifactId>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>


## File: src/main/java/com/school/auth/dto/TokenPayloadDto.java
package com.school.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TokenPayloadDto {

    public record UserDetails(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("alternate_phone_number") String alternatePhoneNumber,
            @JsonProperty("user_category") String userCategory,
            @JsonProperty("is_active_flag") boolean isActiveFlag
    ) {}

    public record EnabledFeature(
            @JsonProperty("feature_id") Long featureId,
            @JsonProperty("feature_code") String featureCode,
            @JsonProperty("feature_name") String featureName
    ) {}

    public record SchoolAccess(
            @JsonProperty("school_id") Long schoolId,
            @JsonProperty("school_code") String schoolCode,
            @JsonProperty("school_name") String schoolName,
            @JsonProperty("host_url") String hostUrl,
            String city,
            String state,
            String country,
            String status,
            List<String> roles,
            @JsonProperty("enabled_features") List<EnabledFeature> enabledFeatures
    ) {}
}

## File: src/main/java/com/school/auth/dto/UserCreationDto.java
package com.school.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class UserCreationDto {

    public record UserSchoolAssignmentRequest(
            @NotNull(message = "School ID is required")
            Long schoolId
    ) {}

    public record CreateUserRequest(
            @NotBlank(message = "First name is required")
            String firstName,

            @NotBlank(message = "Last name is required")
            String lastName,

            @NotBlank(message = "Phone number is required")
            String phoneNumber,

            String alternatePhoneNumber,

            @Email(message = "Valid email is required")
            @NotBlank(message = "Email is required")
            String email,

            @NotEmpty(message = "At least one school assignment is required")
            List<UserSchoolAssignmentRequest> schoolAssignments
    ) {}

    public record UserCreatedResponse(
            Long userId,
            String email,
            String phoneNumber,
            String status
    ) {}
}

## File: src/main/java/com/school/auth/dto/CompleteOnboardingRequest.java
package com.school.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteOnboardingRequest(
    @NotBlank(message = "Invitation token is required")
    String token,

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Verified phone number is required")
    String phoneNumber
) {}

## File: src/main/java/com/school/auth/configuration/CustomJwtTokenCustomizerConfig.java
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

## File: src/main/java/com/school/auth/configuration/AuthorizationServerConfig.java
package com.school.auth.configuration;

import com.school.auth.otp.OAuth2OtpAuthenticationConverter;
import com.school.auth.otp.OAuth2OtpAuthenticationProvider;
import com.school.auth.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<?> tokenGenerator,
            UserRepository userRepository) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer
                                .oidc(Customizer.withDefaults()) // Enable OpenID Connect 1.0
                                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                        .accessTokenRequestConverter(new OAuth2OtpAuthenticationConverter())
                                        .authenticationProvider(new OAuth2OtpAuthenticationProvider(
                                                authorizationService,
                                                tokenGenerator,
                                                userRepository
                                        ))
                                )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                // Enable Form Login and Social Logins (Google, Facebook, Microsoft)
                .formLogin(Customizer.withDefaults())
                .oauth2Login(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("school-mobile-app")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // Mobile app PKCE client
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // Register Custom OTP Grant Type
                .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:otp"))
                .redirectUri("com.yourplatform.app://oauth2redirect")
                .scope("openid")
                .scope("profile")
                .scope("email")
                .scope("school_access")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true) // Enforce PKCE for mobile apps
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(90))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }
}

## File: src/main/java/com/school/auth/repository/SchoolRepository.java
package com.school.auth.repository;

import com.school.auth.entity.SchoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<SchoolEntity, Long> {
    Optional<SchoolEntity> findByHostUrl(String hostUrl);
}

## File: src/main/java/com/school/auth/repository/UserSchoolStaffTypeRepository.java
package com.school.auth.repository;


import com.school.auth.entity.UserSchoolStaffTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSchoolStaffTypeRepository extends JpaRepository<UserSchoolStaffTypeEntity, Long> {

    @Query("SELECT ust FROM UserSchoolStaffType ust WHERE ust.user.userId = :userId AND ust.isActive = true")
    List<UserSchoolStaffTypeEntity> findActiveGrantsByUserId(@Param("userId") Long userId);
}

## File: src/main/java/com/school/auth/repository/SchoolInvitationRepository.java
package com.school.auth.repository;

import com.school.auth.entity.SchoolInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolInvitationRepository extends JpaRepository<SchoolInvitation, Long> {
    Optional<SchoolInvitation> findByToken(String token);
}

## File: src/main/java/com/school/auth/repository/StaffTypeRepository.java
package com.school.auth.repository;

import com.school.auth.entity.StaffTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffTypeRepository extends JpaRepository<StaffTypeEntity, Long> {
    Optional<StaffTypeEntity> findByTypeCode(String typeCode);
}

## File: src/main/java/com/school/auth/repository/UserRepository.java
package com.school.auth.repository;

import com.school.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    @Query("SELECT u FROM UserEntity u WHERE u.phoneNumber = :phone OR u.alternatePhoneNumber = :phone")
    Optional<UserEntity> findByPhoneNumberOrAlternate(@Param("phone") String phone);
}

## File: src/main/java/com/school/auth/otp/OAuth2OtpAuthenticationProvider.java
package com.school.auth.otp;

import com.school.auth.entity.UserEntity;
import com.school.auth.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Collections;

public class OAuth2OtpAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final UserRepository userRepository;

    public OAuth2OtpAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            UserRepository userRepository) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.userRepository = userRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2OtpAuthenticationToken otpAuthToken = (OAuth2OtpAuthenticationToken) authentication;

        // 1. Validate the client
        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(otpAuthToken);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        String inputPhone = otpAuthToken.getPhoneNumber();
        String otp = (String) otpAuthToken.getCredentials();

        // 2. Query DB to match either primary or alternative phone number
        UserEntity userEntity = userRepository.findByPhoneNumberOrAlternate(inputPhone)
                .orElseThrow(() -> new BadCredentialsException("No user found with the provided phone number."));

        // 3. Validate OTP logic (Redis, cache, or mock)
        boolean isValidOtp = validateOtpService(inputPhone, otp);
        if (!isValidOtp) {
            throw new BadCredentialsException("Invalid or expired OTP.");
        }

        // 4. Build user principal using the user's email or identity name
        var userDetails = User.withUsername(userEntity.getEmail())
                .password("")
                .authorities(Collections.emptyList())
                .build();

        Authentication userAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        // 5. Build Token Context (This triggers your CustomJwtTokenCustomizerConfig automatically)
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuth)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizationGrantType(OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE)
                .authorizationGrant(otpAuthToken);

        OAuth2TokenContext accessTokenContext = tokenContextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizedScopes(registeredClient.getScopes())
                .build();

        OAuth2Token generatedAccessToken = this.tokenGenerator.generate(accessTokenContext);
        if (generatedAccessToken == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR));
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(),
                generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(),
                accessTokenContext.getAuthorizedScopes()
        );

        // 6. Generate Refresh Token if supported by the client
        OAuth2RefreshToken refreshToken = null;
        // 6. Save authorization state
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(userAuth.getName())
                .authorizationGrantType(OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE)
                .authorizedScopes(registeredClient.getScopes());

        if (generatedAccessToken instanceof ClaimAccessor) {
            authorizationBuilder.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, ((ClaimAccessor) generatedAccessToken).getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }

        if (refreshToken != null) {
            authorizationBuilder.refreshToken(refreshToken);
        }

        OAuth2Authorization authorization = authorizationBuilder.build();
        this.authorizationService.save(authorization);

        // 7. Return using the correct constructor overload (no scopes argument, passing additional parameters map)
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient,
                clientPrincipal,
                accessToken,
                refreshToken,
                otpAuthToken.getAdditionalParameters()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2OtpAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(Authentication authentication) {
        OAuth2ClientAuthenticationToken clientPrincipal = null;
        if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal().getClass())) {
            clientPrincipal = (OAuth2ClientAuthenticationToken) authentication.getPrincipal();
        }
        if (clientPrincipal != null && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
    }

    private boolean validateOtpService(String phone, String otp) {
        // Plug in your Redis or cache verification logic here
        return "123456".equals(otp);
    }
}

## File: src/main/java/com/school/auth/otp/OAuth2OtpAuthenticationConverter.java
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

## File: src/main/java/com/school/auth/otp/OAuth2OtpAuthenticationToken.java
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

## File: src/main/java/com/school/auth/entity/FeatureEntity.java
package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "FEATURE", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_id")
    private Long featureId;

    @Column(name = "feature_code", unique = true, nullable = false, length = 50)
    private String featureCode;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}

## File: src/main/java/com/school/auth/entity/SchoolEntity.java
package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "SCHOOL", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "school_code", unique = true, nullable = false, length = 50)
    private String schoolCode;

    @Column(name = "school_name", nullable = false, length = 150)
    private String schoolName;

    @Column(name = "host_url", nullable = false, length = 255)
    private String hostUrl;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @ManyToMany
    @JoinTable(
            name = "SCHOOL_FEATURE",
            schema = "identity",
            joinColumns = @JoinColumn(name = "school_id"),
            inverseJoinColumns = @JoinColumn(name = "feature_id")
    )
    private List<FeatureEntity> features;
}

## File: src/main/java/com/school/auth/entity/UserSchoolStaffTypeEntity.java
package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "USER_SCHOOL_STAFF_TYPE", schema = "identity",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "school_id", "staff_type_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSchoolStaffTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_school_staff_id")
    private Long userSchoolStaffId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private SchoolEntity school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_type_id", nullable = false)
    private StaffTypeEntity staffType;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate = LocalDate.now();

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}

## File: src/main/java/com/school/auth/entity/UserEntity.java
package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "USER", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "alternate_phone_number", length = 20)
    private String alternatePhoneNumber;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "is_active_flag", nullable = false)
    private boolean isActiveFlag = true;
}

## File: src/main/java/com/school/auth/entity/StaffTypeEntity.java
package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "STAFF_TYPE", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_type_id")
    private Long staffTypeId;

    @Column(name = "type_code", unique = true, nullable = false, length = 50)
    private String typeCode; // e.g., TEACHER, SCHOOL_ADMIN, PARENT

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}

## File: src/main/java/com/school/auth/entity/SchoolInvitation.java
package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "school_invitation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invitationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private SchoolEntity school;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String targetRoleCode; // e.g. PRINCIPAL, ADMIN, TEACHER, STAFF

    @Column(nullable = false)
    private Long invitedByUserId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean isUsed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

## File: src/main/java/com/school/auth/controller/OnboardingController.java
package com.school.auth.controller;

import com.school.auth.dto.CompleteOnboardingRequest;
import com.school.auth.entity.SchoolInvitation;
import com.school.auth.repository.SchoolInvitationRepository;
import com.school.auth.service.UserOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final SchoolInvitationRepository invitationRepository;
    private final UserOnboardingService onboardingService;

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        SchoolInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token."));

        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("This invitation has expired or already been used.");
        }

        return ResponseEntity.ok(new InvitationDetailsResponse(
                invitation.getEmail(),
                invitation.getTargetRoleCode(),
                invitation.getSchool().getSchoolName()
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeOnboarding(@Valid @RequestBody CompleteOnboardingRequest request) {
        var user = onboardingService.completeOnboarding(
                request.token(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber()
        );
        return ResponseEntity.ok(user);
    }
}

record InvitationDetailsResponse(String email, String targetRoleCode, String schoolName) {}

## File: src/main/java/com/school/auth/AuthApplication.java
package com.school.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}

}


## File: src/main/java/com/school/auth/service/SchoolGrantService.java
package com.school.auth.service;

import com.school.auth.dto.TokenPayloadDto;
import com.school.auth.entity.UserSchoolStaffTypeEntity;
import com.school.auth.repository.UserSchoolStaffTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolGrantService {

    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;

    @Transactional(readOnly = true)
    public List<TokenPayloadDto.SchoolAccess> getSchoolGrantsForUser(Long userId) {
        List<UserSchoolStaffTypeEntity> mappings = userSchoolStaffTypeRepository.findActiveGrantsByUserId(userId);

        // Group mappings by School to aggregate multiple roles and features per school
        Map<Long, List<UserSchoolStaffTypeEntity>> groupedBySchool = mappings.stream()
                .collect(Collectors.groupingBy(m -> m.getSchool().getSchoolId()));

        return groupedBySchool.values().stream().map(schoolMappings -> {
            var school = schoolMappings.get(0).getSchool();

            // Extract all roles/staff types for this specific school
            List<String> roles = schoolMappings.stream()
                    .map(m -> m.getStaffType().getTypeCode())
                    .distinct()
                    .collect(Collectors.toList());

            // Extract enabled features for this school
            List<TokenPayloadDto.EnabledFeature> features = school.getFeatures().stream()
                    .filter(f -> f.isActive())
                    .map(f -> new TokenPayloadDto.EnabledFeature(
                            f.getFeatureId(),
                            f.getFeatureCode(),
                            f.getFeatureName()
                    ))
                    .collect(Collectors.toList());

            return new TokenPayloadDto.SchoolAccess(
                    school.getSchoolId(),
                    school.getSchoolCode(),
                    school.getSchoolName(),
                    school.getHostUrl(),
                    school.getCity(),
                    school.getState(),
                    school.getCountry(),
                    school.getStatus(),
                    roles,
                    features
            );
        }).collect(Collectors.toList());
    }
}

## File: src/main/java/com/school/auth/service/UserOnboardingService.java
package com.school.auth.service;

import com.school.auth.entity.*;
import com.school.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserOnboardingService {

    private final SchoolInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final StaffTypeRepository staffTypeRepository;
    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;

    @Transactional
    public UserEntity completeOnboarding(String token, String firstName, String lastName, String verifiedPhone) {
        SchoolInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token."));

        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This invitation has expired or has already been used.");
        }

        // 1. Find or create master user record
        UserEntity user = userRepository.findByEmail(invitation.getEmail())
                .orElseGet(() -> userRepository.save(
                        UserEntity.builder()
                                .email(invitation.getEmail())
                                .phoneNumber(verifiedPhone)
                                .firstName(firstName)
                                .lastName(lastName)
                                .isActiveFlag(true)
                                .build()
                ));

        // 2. Fetch target staff role
        StaffType staffType = staffTypeRepository.findByTypeCode(invitation.getTargetRoleCode())
                .orElseThrow(() -> new IllegalStateException("Role code not configured in master data: " + invitation.getTargetRoleCode()));

        // 3. Map user to school with role
        boolean alreadyMapped = userSchoolStaffTypeRepository.findActiveGrantsByUserId(user.getUserId()).stream()
                .anyMatch(g -> g.getSchool().getSchoolId().equals(invitation.getSchool().getSchoolId()) &&
                        g.getStaffType().getTypeCode().equals(invitation.getTargetRoleCode()));

        if (!alreadyMapped) {
            userSchoolStaffTypeRepository.save(
                    UserSchoolStaffType.builder()
                            .user(user)
                            .school(invitation.getSchool())
                            .staffType(staffType)
                            .assignedDate(LocalDate.now())
                            .isActive(true)
                            .build()
            );
        }

        // 4. Mark invitation as used
        invitation.setUsed(true);
        invitationRepository.save(invitation);

        return user;
    }
}

## File: src/main/java/com/school/auth/service/SchoolInvitationService.java
package com.school.auth.service;

import com.school.auth.entity.SchoolEntity;
import com.school.auth.entity.SchoolInvitation;
import com.school.auth.entity.UserSchoolStaffTypeEntity;
import com.school.auth.repository.SchoolInvitationRepository;
import com.school.auth.repository.SchoolRepository;
import com.school.auth.repository.StaffTypeRepository;
import com.school.auth.repository.UserSchoolStaffTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolInvitationService {

    private final SchoolInvitationRepository invitationRepository;
    private final SchoolRepository schoolRepository;
    private final StaffTypeRepository staffTypeRepository;
    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;

    @Value("${app.frontend.base-url:http://localhost:8080}")
    private String frontendBaseUrl;

    @Transactional
    public String createInvitation(Long inviterUserId, boolean isSuperUser, Long schoolId, String inviteeEmail, String targetRoleCode) {
        // 1. Enforce Role Hierarchy Permission Checks using the active grants query
        if (!isSuperUser) {
            List<UserSchoolStaffTypeEntity> activeGrants = userSchoolStaffTypeRepository.findActiveGrantsByUserId(inviterUserId);

            List<String> inviterRoles = activeGrants.stream()
                    .filter(g -> g.getSchool().getSchoolId().equals(schoolId))
                    .map(g -> g.getStaffType().getTypeCode())
                    .toList();

            boolean canInvite = switch (targetRoleCode.toUpperCase()) {
                case "PRINCIPAL", "ADMIN" -> inviterRoles.contains("SCHOOL_ADMIN");
                case "TEACHER", "STAFF" -> inviterRoles.contains("SCHOOL_ADMIN") ||
                        inviterRoles.contains("PRINCIPAL") ||
                        inviterRoles.contains("ADMIN");
                case "PARENT" -> inviterRoles.contains("SCHOOL_ADMIN") ||
                        inviterRoles.contains("PRINCIPAL") ||
                        inviterRoles.contains("ADMIN") ||
                        inviterRoles.contains("TEACHER");
                default -> false;
            };

            if (!canInvite) {
                throw new SecurityException("You do not have permission to invite a " + targetRoleCode + " to this school.");
            }
        }

        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));

        // 2. Generate secure token
        String token = UUID.randomUUID().toString();

        SchoolInvitation invitation = SchoolInvitation.builder()
                .school(school)
                .email(inviteeEmail)
                .targetRoleCode(targetRoleCode.toUpperCase())
                .invitedByUserId(inviterUserId)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isUsed(false)
                .build();

        invitationRepository.save(invitation);

        // 3. Dispatch Invitation Email using externalized base URL
        String inviteLink = frontendBaseUrl + "/onboard?token=" + token;

        return inviteLink;
    }
}