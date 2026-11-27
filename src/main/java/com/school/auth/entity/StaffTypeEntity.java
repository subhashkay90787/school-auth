package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A role. Built-in roles have {@code school_id = NULL}; custom roles belong to one school and reuse the
 * app module of their base built-in role.
 */
@Entity
@Table(name = "staff_type", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffTypeEntity {

    public static final String SUPER_USER = "SUPER_USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_type_id")
    private Long staffTypeId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "type_code", nullable = false, length = 50)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Builder.Default
    @Column(name = "level", nullable = false)
    private int level = 50;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_staff_type_id")
    private StaffTypeEntity baseStaffType;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public boolean isBuiltIn() {
        return schoolId == null;
    }

    /** The built-in role whose module this role uses (itself for built-ins without an alias). */
    public String getBaseRoleCode() {
        return baseStaffType != null ? baseStaffType.getTypeCode() : typeCode;
    }
}
