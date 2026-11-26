package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

/** Role -> feature. {@code school_id = NULL} is the platform default; set = that school's own mapping. */
@Entity
@Table(name = "staff_type_feature", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffTypeFeatureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_type_feature_id")
    private Long staffTypeFeatureId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "staff_type_id", nullable = false)
    private Long staffTypeId;

    @Column(name = "feature_id", nullable = false)
    private Long featureId;

    public StaffTypeFeatureEntity(Long schoolId, Long staffTypeId, Long featureId) {
        this.schoolId = schoolId;
        this.staffTypeId = staffTypeId;
        this.featureId = featureId;
    }
}
