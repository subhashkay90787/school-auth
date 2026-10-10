package com.school.auth.repository;


import com.school.auth.entity.UserSchoolStaffTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSchoolStaffTypeRepository extends JpaRepository<UserSchoolStaffTypeEntity, Long> {

    @Query("SELECT ust FROM UserSchoolStaffType ust WHERE ust.user.userId = :userId AND ust.isActive = true")
    List<UserSchoolStaffTypeEntity> findActiveGrantsByUserId(@Param("userId") Long userId);
}