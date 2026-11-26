package com.school.auth.repository;

import com.school.auth.entity.FeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<FeatureEntity, Long> {
    Optional<FeatureEntity> findByFeatureCode(String featureCode);

    List<FeatureEntity> findByFeatureCodeIn(Collection<String> featureCodes);

    List<FeatureEntity> findAllByOrderByFeatureCode();
}
