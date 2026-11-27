package com.school.auth.service;

import com.school.auth.dto.TokenPayloadDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * Builds the {@code school_access} claim: one entry per branch the user can use right now, with their roles
 * (built-in and custom), the app modules those roles use (base roles), their effective permissions and the
 * features that are both switched on in the branch and mapped to one of their roles.
 *
 * <p>A branch is included when the organization is not INACTIVE and the branch is ACTIVE, or ONBOARDING for
 * users holding a level-80+ role there (so admins can set it up before go-live). For a dedicated-app client
 * the list is narrowed to the branches pinned to that app ({@code app_client_effective_school}).
 */
@Service
@RequiredArgsConstructor
public class TokenClaimsService {

    private static final int SETUP_LEVEL = 80;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    private record Grant(long schoolId, long staffTypeId, String typeCode, String baseCode, int level, boolean orgWide) {}

    private record Branch(long schoolId, String schoolCode, String schoolName, String branchCode, String branchName,
                          long organizationId, String orgCode, String orgName, String institutionType,
                          String terminology, String timezone, String hostUrl, String city, String state,
                          String country, String status, String brandColor, String logoUrl, long permVersion,
                          String deploymentModel) {}

    /** Whether the client is a dedicated app (it then gets only its pinned branches). */
    @Transactional(readOnly = true)
    public Optional<String> dedicatedAppStatus(String clientId) {
        return jdbc.sql("SELECT status FROM app_client WHERE client_id = :clientId")
                .param("clientId", clientId).query(String.class).optional();
    }

    @Transactional(readOnly = true)
    public List<TokenPayloadDto.SchoolAccess> schoolAccess(Long userId, String clientId) {
        List<Grant> grants = jdbc.sql("""
                        SELECT g.school_id, st.staff_type_id, st.type_code, coalesce(b.type_code, st.type_code) AS base_code,
                               st.level, FALSE AS org_wide
                        FROM user_school_staff_type g
                        JOIN staff_type st ON st.staff_type_id = g.staff_type_id AND st.is_active
                        LEFT JOIN staff_type b ON b.staff_type_id = st.base_staff_type_id
                        WHERE g.user_id = :userId AND g.is_active
                        UNION
                        SELECT s.school_id, st.staff_type_id, st.type_code, coalesce(b.type_code, st.type_code), st.level, TRUE
                        FROM organization_member m
                        JOIN school s ON s.organization_id = m.organization_id
                        JOIN staff_type st ON st.staff_type_id = m.staff_type_id AND st.is_active
                        LEFT JOIN staff_type b ON b.staff_type_id = st.base_staff_type_id
                        WHERE m.user_id = :userId AND m.is_active
                        """)
                .param("userId", userId)
                .query((rs, i) -> new Grant(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getBoolean(6)))
                .list();
        if (grants.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> pinned = null;
        if (clientId != null && dedicatedAppStatus(clientId).isPresent()) {
            pinned = new HashSet<>(jdbc.sql("SELECT school_id FROM app_client_effective_school WHERE client_id = :clientId")
                    .param("clientId", clientId).query(Long.class).list());
        }

        Map<Long, List<Grant>> bySchool = new LinkedHashMap<>();
        grants.forEach(g -> bySchool.computeIfAbsent(g.schoolId(), k -> new ArrayList<>()).add(g));

        Map<Long, Branch> branches = new HashMap<>();
        jdbc.sql("""
                        SELECT s.school_id, s.school_code, s.school_name, s.branch_code, s.branch_name, o.organization_id,
                               o.org_code, o.org_name, it.type_code, it.terminology::text, s.timezone, s.host_url, s.city,
                               s.state, s.country, s.status, s.brand_color, s.logo_url, s.perm_version, o.deployment_model
                        FROM school s
                        JOIN organization o ON o.organization_id = s.organization_id
                        JOIN institution_type it ON it.institution_type_id = s.institution_type_id
                        WHERE s.school_id IN (:ids) AND o.status <> 'INACTIVE' AND s.status <> 'INACTIVE'
                        """)
                .param("ids", bySchool.keySet())
                .query((rs, i) -> new Branch(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
                        rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18), rs.getLong(19),
                        rs.getString(20)))
                .list()
                .forEach(b -> branches.put(b.schoolId(), b));

        List<TokenPayloadDto.SchoolAccess> result = new ArrayList<>();
        for (Map.Entry<Long, List<Grant>> entry : bySchool.entrySet()) {
            Branch branch = branches.get(entry.getKey());
            if (branch == null || (pinned != null && !pinned.contains(branch.schoolId()))) {
                continue;
            }
            List<Grant> schoolGrants = entry.getValue();
            int maxLevel = schoolGrants.stream().mapToInt(Grant::level).max().orElse(0);
            if (!"ACTIVE".equals(branch.status()) && maxLevel < SETUP_LEVEL) {
                continue; // ONBOARDING: only setup roles may sign in
            }
            List<Long> roleIds = schoolGrants.stream().map(Grant::staffTypeId).distinct().toList();

            List<String> permissions = new ArrayList<>(jdbc.sql("""
                            SELECT DISTINCT permission_code FROM effective_role_permission
                            WHERE school_id = :schoolId AND staff_type_id IN (:roles) ORDER BY 1
                            """)
                    .param("schoolId", branch.schoolId()).param("roles", roleIds)
                    .query(String.class).list());

            List<TokenPayloadDto.EnabledFeature> features = new ArrayList<>(jdbc.sql("""
                            SELECT DISTINCT f.feature_id, f.feature_code, f.feature_name
                            FROM school_feature sf
                            JOIN feature f ON f.feature_id = sf.feature_id AND f.is_active
                            JOIN organization_feature ofe ON ofe.feature_id = sf.feature_id AND ofe.organization_id = :orgId AND ofe.is_enabled
                            JOIN effective_role_feature erf ON erf.school_id = sf.school_id AND erf.feature_id = sf.feature_id
                            WHERE sf.school_id = :schoolId AND sf.is_enabled AND erf.staff_type_id IN (:roles)
                            ORDER BY f.feature_code
                            """)
                    .param("schoolId", branch.schoolId()).param("orgId", branch.organizationId()).param("roles", roleIds)
                    .query((rs, i) -> new TokenPayloadDto.EnabledFeature(rs.getLong(1), rs.getString(2), rs.getString(3)))
                    .list());

            result.add(new TokenPayloadDto.SchoolAccess(
                    branch.schoolId(), branch.schoolCode(), branch.schoolName(), branch.branchCode(), branch.branchName(),
                    branch.organizationId(), branch.orgCode(), branch.orgName(), branch.institutionType(),
                    jsonMapper.readValue(branch.terminology(), MAP), branch.timezone(), branch.hostUrl(),
                    branch.city(), branch.state(), branch.country(), branch.status(), branch.deploymentModel(),
                    branch.brandColor(), branch.logoUrl(),
                    new ArrayList<>(schoolGrants.stream().map(Grant::typeCode).distinct().toList()),
                    new ArrayList<>(schoolGrants.stream().map(Grant::baseCode).distinct().sorted().toList()),
                    schoolGrants.stream().anyMatch(Grant::orgWide),
                    permissions, features, branch.permVersion()));
        }
        return result;
    }
}
