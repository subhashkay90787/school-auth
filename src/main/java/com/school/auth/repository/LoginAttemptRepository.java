package com.school.auth.repository;

import com.school.auth.entity.LoginAttemptEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {

    @Query("SELECT a FROM LoginAttemptEntity a WHERE a.schoolId = :schoolId AND a.result <> 'SUCCESS' AND a.result <> 'OTP_SENT' " +
            "AND a.attemptedAt >= :since ORDER BY a.attemptedAt DESC")
    Page<LoginAttemptEntity> findFailures(@Param("schoolId") Long schoolId, @Param("since") OffsetDateTime since, Pageable pageable);
}
