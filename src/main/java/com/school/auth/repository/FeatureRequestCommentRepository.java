package com.school.auth.repository;

import com.school.auth.entity.FeatureRequestCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureRequestCommentRepository extends JpaRepository<FeatureRequestCommentEntity, Long> {
    List<FeatureRequestCommentEntity> findByFeatureRequestIdOrderByCreatedAt(Long featureRequestId);

    List<FeatureRequestCommentEntity> findByFeatureRequestIdAndIsInternalFalseOrderByCreatedAt(Long featureRequestId);
}
