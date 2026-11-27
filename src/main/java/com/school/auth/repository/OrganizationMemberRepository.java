package com.school.auth.repository;

import com.school.auth.entity.OrganizationMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, Long> {

    @Query("SELECT m FROM OrganizationMemberEntity m JOIN FETCH m.staffType JOIN FETCH m.user " +
            "WHERE m.organizationId = :orgId AND m.isActive = true ORDER BY m.user.firstName")
    List<OrganizationMemberEntity> findActiveByOrganization(@Param("orgId") Long organizationId);

    @Query("SELECT m FROM OrganizationMemberEntity m JOIN FETCH m.staffType " +
            "WHERE m.organizationId = :orgId AND m.user.userId = :userId AND m.isActive = true")
    List<OrganizationMemberEntity> findActive(@Param("orgId") Long organizationId, @Param("userId") Long userId);

    @Query("SELECT m FROM OrganizationMemberEntity m " +
            "WHERE m.organizationId = :orgId AND m.user.userId = :userId AND m.staffType.typeCode = :code")
    Optional<OrganizationMemberEntity> findGrant(@Param("orgId") Long organizationId, @Param("userId") Long userId,
                                                 @Param("code") String typeCode);
}
