package com.school.auth.repository;

import com.school.auth.entity.StaffTypePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StaffTypePermissionRepository extends JpaRepository<StaffTypePermissionEntity, Long> {

    @Modifying
    @Query("DELETE FROM StaffTypePermissionEntity m WHERE m.schoolId = :schoolId AND m.staffTypeId = :staffTypeId")
    void deleteSchoolMapping(@Param("schoolId") Long schoolId, @Param("staffTypeId") Long staffTypeId);

    @Query("SELECT m FROM StaffTypePermissionEntity m WHERE m.schoolId = :schoolId")
    List<StaffTypePermissionEntity> findSchoolMappings(@Param("schoolId") Long schoolId);
}
