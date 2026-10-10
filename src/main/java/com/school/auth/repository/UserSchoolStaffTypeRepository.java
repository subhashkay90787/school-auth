package com.school.auth.repository;


import com.school.auth.entity.UserSchoolStaffTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSchoolStaffTypeRepository extends JpaRepository<UserSchoolStaffTypeEntity, Long> {

    @Query("SELECT ust FROM UserSchoolStaffTypeEntity ust WHERE ust.user.userId = :userId AND ust.isActive = true")
    List<UserSchoolStaffTypeEntity> findActiveGrantsByUserId(@Param("userId") Long userId);

    @Query("SELECT ust FROM UserSchoolStaffTypeEntity ust JOIN FETCH ust.staffType " +
            "WHERE ust.user.userId = :userId AND ust.school.schoolId = :schoolId AND ust.isActive = true")
    List<UserSchoolStaffTypeEntity> findActiveGrants(@Param("userId") Long userId, @Param("schoolId") Long schoolId);

    // Includes inactive rows, so a removed role can be reactivated (the table has a unique key on user/school/role)
    @Query("SELECT ust FROM UserSchoolStaffTypeEntity ust " +
            "WHERE ust.user.userId = :userId AND ust.school.schoolId = :schoolId AND ust.staffType.typeCode = :typeCode")
    Optional<UserSchoolStaffTypeEntity> findGrant(@Param("userId") Long userId,
                                                  @Param("schoolId") Long schoolId,
                                                  @Param("typeCode") String typeCode);
}