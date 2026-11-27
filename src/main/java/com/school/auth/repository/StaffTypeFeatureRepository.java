package com.school.auth.repository;

import com.school.auth.entity.StaffTypeFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StaffTypeFeatureRepository extends JpaRepository<StaffTypeFeatureEntity, Long> {

    @Modifying
    @Query("DELETE FROM StaffTypeFeatureEntity m WHERE m.schoolId = :schoolId AND m.staffTypeId = :staffTypeId")
    void deleteSchoolMapping(@Param("schoolId") Long schoolId, @Param("staffTypeId") Long staffTypeId);

    @Query("SELECT m FROM StaffTypeFeatureEntity m WHERE m.schoolId IS NULL AND m.staffTypeId = :staffTypeId")
    List<StaffTypeFeatureEntity> findDefaults(@Param("staffTypeId") Long staffTypeId);

    @Query("SELECT m FROM StaffTypeFeatureEntity m WHERE m.schoolId = :schoolId")
    List<StaffTypeFeatureEntity> findSchoolMappings(@Param("schoolId") Long schoolId);
}
