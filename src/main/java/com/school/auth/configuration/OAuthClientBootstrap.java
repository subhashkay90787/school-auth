package com.school.auth.configuration;

import com.school.auth.otp.OAuth2OtpAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Writes the configuration-managed OAuth clients on every start (JdbcRegisteredClientRepository.save updates
 * an existing row with the same id):
 * <ul>
 *   <li>the generic mobile/web app (public PKCE client; OTP + refresh_token grants),</li>
 *   <li>the school-app-server internal client (client_credentials, scope directory.read), when a secret is set.</li>
 * </ul>
 * Dedicated apps are created through the super-user API and are not touched here.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class OAuthClientBootstrap implements ApplicationRunner {

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;

    @Value("${oauth2.client.mobile.client-id}")
    private String mobileClientId;

    @Value("${oauth2.client.mobile.redirect-uri:com.yourplatform.app://oauth2redirect}")
    private String mobileRedirectUri;

    @Value("${oauth2.token.access-token-time-to-live-minutes}")
    private long accessTokenTtlMinutes;

    @Value("${oauth2.token.refresh-token-time-to-live-days}")
    private long refreshTokenTtlDays;

    @Value("${oauth2.client.internal.client-id:school-app-server}")
    private String internalClientId;

    @Value("${oauth2.client.internal.client-secret:}")
    private String internalClientSecret;

    @Override
    public void run(ApplicationArguments args) {
        clients.save(publicAppClient(mobileClientId, mobileClientId, "SchoolCRM app", mobileRedirectUri,
                accessTokenTtlMinutes, refreshTokenTtlDays));
        log.info("OAuth client {} registered", mobileClientId);

        if (StringUtils.hasText(internalClientSecret)) {
            RegisteredClient existing = clients.findByClientId(internalClientId);
            clients.save(RegisteredClient.withId(existing != null ? existing.getId() : internalClientId)
                    .clientId(internalClientId)
                    .clientName("school-app-server (internal)")
                    .clientSecret(passwordEncoder.encode(internalClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope(AuthorizationServerConfig.DIRECTORY_READ_SCOPE)
                    .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(10)).build())
                    .build());
            log.info("Internal OAuth client {} registered", internalClientId);
        }
    }

    /** Settings shared by the generic app and every dedicated app. */
    public static RegisteredClient publicAppClient(String id, String clientId, String name, String redirectUri,
                                                   long accessTtlMinutes, long refreshTtlDays) {
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientName(name)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public PKCE client
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(OAuth2OtpAuthenticationToken.OTP_GRANT_TYPE)
                .redirectUri(redirectUri)
                .scope("openid")
                .scope("profile")
                .scope("email")
                .scope("school_access")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(accessTtlMinutes))
                        .refreshTokenTimeToLive(Duration.ofDays(refreshTtlDays))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }
}
