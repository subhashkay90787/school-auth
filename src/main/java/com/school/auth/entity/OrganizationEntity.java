package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "organization", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationEntity {

    public static final String STATUS_ONBOARDING = "ONBOARDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "org_code", nullable = false, unique = true, length = 50)
    private String orgCode;

    @Column(name = "org_name", nullable = false, length = 150)
    private String orgName;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_type_id", nullable = false)
    private InstitutionTypeEntity institutionType;

    @Builder.Default
    @Column(name = "deployment_model", nullable = false, length = 20)
    private String deploymentModel = "HOSTED";

    @Column(name = "branch_limit")
    private Integer branchLimit;

    @Column(name = "primary_contact_email")
    private String primaryContactEmail;

    @Column(name = "primary_contact_phone", length = 20)
    private String primaryContactPhone;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ONBOARDING;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
