package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One sign-in. Lives as long as its refresh-token family (the oauth2_authorization row with the same id). */
@Entity
@Table(name = "user_session", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "user_device_id")
    private Long userDeviceId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "refresh_token_family_id", nullable = false, unique = true, length = 100)
    private String refreshTokenFamilyId;

    @Column(name = "auth_method", nullable = false, length = 20)
    private String authMethod;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "platform", length = 10)
    private String platform;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Builder.Default
    @Column(name = "login_at", nullable = false)
    private OffsetDateTime loginAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    @Column(name = "logout_at")
    private OffsetDateTime logoutAt;

    @Column(name = "end_reason", length = 20)
    private String endReason;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    public boolean isOpen() {
        return logoutAt == null;
    }
}
