package com.school.auth.repository;

import com.school.auth.entity.PermissionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    List<PermissionEntity> findByPermissionCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = "feature")
    @Query("SELECT p FROM PermissionEntity p ORDER BY p.permissionCode")
    List<PermissionEntity> findAllWithFeature();
}
