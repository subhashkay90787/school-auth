package com.school.auth.configuration;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.school.auth.otp.OAuth2OtpAuthenticationConverter;
import com.school.auth.otp.OAuth2OtpAuthenticationProvider;
import com.school.auth.otp.OAuth2OtpAuthenticationToken;
import com.school.auth.otp.OtpService;
import com.school.auth.otp.PublicClientGrantAuthenticationConverter;
import com.school.auth.otp.PublicClientGrantAuthenticationProvider;
import com.school.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Set;

@Configuration
public class AuthorizationServerConfig {

    public static final String SUPER_USER_AUTHORITY = "SUPER_USER";

    /**
     * OAuth2 / OIDC protocol endpoints: /oauth2/token, /oauth2/authorize, /oauth2/jwks, /userinfo, ...
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<?> tokenGenerator,
            RegisteredClientRepository registeredClientRepository,
            UserRepository userRepository,
            OtpService otpService) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer
                                .oidc(Customizer.withDefaults()) // Enable OpenID Connect 1.0
                                // Let the public mobile client use the OTP and refresh_token grants without a client secret
                                .clientAuthentication(clientAuthentication -> clientAuthentication
                                        .authenticationConverter(new PublicClientGrantAuthenticationConverter())
                                        .authenticationProvider(new PublicClientGrantAuthenticationProvider(registeredClientRepository))
                                )
                                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                        .accessTokenRequestConverter(new OAuth2OtpAuthenticationConverter())
                                        .authenticationProvider(new OAuth2OtpAuthenticationProvider(
                                                authorizationService,
                                                tokenGenerator,
                                                userRepository,
                                                otpService
                                        ))
                                )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                // Bearer tokens for the OIDC /userinfo endpoint
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                // Browsers hitting /oauth2/authorize unauthenticated are sent to the social login page
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                browserRequestMatcher())
                );

        return http.build();
    }

    // Matches real browser navigations only; API clients sending a wildcard Accept header get a 401, not a redirect
    private static MediaTypeRequestMatcher browserRequestMatcher() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }

    /**
     * REST APIs, protected by access tokens issued by this server.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/otp/**", "/api/v1/onboarding/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/schools").hasAuthority(SUPER_USER_AUTHORITY)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/schools/*/status").hasAuthority(SUPER_USER_AUTHORITY)
                        .requestMatchers("/api/v1/schools/*/features/**").hasAuthority(SUPER_USER_AUTHORITY)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasAuthority(SUPER_USER_AUTHORITY)
                        // Invite/remove permissions depend on the caller's per-school role; checked in the services via RoleHierarchy
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Browser flows: social login (Google, Facebook, Microsoft) backing /oauth2/authorize.
     */
    /**
     * Browser flows: social login (Google, Facebook, Microsoft) backing /oauth2/authorize.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login/**",
                                "/oauth2/**",
                                "/error",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Maps the "roles" claim (e.g. SUPER_USER) straight to Spring authorities, without a prefix.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * Issued tokens are persisted in Postgres so refresh tokens survive restarts.
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        // Token claims are stored as JSON and read back through Spring Security's type allowlist;
        // extend it so numeric claims (user_id, school_id, feature_id) survive the refresh_token grant.
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(
                        getClass().getClassLoader(),
                        BasicPolymorphicTypeValidator.builder().allowIfSubType(Long.class)))
                .build();

        JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(registeredClientRepository, jsonMapper);

        JdbcOAuth2AuthorizationService authorizationService =
                new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
        authorizationService.setAuthorizationRowMapper(rowMapper);
        authorizationService.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper));
        return authorizationService;
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(jwtTokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2RefreshTokenGenerator());
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            @Value("${oauth2.client.mobile.client-id}") String mobileClientId,
            @Value("${oauth2.token.access-token-time-to-live-minutes}") long accessTokenTtlMinutes,
            @Value("${oauth2.token.refresh-token-time-to-live-days}") long refreshTokenTtlDays) {
        // Stable id: stored authorizations reference it, so it must not change across restarts
        RegisteredClient registeredClient = RegisteredClient.withId(mobileClientId)
                .clientId(mobileClientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // Mobile app PKCE client
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // Register Custom OTP Grant Type
                .authorizationGrantType(OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE)
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
                        .accessTokenTimeToLive(Duration.ofMinutes(accessTokenTtlMinutes))
                        .refreshTokenTimeToLive(Duration.ofDays(refreshTokenTtlDays))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }
}
