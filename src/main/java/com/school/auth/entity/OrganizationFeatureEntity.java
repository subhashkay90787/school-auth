package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Entitlement ceiling: branches can only enable features their organization holds. */
@Entity
@Table(name = "organization_feature", schema = "identity")
@IdClass(FeatureFlagIds.OrganizationFeatureId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationFeatureEntity {

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @Id
    @Column(name = "feature_id")
    private Long featureId;

    @Column(name = "enabled_date", nullable = false)
    private LocalDate enabledDate = LocalDate.now();

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;
}
