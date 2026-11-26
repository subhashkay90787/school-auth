package com.school.auth.service;

import com.school.auth.entity.StaffTypeEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Authoritative answers (from the database, not the token) to "what may this user do in this branch".
 * A user's roles in a branch are their branch grants plus their org-wide roles when the branch belongs
 * to that organization.
 */
@Service
@RequiredArgsConstructor
public class SchoolAccessService {

    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String PARENT_INVITE = "PARENT_INVITE";
    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String SESSION_MONITOR = "SESSION_MONITOR";
    public static final String FEATURE_REQUEST_RAISE = "FEATURE_REQUEST_RAISE";

    private static final Set<String> PARENT_INVITE_TARGETS = Set.of("PARENT", "STUDENT");

    static final String USER_ROLES_IN_SCHOOL = """
            SELECT g.staff_type_id FROM user_school_staff_type g
            WHERE g.user_id = :userId AND g.school_id = :schoolId AND g.is_active
            UNION
            SELECT m.staff_type_id FROM organization_member m
            JOIN school s ON s.organization_id = m.organization_id
            WHERE m.user_id = :userId AND s.school_id = :schoolId AND m.is_active
            """;

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public Set<String> permissions(Long userId, Long schoolId) {
        return new TreeSet<>(jdbc.sql("SELECT DISTINCT e.permission_code FROM effective_role_permission e " +
                        "WHERE e.school_id = :schoolId AND e.staff_type_id IN (" + USER_ROLES_IN_SCHOOL + ")")
                .param("userId", userId).param("schoolId", schoolId)
                .query(String.class).list());
    }

    @Transactional(readOnly = true)
    public List<String> roleCodes(Long userId, Long schoolId) {
        return jdbc.sql("SELECT st.type_code FROM staff_type st WHERE st.staff_type_id IN (" + USER_ROLES_IN_SCHOOL + ") ORDER BY st.level DESC")
                .param("userId", userId).param("schoolId", schoolId)
                .query(String.class).list();
    }

    /** Highest role level the user holds in the branch; 0 when they hold none. */
    @Transactional(readOnly = true)
    public int maxLevel(Long userId, Long schoolId) {
        Integer level = jdbc.sql("SELECT max(st.level) FROM staff_type st WHERE st.staff_type_id IN (" + USER_ROLES_IN_SCHOOL + ")")
                .param("userId", userId).param("schoolId", schoolId)
                .query(Integer.class).optional().orElse(null);
        return level == null ? 0 : level;
    }

    public boolean hasPermission(Long userId, Long schoolId, String permissionCode) {
        return permissions(userId, schoolId).contains(permissionCode);
    }

    public void requirePermission(Long userId, Long schoolId, String permissionCode) {
        if (!hasPermission(userId, schoolId, permissionCode)) {
            throw new SecurityException("You need the " + permissionCode + " permission in this branch.");
        }
    }

    /**
     * Who may invite or remove a holder of {@code target} in the branch:
     * USER_MANAGE with a strictly higher role level, or PARENT_INVITE for parents and adult students.
     */
    public boolean canManage(Long actorUserId, Long schoolId, StaffTypeEntity target) {
        if (StaffTypeEntity.SUPER_USER.equals(target.getTypeCode())) {
            return false;
        }
        Set<String> perms = permissions(actorUserId, schoolId);
        if (perms.contains(USER_MANAGE) && maxLevel(actorUserId, schoolId) > target.getLevel()) {
            return true;
        }
        return perms.contains(PARENT_INVITE) && PARENT_INVITE_TARGETS.contains(target.getBaseRoleCode());
    }
}
