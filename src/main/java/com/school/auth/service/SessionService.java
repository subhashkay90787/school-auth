package com.school.auth.service;

import com.school.auth.entity.LoginAttemptEntity;
import com.school.auth.entity.UserDeviceEntity;
import com.school.auth.entity.UserSessionEntity;
import com.school.auth.otp.OtpService;
import com.school.auth.repository.LoginAttemptRepository;
import com.school.auth.repository.UserDeviceRepository;
import com.school.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Sessions, devices and sign-in attempts. A session is one refresh-token family: its
 * {@code refresh_token_family_id} is the id of the oauth2_authorization row, so ending a session removes that
 * row and the refresh token stops working (the access token lapses within its 30-minute TTL).
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);
    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS", "WEB");

    private final UserSessionRepository sessionRepository;
    private final UserDeviceRepository deviceRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final OAuth2AuthorizationService authorizationService;

    public record DeviceContext(String deviceId, String deviceName, String platform, String appVersion,
                                String ipAddress, String userAgent, Long schoolId) {
        public String normalizedPlatform() {
            String p = platform == null ? null : platform.toUpperCase();
            return PLATFORMS.contains(p) ? p : null;
        }
    }

    @Transactional
    public UserSessionEntity openSession(Long userId, String clientId, String authorizationId, String authMethod,
                                         DeviceContext device) {
        Long userDeviceId = null;
        if (StringUtils.hasText(device.deviceId()) && device.normalizedPlatform() != null) {
            userDeviceId = upsertDevice(userId, clientId, device.deviceId(), device.deviceName(),
                    device.normalizedPlatform(), device.appVersion(), null).getUserDeviceId();
        }
        return sessionRepository.save(UserSessionEntity.builder()
                .sessionId(UUID.randomUUID())
                .userId(userId)
                .schoolId(device.schoolId())
                .userDeviceId(userDeviceId)
                .clientId(clientId)
                .refreshTokenFamilyId(authorizationId)
                .authMethod(authMethod)
                .deviceId(device.deviceId())
                .platform(device.normalizedPlatform())
                .appVersion(device.appVersion())
                .ipAddress(device.ipAddress())
                .userAgent(device.userAgent())
                .build());
    }

    /**
     * Called on every token issuance for an existing authorization (refresh, code exchange).
     * Creates the session for social sign-ins, which have no OTP step.
     */
    @Transactional
    public UserSessionEntity touchOrOpen(Long userId, String clientId, String authorizationId) {
        UserSessionEntity session = sessionRepository.findByRefreshTokenFamilyId(authorizationId)
                .orElseGet(() -> openSession(userId, clientId, authorizationId, "SOCIAL",
                        new DeviceContext(null, null, null, null, null, null, null)));
        if (!session.isOpen()) {
            throw new IllegalStateException("This session has ended. Sign in again.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (session.getLastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            session.setLastSeenAt(now);
        }
        return session;
    }

    @Transactional(readOnly = true)
    public List<UserSessionEntity> openSessions(Long userId) {
        return sessionRepository.findByUserIdAndLogoutAtIsNullOrderByLastSeenAtDesc(userId);
    }

    @Transactional
    public void endSession(UUID sessionId, String reason, Long actorUserId) {
        UserSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
        end(session, reason, actorUserId);
    }

    @Transactional
    public void end(UserSessionEntity session, String reason, Long actorUserId) {
        if (!session.isOpen()) {
            return;
        }
        session.setLogoutAt(OffsetDateTime.now());
        session.setEndReason(reason);
        session.setRevokedByUserId(actorUserId);
        OAuth2Authorization authorization = authorizationService.findById(session.getRefreshTokenFamilyId());
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
    }

    @Transactional
    public int endAllSessions(Long userId, String reason, Long actorUserId, UUID keepSessionId) {
        List<UserSessionEntity> open = openSessions(userId).stream()
                .filter(s -> !s.getSessionId().equals(keepSessionId))
                .toList();
        open.forEach(s -> end(s, reason, actorUserId));
        return open.size();
    }

    @Transactional
    public UserDeviceEntity upsertDevice(Long userId, String clientId, String deviceId, String deviceName,
                                         String platform, String appVersion, String pushToken) {
        UserDeviceEntity device = deviceRepository.findByUserIdAndClientIdAndDeviceId(userId, clientId, deviceId)
                .orElseGet(() -> UserDeviceEntity.builder()
                        .userId(userId).clientId(clientId).deviceId(deviceId).platform(platform).build());
        if (pushToken != null) {
            // A push token moves with the physical device: detach it from any other registration first
            deviceRepository.findByClientIdAndPushToken(clientId, pushToken)
                    .filter(other -> !other.getUserDeviceId().equals(device.getUserDeviceId()))
                    .ifPresent(other -> {
                        other.setPushToken(null);
                        deviceRepository.saveAndFlush(other);
                    });
            device.setPushToken(pushToken);
        }
        if (deviceName != null) {
            device.setDeviceName(deviceName);
        }
        if (appVersion != null) {
            device.setAppVersion(appVersion);
        }
        device.setPlatform(platform);
        device.setActive(true);
        device.setLastSeenAt(OffsetDateTime.now());
        return deviceRepository.save(device);
    }

    /** Recorded in its own transaction so failed sign-ins are kept even though the grant fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(String normalizedIdentifier, String method, String result, Long userId, Long schoolId,
                              String ipAddress, String deviceId) {
        loginAttemptRepository.save(LoginAttemptEntity.builder()
                .identifierHash(OtpService.identifierHash(normalizedIdentifier))
                .method(method)
                .result(result)
                .userId(userId)
                .schoolId(schoolId)
                .ipAddress(ipAddress)
                .deviceId(deviceId)
                .build());
    }
}
