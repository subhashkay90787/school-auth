package com.school.auth.repository;

import com.school.auth.entity.StaffTypeEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffTypeRepository extends JpaRepository<StaffTypeEntity, Long> {

    @Query("SELECT st FROM StaffTypeEntity st WHERE st.typeCode = :code AND st.schoolId IS NULL")
    Optional<StaffTypeEntity> findBuiltIn(@Param("code") String typeCode);

    /** The school's custom role with this code, else the built-in one. */
    @EntityGraph(attributePaths = "baseStaffType")
    @Query("SELECT st FROM StaffTypeEntity st WHERE st.typeCode = :code AND (st.schoolId = :schoolId OR st.schoolId IS NULL) " +
            "ORDER BY st.schoolId NULLS LAST")
    List<StaffTypeEntity> findVisible(@Param("schoolId") Long schoolId, @Param("code") String typeCode);

    @EntityGraph(attributePaths = "baseStaffType")
    @Query("SELECT st FROM StaffTypeEntity st WHERE (st.schoolId = :schoolId OR st.schoolId IS NULL) " +
            "AND st.typeCode <> 'SUPER_USER' ORDER BY st.schoolId NULLS FIRST, st.level DESC, st.typeName")
    List<StaffTypeEntity> findAllVisible(@Param("schoolId") Long schoolId);

    @Query("SELECT st FROM StaffTypeEntity st WHERE st.schoolId IS NULL ORDER BY st.level DESC, st.typeName")
    List<StaffTypeEntity> findAllBuiltIn();

    List<StaffTypeEntity> findBySchoolId(Long schoolId);

    default Optional<StaffTypeEntity> findForSchool(Long schoolId, String typeCode) {
        return findVisible(schoolId, typeCode.toUpperCase()).stream().findFirst();
    }
}
