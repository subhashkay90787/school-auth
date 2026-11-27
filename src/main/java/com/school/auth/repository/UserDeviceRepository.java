package com.school.auth.repository;

import com.school.auth.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, Long> {
    Optional<UserDeviceEntity> findByUserIdAndClientIdAndDeviceId(Long userId, String clientId, String deviceId);

    Optional<UserDeviceEntity> findByClientIdAndPushToken(String clientId, String pushToken);

    List<UserDeviceEntity> findByUserIdAndIsActiveTrueOrderByLastSeenAtDesc(Long userId);
}
