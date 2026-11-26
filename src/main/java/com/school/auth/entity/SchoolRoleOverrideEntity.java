package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Marks a built-in role whose mapping a school has customized (its school rows replace the defaults). */
@Entity
@Table(name = "school_role_override", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
public class SchoolRoleOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "school_role_override_id")
    private Long schoolRoleOverrideId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "staff_type_id", nullable = false)
    private Long staffTypeId;

    @Column(name = "customized_by_user_id")
    private Long customizedByUserId;

    @Column(name = "customized_at", nullable = false)
    private OffsetDateTime customizedAt = OffsetDateTime.now();
}
