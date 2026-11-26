package com.school.auth.repository;

import com.school.auth.entity.FeatureFlagIds;
import com.school.auth.entity.SchoolFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolFeatureRepository extends JpaRepository<SchoolFeatureEntity, FeatureFlagIds.SchoolFeatureId> {
    List<SchoolFeatureEntity> findBySchoolId(Long schoolId);
}
