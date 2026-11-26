package com.school.auth.repository;

import com.school.auth.entity.SchoolRoleOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolRoleOverrideRepository extends JpaRepository<SchoolRoleOverrideEntity, Long> {
    Optional<SchoolRoleOverrideEntity> findBySchoolIdAndStaffTypeId(Long schoolId, Long staffTypeId);

    List<SchoolRoleOverrideEntity> findBySchoolId(Long schoolId);
}
