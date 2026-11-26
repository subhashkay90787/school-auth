package com.school.auth.repository;

import com.school.auth.entity.OrganizationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, Long> {

    boolean existsByOrgCode(String orgCode);

    @EntityGraph(attributePaths = "institutionType")
    Optional<OrganizationEntity> findWithTypeByOrganizationId(Long organizationId);

    @EntityGraph(attributePaths = "institutionType")
    @Query("SELECT o FROM OrganizationEntity o WHERE (:status IS NULL OR o.status = :status) " +
            "AND (:q IS NULL OR lower(o.orgName) LIKE lower(concat('%', :q, '%')) OR lower(o.orgCode) LIKE lower(concat('%', :q, '%')))")
    Page<OrganizationEntity> search(@Param("status") String status, @Param("q") String q, Pageable pageable);
}
