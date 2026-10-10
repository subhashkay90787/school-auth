package com.school.auth.repository;

import com.school.auth.entity.SchoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<SchoolEntity, Long> {
    Optional<SchoolEntity> findByHostUrl(String hostUrl);

    boolean existsBySchoolCode(String schoolCode);
}