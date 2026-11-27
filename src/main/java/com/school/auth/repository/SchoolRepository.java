package com.school.auth.repository;

import com.school.auth.entity.SchoolEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<SchoolEntity, Long> {
    Optional<SchoolEntity> findByHostUrl(String hostUrl);

    boolean existsBySchoolCode(String schoolCode);

    boolean existsByOrganizationOrganizationIdAndBranchCode(Long organizationId, String branchCode);

    @EntityGraph(attributePaths = {"organization", "institutionType"})
    Optional<SchoolEntity> findWithOrganizationBySchoolId(Long schoolId);

    @EntityGraph(attributePaths = "institutionType")
    List<SchoolEntity> findByOrganizationOrganizationIdOrderByIsMainBranchDescBranchName(Long organizationId);
}
