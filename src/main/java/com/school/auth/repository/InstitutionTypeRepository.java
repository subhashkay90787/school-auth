package com.school.auth.repository;

import com.school.auth.entity.InstitutionTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstitutionTypeRepository extends JpaRepository<InstitutionTypeEntity, Long> {
    Optional<InstitutionTypeEntity> findByTypeCode(String typeCode);
}
