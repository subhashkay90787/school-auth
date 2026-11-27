package com.school.auth.repository;

import com.school.auth.entity.BranchProvisioningEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BranchProvisioningRepository extends JpaRepository<BranchProvisioningEntity, Long> {
    List<BranchProvisioningEntity> findByBatchIdOrderByProvisioningId(UUID batchId);
}
