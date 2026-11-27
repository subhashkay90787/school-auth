package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "school_feature", schema = "identity")
@IdClass(FeatureFlagIds.SchoolFeatureId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchoolFeatureEntity {

    @Id
    @Column(name = "school_id")
    private Long schoolId;

    @Id
    @Column(name = "feature_id")
    private Long featureId;

    @Column(name = "enabled_date", nullable = false)
    private LocalDate enabledDate = LocalDate.now();

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;
}
