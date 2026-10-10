package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "FEATURE", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_id")
    private Long featureId;

    @Column(name = "feature_code", unique = true, nullable = false, length = 50)
    private String featureCode;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}