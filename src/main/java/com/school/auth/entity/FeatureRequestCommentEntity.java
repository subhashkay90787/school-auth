package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "feature_request_comment", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureRequestCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "feature_request_id", nullable = false)
    private Long featureRequestId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Builder.Default
    @Column(name = "is_internal", nullable = false)
    private boolean isInternal = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
