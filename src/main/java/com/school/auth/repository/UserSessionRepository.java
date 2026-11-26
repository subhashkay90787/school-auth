package com.school.auth.repository;

import com.school.auth.entity.UserSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

    Optional<UserSessionEntity> findByRefreshTokenFamilyId(String familyId);

    List<UserSessionEntity> findByUserIdAndLogoutAtIsNullOrderByLastSeenAtDesc(Long userId);

    /** Open sessions of anyone holding a role in the branch (directly or org-wide). */
    @Query("SELECT s FROM UserSessionEntity s WHERE s.logoutAt IS NULL AND (s.schoolId = :schoolId OR s.userId IN (" +
            "SELECT g.user.userId FROM UserSchoolStaffTypeEntity g WHERE g.school.schoolId = :schoolId AND g.isActive = true)) " +
            "ORDER BY s.lastSeenAt DESC")
    Page<UserSessionEntity> findOpenForSchool(@Param("schoolId") Long schoolId, Pageable pageable);
}
