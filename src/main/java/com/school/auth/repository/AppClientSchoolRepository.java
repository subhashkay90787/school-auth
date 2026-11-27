package com.school.auth.repository;

import com.school.auth.entity.AppClientSchoolEntity;
import com.school.auth.entity.FeatureFlagIds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppClientSchoolRepository extends JpaRepository<AppClientSchoolEntity, FeatureFlagIds.AppClientSchoolId> {
    List<AppClientSchoolEntity> findByAppClientIdOrderBySortOrder(Long appClientId);

    @Modifying
    @Query("DELETE FROM AppClientSchoolEntity a WHERE a.appClientId = :appClientId")
    void deleteByAppClientId(@Param("appClientId") Long appClientId);
}
