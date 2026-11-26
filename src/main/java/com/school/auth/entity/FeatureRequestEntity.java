package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "feature_request", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_request_id")
    private Long featureRequestId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "category", nullable = false, length = 20)
    private String category = "NEW_FEATURE";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "NEW";

    @Column(name = "priority", length = 2)
    private String priority;

    @Builder.Default
    @Column(name = "source", nullable = false, length = 20)
    private String source = "TENANT";

    @Column(name = "institution_type_id")
    private Long institutionTypeId;

    @Column(name = "feature_id")
    private Long featureId;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "requested_by_school_id")
    private Long requestedBySchoolId;

    @Column(name = "duplicate_of_id")
    private Long duplicateOfId;

    @Column(name = "target_release", length = 30)
    private String targetRelease;

    @Column(name = "vote_count", nullable = false, insertable = false, updatable = false)
    private int voteCount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
