package com.school.auth.service;

import com.school.auth.dto.DirectoryDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DirectoryService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public SchoolsPage schools(Collection<Long> schoolIds) {
        return new SchoolsPage(jdbc.sql("""
                        SELECT s.school_id, s.organization_id, s.school_code, s.school_name, s.branch_code, s.branch_name,
                               it.type_code, it.terminology::text, s.timezone,
                               CASE WHEN o.status = 'INACTIVE' THEN 'INACTIVE' ELSE s.status END
                        FROM school s
                        JOIN organization o ON o.organization_id = s.organization_id
                        JOIN institution_type it ON it.institution_type_id = s.institution_type_id
                        WHERE s.school_id IN (:ids) ORDER BY s.school_id
                        """)
                .param("ids", schoolIds)
                .query((rs, i) -> new DirectorySchool(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), jsonMapper.readValue(rs.getString(8), MAP),
                        rs.getString(9), rs.getString(10)))
                .list());
    }

    /** Everyone holding (or having held) a role in the branch, so deactivations reach the cache too. */
    @Transactional(readOnly = true)
    public UsersPage users(Long schoolId) {
        return new UsersPage(schoolId, OffsetDateTime.now(), jdbc.sql("""
                        SELECT DISTINCT u.user_id, u.first_name, u.last_name, u.phone_number, u.email, u.user_category,
                               u.is_active_flag AND EXISTS (
                                   SELECT 1 FROM user_school_staff_type g2 WHERE g2.user_id = u.user_id AND g2.school_id = :schoolId AND g2.is_active
                                   UNION ALL
                                   SELECT 1 FROM organization_member m2 JOIN school s2 ON s2.organization_id = m2.organization_id
                                   WHERE m2.user_id = u.user_id AND s2.school_id = :schoolId AND m2.is_active)
                        FROM app_user u
                        WHERE u.user_id IN (
                            SELECT g.user_id FROM user_school_staff_type g WHERE g.school_id = :schoolId
                            UNION
                            SELECT m.user_id FROM organization_member m JOIN school s ON s.organization_id = m.organization_id
                            WHERE s.school_id = :schoolId)
                        ORDER BY u.user_id
                        """)
                .param("schoolId", schoolId)
                .query((rs, i) -> new DirectoryUser(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getBoolean(7)))
                .list());
    }

    /** Who holds a role in a branch (custom roles count when their base or own code matches). */
    @Transactional(readOnly = true)
    public List<RoleHolder> roleHolders(Long schoolId, String roleCode) {
        return jdbc.sql("""
                        SELECT u.user_id, u.first_name, u.last_name, st.type_code, x.org_wide
                        FROM (
                            SELECT g.user_id, g.staff_type_id, FALSE AS org_wide FROM user_school_staff_type g
                            WHERE g.school_id = :schoolId AND g.is_active
                            UNION
                            SELECT m.user_id, m.staff_type_id, TRUE FROM organization_member m
                            JOIN school s ON s.organization_id = m.organization_id
                            WHERE s.school_id = :schoolId AND m.is_active
                        ) x
                        JOIN app_user u ON u.user_id = x.user_id AND u.is_active_flag
                        JOIN staff_type st ON st.staff_type_id = x.staff_type_id AND st.is_active
                        LEFT JOIN staff_type b ON b.staff_type_id = st.base_staff_type_id
                        WHERE st.type_code = :role OR b.type_code = :role
                        ORDER BY u.first_name, u.last_name
                        """)
                .param("schoolId", schoolId).param("role", roleCode.toUpperCase())
                .query((rs, i) -> new RoleHolder(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .list();
    }
}
