package com.school.auth.repository;

import com.school.auth.entity.FeatureFlagIds;
import com.school.auth.entity.OrganizationFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationFeatureRepository extends JpaRepository<OrganizationFeatureEntity, FeatureFlagIds.OrganizationFeatureId> {
    List<OrganizationFeatureEntity> findByOrganizationId(Long organizationId);
}
