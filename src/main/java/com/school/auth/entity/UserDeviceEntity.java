package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_device", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_id")
    private Long userDeviceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    @Column(name = "push_token", length = 500)
    private String pushToken;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Builder.Default
    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "is_trusted", nullable = false)
    private boolean isTrusted = false;
}
