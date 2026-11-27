package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permission", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "permission_code", nullable = false, unique = true, length = 80)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 150)
    private String permissionName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id")
    private FeatureEntity feature;
}
