package com.school.auth.repository;

import com.school.auth.entity.FeatureRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureRequestRepository extends JpaRepository<FeatureRequestEntity, Long> {

    @Query("SELECT f FROM FeatureRequestEntity f WHERE (:status IS NULL OR f.status = :status) " +
            "AND (:category IS NULL OR f.category = :category)")
    Page<FeatureRequestEntity> search(@Param("status") String status, @Param("category") String category, Pageable pageable);
}
