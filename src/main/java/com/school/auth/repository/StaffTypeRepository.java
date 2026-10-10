package com.school.auth.repository;

import com.school.auth.entity.StaffTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffTypeRepository extends JpaRepository<StaffTypeEntity, Long> {
    Optional<StaffTypeEntity> findByTypeCode(String typeCode);
}