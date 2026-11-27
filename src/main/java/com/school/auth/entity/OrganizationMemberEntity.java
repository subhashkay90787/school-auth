package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Org-wide role: applies in every active branch of the organization. */
@Entity
@Table(name = "organization_member", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "organization_member_id")
    private Long organizationMemberId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_type_id", nullable = false)
    private StaffTypeEntity staffType;

    @Builder.Default
    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate = LocalDate.now();

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
