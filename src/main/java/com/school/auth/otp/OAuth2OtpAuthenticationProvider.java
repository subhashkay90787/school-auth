package com.school.auth.otp;

import com.school.auth.entity.UserEntity;
import com.school.auth.entity.UserSessionEntity;
import com.school.auth.repository.UserRepository;
import com.school.auth.service.SessionService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
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

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OAuth2OtpAuthenticationProvider implements AuthenticationProvider {

    private static final OAuth2TokenType ID_TOKEN_TOKEN_TYPE = new OAuth2TokenType(OidcParameterNames.ID_TOKEN);

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final SessionService sessionService;

    public OAuth2OtpAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            UserRepository userRepository,
            OtpService otpService,
            SessionService sessionService) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.sessionService = sessionService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2OtpAuthenticationToken otpAuthToken = (OAuth2OtpAuthenticationToken) authentication;

        // 1. Validate the client
        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(otpAuthToken);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (!registeredClient.getAuthorizationGrantTypes().contains(OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        OtpChannel channel = otpAuthToken.getChannel();
        String identifier = OtpService.normalize(channel, otpAuthToken.getIdentifier());
        String otp = (String) otpAuthToken.getCredentials();
        String ip = otpAuthToken.parameter("ip_address");
        String deviceId = otpAuthToken.parameter("device_id");
        Long schoolId = parseLong(otpAuthToken.parameter("school_id"));

        // 2. Validate OTP against Redis first, so the response doesn't reveal whether the identifier is registered
        OtpService.Result result = otpService.verify(channel, identifier, otp);
        if (result != OtpService.Result.OK) {
            sessionService.recordAttempt(identifier, channel.authMethod,
                    switch (result) {
                        case EXPIRED -> "EXPIRED_OTP";
                        case LOCKED -> "LOCKED";
                        default -> "WRONG_OTP";
                    }, null, schoolId, ip, deviceId);
            throw invalidGrant("Invalid or expired OTP.");
        }

        // 3. Find the account: phone matches primary or alternate number; email is case-insensitive
        UserEntity userEntity = (channel == OtpChannel.EMAIL
                ? userRepository.findByEmailIgnoreCase(identifier)
                : userRepository.findByPhoneNumberOrAlternate(identifier))
                .filter(UserEntity::isActiveFlag)
                .orElse(null);
        if (userEntity == null) {
            sessionService.recordAttempt(identifier, channel.authMethod, "WRONG_OTP", null, schoolId, ip, deviceId);
            throw invalidGrant("Invalid or expired OTP.");
        }

        // 4. Principal = user id (email is optional). Resolved again by CustomJwtTokenCustomizerConfig.
        Authentication userAuth = UsernamePasswordAuthenticationToken.authenticated(
                userEntity.getUserId().toString(), null, Collections.emptyList());

        // The session (refresh-token family) exists before the tokens, so the access token can carry its id
        String authorizationId = UUID.randomUUID().toString();
        UserSessionEntity session = sessionService.openSession(userEntity.getUserId(), registeredClient.getClientId(),
                authorizationId, channel.authMethod, new SessionService.DeviceContext(deviceId,
                        otpAuthToken.parameter("device_name"), otpAuthToken.parameter("platform"),
                        otpAuthToken.parameter("app_version"), ip, otpAuthToken.parameter("user_agent"), schoolId));
        otpAuthToken.setAuthorizationId(authorizationId);

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

        OAuth2Token generatedAccessToken;
        try {
            generatedAccessToken = this.tokenGenerator.generate(accessTokenContext);
        } catch (OAuth2AuthenticationException ex) {
            // e.g. no active branch: the customizer refused the token
            sessionService.end(session, "REVOKED_BY_ADMIN", null);
            sessionService.recordAttempt(identifier, channel.authMethod, "BLOCKED_INACTIVE_BRANCH",
                    userEntity.getUserId(), schoolId, ip, deviceId);
            throw ex;
        }
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

        // 6. Save authorization state. The principal attribute is needed later by the refresh_token grant.
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(authorizationId)
                .principalName(userAuth.getName())
                .authorizationGrantType(OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE)
                .authorizedScopes(registeredClient.getScopes())
                .attribute(Principal.class.getName(), userAuth);

        if (generatedAccessToken instanceof ClaimAccessor) {
            authorizationBuilder.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, ((ClaimAccessor) generatedAccessToken).getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }

        // 7. Generate Refresh Token if supported by the client
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2TokenContext refreshTokenContext = tokenContextBuilder
                    .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                    .authorizedScopes(registeredClient.getScopes())
                    .build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(refreshTokenContext);
            if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                        "The token generator failed to generate the refresh token.", null));
            }
            refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
            authorizationBuilder.refreshToken(refreshToken);
        }

        // 8. Generate an OIDC ID token when "openid" is authorized; the refresh_token grant later rotates it
        Map<String, Object> additionalParameters = new HashMap<>();
        if (registeredClient.getScopes().contains(OidcScopes.OPENID)) {
            OAuth2TokenContext idTokenContext = tokenContextBuilder
                    .tokenType(ID_TOKEN_TOKEN_TYPE)
                    .authorizedScopes(registeredClient.getScopes())
                    .build();
            OAuth2Token generatedIdToken = this.tokenGenerator.generate(idTokenContext);
            if (!(generatedIdToken instanceof Jwt jwtIdToken)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                        "The token generator failed to generate the ID token.", null));
            }
            OidcIdToken idToken = new OidcIdToken(jwtIdToken.getTokenValue(), jwtIdToken.getIssuedAt(),
                    jwtIdToken.getExpiresAt(), jwtIdToken.getClaims());
            authorizationBuilder.token(idToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idToken.getClaims()));
            additionalParameters.put(OidcParameterNames.ID_TOKEN, idToken.getTokenValue());
        }

        OAuth2Authorization authorization = authorizationBuilder.build();
        this.authorizationService.save(authorization);
        sessionService.recordAttempt(identifier, channel.authMethod, "SUCCESS", userEntity.getUserId(), schoolId, ip, deviceId);

        // 9. Return the token response (id_token goes in the additional parameters)
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient,
                clientPrincipal,
                accessToken,
                refreshToken,
                additionalParameters
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

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
    }

    private static OAuth2AuthenticationException invalidGrant(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, description, null));
    }
}