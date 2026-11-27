package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Invitation into one branch ({@code school}) or org-wide ({@code organization}); exactly one is set. */
@Entity
@Table(name = "school_invitation", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invitationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private SchoolEntity school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private OrganizationEntity organization;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String targetRoleCode; // built-in or the school's custom role code

    @Column(nullable = false)
    private Long invitedByUserId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "is_used", nullable = false)
    private boolean isUsed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isOrgWide() {
        return organization != null;
    }

    public String targetName() {
        return isOrgWide() ? organization.getOrgName() : school.getSchoolName();
    }

    public boolean targetIsDisabled() {
        return isOrgWide()
                ? OrganizationEntity.STATUS_INACTIVE.equals(organization.getStatus())
                : SchoolEntity.STATUS_INACTIVE.equals(school.getStatus());
    }
}
