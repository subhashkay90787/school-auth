package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * One branch: the tenant key ({@code school_id}) used by every school-app-server table.
 */
@Entity
@Table(name = "school", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolEntity {

    public static final String STATUS_ONBOARDING = "ONBOARDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "school_id")
    private Long schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "parent_school_id")
    private Long parentSchoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_type_id", nullable = false)
    private InstitutionTypeEntity institutionType;

    @Column(name = "school_code", unique = true, nullable = false, length = 50)
    private String schoolCode;

    @Column(name = "school_name", nullable = false, length = 150)
    private String schoolName;

    @Column(name = "branch_code", nullable = false, length = 50)
    private String branchCode;

    @Column(name = "branch_name", nullable = false, length = 150)
    private String branchName;

    @Builder.Default
    @Column(name = "is_main_branch", nullable = false)
    private boolean isMainBranch = false;

    @Column(name = "host_url", unique = true, nullable = false)
    private String hostUrl;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ONBOARDING;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "udise_code", length = 20)
    private String udiseCode;

    @Column(name = "board", length = 20)
    private String board;

    @Column(name = "affiliation_number", length = 50)
    private String affiliationNumber;

    @Column(name = "brand_color", length = 9)
    private String brandColor;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Asia/Kolkata";

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "perm_version", nullable = false, insertable = false, updatable = false)
    private Long permVersion;

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
