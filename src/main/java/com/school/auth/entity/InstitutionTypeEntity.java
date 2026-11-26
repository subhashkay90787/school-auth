package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "institution_type", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
public class InstitutionTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "institution_type_id")
    private Long institutionTypeId;

    @Column(name = "type_code", nullable = false, unique = true, length = 30)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "academic_model", nullable = false, length = 20)
    private String academicModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terminology", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> terminology;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
