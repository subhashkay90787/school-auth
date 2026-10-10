package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "STAFF_TYPE", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_type_id")
    private Long staffTypeId;

    @Column(name = "type_code", unique = true, nullable = false, length = 50)
    private String typeCode; // e.g., TEACHER, SYS_ADMIN, PARENT

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}