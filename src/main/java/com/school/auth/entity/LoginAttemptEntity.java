package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "login_attempt", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "identifier_hash", nullable = false, length = 64)
    private String identifierHash;

    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "result", nullable = false, length = 30)
    private String result;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Builder.Default
    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt = OffsetDateTime.now();
}
