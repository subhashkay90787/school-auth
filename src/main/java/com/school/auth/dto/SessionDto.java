package com.school.auth.dto;

import com.school.auth.entity.LoginAttemptEntity;
import com.school.auth.entity.UserDeviceEntity;
import com.school.auth.entity.UserSessionEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class SessionDto {

    private SessionDto() {}

    public record SessionResponse(UUID sessionId, Long userId, Long schoolId, String clientId, String authMethod,
                                  String deviceId, String platform, String appVersion, String ipAddress,
                                  OffsetDateTime loginAt, OffsetDateTime lastSeenAt, boolean current) {
        public static SessionResponse from(UserSessionEntity s, UUID currentSessionId) {
            return new SessionResponse(s.getSessionId(), s.getUserId(), s.getSchoolId(), s.getClientId(), s.getAuthMethod(),
                    s.getDeviceId(), s.getPlatform(), s.getAppVersion(), s.getIpAddress(), s.getLoginAt(), s.getLastSeenAt(),
                    s.getSessionId().equals(currentSessionId));
        }
    }

    public record LoginAttemptResponse(Long attemptId, Long userId, String method, String result, String ipAddress,
                                       String deviceId, OffsetDateTime attemptedAt) {
        public static LoginAttemptResponse from(LoginAttemptEntity a) {
            return new LoginAttemptResponse(a.getAttemptId(), a.getUserId(), a.getMethod(), a.getResult(),
                    a.getIpAddress(), a.getDeviceId(), a.getAttemptedAt());
        }
    }

    /** Register or refresh this device's push token for the calling app. */
    public record RegisterDeviceRequest(
            @NotBlank String deviceId,
            String deviceName,
            @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform,
            String appVersion,
            String pushToken) {}

    public record DeviceResponse(Long userDeviceId, String clientId, String deviceId, String deviceName, String platform,
                                 String appVersion, boolean hasPushToken, boolean trusted,
                                 OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt) {
        public static DeviceResponse from(UserDeviceEntity d) {
            return new DeviceResponse(d.getUserDeviceId(), d.getClientId(), d.getDeviceId(), d.getDeviceName(), d.getPlatform(),
                    d.getAppVersion(), d.getPushToken() != null, d.isTrusted(), d.getFirstSeenAt(), d.getLastSeenAt());
        }
    }
}
