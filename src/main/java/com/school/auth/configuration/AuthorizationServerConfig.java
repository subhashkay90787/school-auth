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
import com.school.auth.service.SessionService;
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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
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

import java.util.Set;

@Configuration
public class AuthorizationServerConfig {

    public static final String SUPER_USER_AUTHORITY = "SUPER_USER";
    /** Scope of internal service clients (school-app-server directory refresh, role-holder lookups). */
    public static final String DIRECTORY_READ_SCOPE = "directory.read";

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
            OtpService otpService,
            SessionService sessionService) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .cors(Customizer.withDefaults())
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
                                                otpService,
                                                sessionService
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
     * Internal service APIs (client_credentials tokens with the directory.read scope).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority("SCOPE_" + DIRECTORY_READ_SCOPE))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * REST APIs, protected by access tokens issued by this server.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/otp/**", "/api/v1/onboarding/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/apps/*/config").permitAll()
                        .requestMatchers("/api/v1/platform/**").hasAuthority(SUPER_USER_AUTHORITY)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/schools/*/status").hasAuthority(SUPER_USER_AUTHORITY)
                        .requestMatchers("/api/v1/schools/*/features/**").hasAuthority(SUPER_USER_AUTHORITY)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasAuthority(SUPER_USER_AUTHORITY)
                        // Branch-level permissions (USER_MANAGE, ROLE_MANAGE, ...) are checked in the services
                        // against the database via SchoolAccessService
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Browser flows: social login (Google, Facebook, Microsoft) backing /oauth2/authorize.
     */
    @Bean
    @Order(4)
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

    /**
     * Clients live in oauth2_registered_client: the generic mobile client and internal service clients are
     * written at startup by {@link OAuthClientBootstrap}; dedicated apps are added by the super user.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
