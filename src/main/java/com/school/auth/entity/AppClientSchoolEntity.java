package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_client_school", schema = "identity")
@IdClass(FeatureFlagIds.AppClientSchoolId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppClientSchoolEntity {

    @Id
    @Column(name = "app_client_id")
    private Long appClientId;

    @Id
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime addedAt;
}
