package com.school.auth.repository;

import com.school.auth.entity.AppClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppClientRepository extends JpaRepository<AppClientEntity, Long> {
    Optional<AppClientEntity> findByClientId(String clientId);

    List<AppClientEntity> findByOrganizationId(Long organizationId);
}
