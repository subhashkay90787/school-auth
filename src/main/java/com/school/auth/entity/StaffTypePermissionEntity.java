package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

/** Role -> permission. {@code school_id = NULL} is the platform default; set = that school's own mapping. */
@Entity
@Table(name = "staff_type_permission", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffTypePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_type_permission_id")
    private Long staffTypePermissionId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "staff_type_id", nullable = false)
    private Long staffTypeId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    public StaffTypePermissionEntity(Long schoolId, Long staffTypeId, Long permissionId) {
        this.schoolId = schoolId;
        this.staffTypeId = staffTypeId;
        this.permissionId = permissionId;
    }
}
