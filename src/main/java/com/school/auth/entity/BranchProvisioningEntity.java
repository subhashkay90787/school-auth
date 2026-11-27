package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** One branch of one onboarding-wizard run (batch). Copies platform config only, never school data. */
@Entity
@Table(name = "branch_provisioning", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProvisioningEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provisioning_id")
    private Long provisioningId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "template_school_id")
    private Long templateSchoolId;

    @Builder.Default
    @Column(name = "copy_features", nullable = false)
    private boolean copyFeatures = true;

    @Builder.Default
    @Column(name = "copy_custom_roles", nullable = false)
    private boolean copyCustomRoles = false;

    @Builder.Default
    @Column(name = "copy_role_mappings", nullable = false)
    private boolean copyRoleMappings = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
