package com.school.auth.repository;

import com.school.auth.entity.UserSchoolStaffTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSchoolStaffTypeRepository extends JpaRepository<UserSchoolStaffTypeEntity, Long> {

    @Query("SELECT ust FROM UserSchoolStaffTypeEntity ust JOIN FETCH ust.staffType " +
            "WHERE ust.user.userId = :userId AND ust.school.schoolId = :schoolId AND ust.isActive = true")
    List<UserSchoolStaffTypeEntity> findActiveGrants(@Param("userId") Long userId, @Param("schoolId") Long schoolId);

    // Includes inactive rows, so a removed role can be reactivated (unique key on user/school/role)
    @Query("SELECT ust FROM UserSchoolStaffTypeEntity ust " +
            "WHERE ust.user.userId = :userId AND ust.school.schoolId = :schoolId AND ust.staffType.staffTypeId = :staffTypeId")
    Optional<UserSchoolStaffTypeEntity> findGrant(@Param("userId") Long userId,
                                                  @Param("schoolId") Long schoolId,
                                                  @Param("staffTypeId") Long staffTypeId);

    @Query("SELECT count(ust) > 0 FROM UserSchoolStaffTypeEntity ust WHERE ust.staffType.staffTypeId = :staffTypeId AND ust.isActive = true")
    boolean existsActiveForRole(@Param("staffTypeId") Long staffTypeId);
}
