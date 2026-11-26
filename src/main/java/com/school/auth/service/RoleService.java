package com.school.auth.service;

import com.school.auth.dto.PlatformDto.RoleResponse;
import com.school.auth.dto.RoleDto.*;
import com.school.auth.entity.*;
import com.school.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Roles and their mappings inside a branch. Built-in roles use the platform defaults until the branch
 * customizes them (school_role_override + school rows); custom roles always use school rows.
 *
 * <p>Only members with ROLE_MANAGE (Administrator, Principal by default) may change roles, and only roles
 * below their own level. Every change bumps the branch's perm_version (DB triggers).
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> UNIVERSAL_FEATURES = Set.of("COMMUNICATION", "NEWS_FEED", "CALENDAR", "CONSENT", "ASSISTANT_MCP");

    private final StaffTypeRepository staffTypeRepository;
    private final PermissionRepository permissionRepository;
    private final StaffTypePermissionRepository stpRepository;
    private final StaffTypeFeatureRepository stfRepository;
    private final SchoolRoleOverrideRepository overrideRepository;
    private final FeatureRepository featureRepository;
    private final UserSchoolStaffTypeRepository grantRepository;
    private final SchoolAccessService access;
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissionCatalog() {
        return permissionRepository.findAllWithFeature().stream()
                .map(p -> new PermissionResponse(p.getPermissionId(), p.getPermissionCode(), p.getPermissionName(),
                        p.getDescription(), p.getFeature() == null ? null : p.getFeature().getFeatureCode()))
                .toList();
    }

    /** Built-in roles with their platform-default mappings (super-user "Roles master"). */
    @Transactional(readOnly = true)
    public List<RoleResponse> builtInRoles() {
        return staffTypeRepository.findAllBuiltIn().stream()
                .map(r -> toResponse(r, null, false,
                        jdbc.sql("SELECT p.permission_code FROM staff_type_permission m JOIN permission p USING (permission_id) " +
                                        "WHERE m.school_id IS NULL AND m.staff_type_id = :id ORDER BY 1")
                                .param("id", r.getStaffTypeId()).query(String.class).list(),
                        jdbc.sql("SELECT f.feature_code FROM staff_type_feature m JOIN feature f USING (feature_id) " +
                                        "WHERE m.school_id IS NULL AND m.staff_type_id = :id ORDER BY 1")
                                .param("id", r.getStaffTypeId()).query(String.class).list()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles(Long actorUserId, Long schoolId) {
        requireAny(actorUserId, schoolId, SchoolAccessService.ROLE_VIEW, SchoolAccessService.ROLE_MANAGE);
        Set<Long> overridden = new HashSet<>();
        overrideRepository.findBySchoolId(schoolId).forEach(o -> overridden.add(o.getStaffTypeId()));
        Map<Long, List<String>> perms = effective(schoolId, "permission");
        Map<Long, List<String>> features = effective(schoolId, "feature");
        return staffTypeRepository.findAllVisible(schoolId).stream()
                .map(r -> toResponse(r, schoolId, overridden.contains(r.getStaffTypeId()),
                        perms.getOrDefault(r.getStaffTypeId(), List.of()),
                        features.getOrDefault(r.getStaffTypeId(), List.of())))
                .toList();
    }

    @Transactional
    public RoleResponse createCustomRole(Long actorUserId, Long schoolId, CreateRoleRequest request) {
        access.requirePermission(actorUserId, schoolId, SchoolAccessService.ROLE_MANAGE);
        int actorLevel = access.maxLevel(actorUserId, schoolId);
        if (request.level() >= actorLevel) {
            throw new SecurityException("A custom role must rank below your own role (level < " + actorLevel + ").");
        }
        if (staffTypeRepository.findForSchool(schoolId, request.typeCode()).isPresent()) {
            throw new IllegalStateException("Role code " + request.typeCode() + " is already used in this branch.");
        }
        StaffTypeEntity base = staffTypeRepository.findBuiltIn(request.baseRoleCode().toUpperCase())
                .filter(b -> !StaffTypeEntity.SUPER_USER.equals(b.getTypeCode()))
                .orElseThrow(() -> new IllegalArgumentException("Base role must be a built-in branch role: " + request.baseRoleCode()));
        StaffTypeEntity role = staffTypeRepository.saveAndFlush(StaffTypeEntity.builder()
                .schoolId(schoolId)
                .typeCode(request.typeCode())
                .typeName(request.typeName())
                .level(request.level())
                .description(request.description())
                .createdByUserId(actorUserId)
                .baseStaffType(base.getBaseStaffType() != null ? base.getBaseStaffType() : base)
                .build());
        List<String> permissionCodes = request.permissionCodes() != null
                ? request.permissionCodes()
                : effective(schoolId, "permission").getOrDefault(base.getStaffTypeId(), List.of());
        writeMapping(schoolId, role, permissionCodes, null);
        return role(schoolId, role.getStaffTypeId());
    }

    @Transactional
    public RoleResponse updateRole(Long actorUserId, Long schoolId, Long staffTypeId, UpdateRoleRequest request) {
        StaffTypeEntity role = editableRole(actorUserId, schoolId, staffTypeId);
        if (role.isBuiltIn()) {
            throw new IllegalArgumentException("Built-in roles can't be renamed; change their permissions instead.");
        }
        if (request.typeName() != null) role.setTypeName(request.typeName());
        if (request.description() != null) role.setDescription(request.description());
        if (request.level() != null) {
            if (request.level() >= access.maxLevel(actorUserId, schoolId)) {
                throw new SecurityException("A custom role must rank below your own role.");
            }
            role.setLevel(request.level());
        }
        if (request.active() != null) role.setActive(request.active());
        staffTypeRepository.flush();
        return role(schoolId, staffTypeId);
    }

    /** Replaces a role's permissions in this branch. For a built-in role this creates the branch override. */
    @Transactional
    public RoleResponse setPermissions(Long actorUserId, Long schoolId, Long staffTypeId, SetPermissionsRequest request) {
        StaffTypeEntity role = editableRole(actorUserId, schoolId, staffTypeId);
        // Governance (managing users, roles, sessions, consent forms) can't be handed out by someone who lacks it
        Set<String> actorPerms = access.permissions(actorUserId, schoolId);
        List<String> beyond = request.permissionCodes().stream()
                .filter(c -> GOVERNANCE.contains(c) && !actorPerms.contains(c)).toList();
        if (!beyond.isEmpty()) {
            throw new SecurityException("You can't grant governance permissions you don't hold: " + beyond);
        }
        if (role.isBuiltIn() && overrideRepository.findBySchoolIdAndStaffTypeId(schoolId, staffTypeId).isEmpty()) {
            SchoolRoleOverrideEntity override = new SchoolRoleOverrideEntity();
            override.setSchoolId(schoolId);
            override.setStaffTypeId(staffTypeId);
            override.setCustomizedByUserId(actorUserId);
            overrideRepository.save(override);
        }
        writeMapping(schoolId, role, request.permissionCodes(), request.featureCodes());
        return role(schoolId, staffTypeId);
    }

    /** Back to platform defaults for a built-in role. */
    @Transactional
    public RoleResponse resetToDefaults(Long actorUserId, Long schoolId, Long staffTypeId) {
        StaffTypeEntity role = editableRole(actorUserId, schoolId, staffTypeId);
        if (!role.isBuiltIn()) {
            throw new IllegalArgumentException("Only built-in roles have defaults to return to.");
        }
        overrideRepository.findBySchoolIdAndStaffTypeId(schoolId, staffTypeId).ifPresent(overrideRepository::delete);
        stpRepository.deleteSchoolMapping(schoolId, staffTypeId);
        stfRepository.deleteSchoolMapping(schoolId, staffTypeId);
        return role(schoolId, staffTypeId);
    }

    @Transactional
    public void deleteCustomRole(Long actorUserId, Long schoolId, Long staffTypeId) {
        StaffTypeEntity role = editableRole(actorUserId, schoolId, staffTypeId);
        if (role.isBuiltIn()) {
            throw new IllegalArgumentException("Built-in roles can't be deleted.");
        }
        if (grantRepository.existsActiveForRole(staffTypeId)) {
            throw new IllegalStateException("Members still hold this role; deactivate it or move them first.");
        }
        staffTypeRepository.delete(role);
    }

    /**
     * Copies a template branch's custom roles and/or built-in overrides into a new branch of the same
     * organization (provisioning only; platform config, never school data).
     */
    @Transactional
    public void copyRoles(Long templateSchoolId, Long targetSchoolId, boolean customRoles, boolean roleMappings) {
        Map<Long, Long> roleIdMap = new HashMap<>();
        if (customRoles) {
            for (StaffTypeEntity src : staffTypeRepository.findBySchoolId(templateSchoolId)) {
                StaffTypeEntity copy = staffTypeRepository.save(StaffTypeEntity.builder()
                        .schoolId(targetSchoolId).typeCode(src.getTypeCode()).typeName(src.getTypeName())
                        .level(src.getLevel()).description(src.getDescription()).baseStaffType(src.getBaseStaffType())
                        .isActive(src.isActive()).build());
                roleIdMap.put(src.getStaffTypeId(), copy.getStaffTypeId());
            }
        }
        if (roleMappings) {
            for (SchoolRoleOverrideEntity o : overrideRepository.findBySchoolId(templateSchoolId)) {
                SchoolRoleOverrideEntity copy = new SchoolRoleOverrideEntity();
                copy.setSchoolId(targetSchoolId);
                copy.setStaffTypeId(o.getStaffTypeId());
                overrideRepository.save(copy);
                roleIdMap.putIfAbsent(o.getStaffTypeId(), o.getStaffTypeId());
            }
        }
        stpRepository.flush();
        for (StaffTypePermissionEntity m : stpRepository.findSchoolMappings(templateSchoolId)) {
            Long target = roleIdMap.get(m.getStaffTypeId());
            if (target != null) {
                stpRepository.save(new StaffTypePermissionEntity(targetSchoolId, target, m.getPermissionId()));
            }
        }
        for (StaffTypeFeatureEntity m : stfRepository.findSchoolMappings(templateSchoolId)) {
            Long target = roleIdMap.get(m.getStaffTypeId());
            if (target != null) {
                stfRepository.save(new StaffTypeFeatureEntity(targetSchoolId, target, m.getFeatureId()));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Governance permissions: only grantable by someone who holds them. Module permissions are limited by role level. */
    static final Set<String> GOVERNANCE = Set.of(SchoolAccessService.USER_MANAGE, SchoolAccessService.ROLE_MANAGE,
            SchoolAccessService.ROLE_VIEW, SchoolAccessService.SESSION_MONITOR, "CONSENT_FORM_MANAGE");

    private void writeMapping(Long schoolId, StaffTypeEntity role, List<String> permissionCodes, List<String> featureCodes) {
        List<PermissionEntity> permissions = permissionRepository.findByPermissionCodeIn(new HashSet<>(permissionCodes));
        if (permissions.size() != new HashSet<>(permissionCodes).size()) {
            Set<String> known = new HashSet<>();
            permissions.forEach(p -> known.add(p.getPermissionCode()));
            throw new IllegalArgumentException("Unknown permission codes: "
                    + permissionCodes.stream().filter(c -> !known.contains(c)).toList());
        }
        stpRepository.deleteSchoolMapping(schoolId, role.getStaffTypeId());
        stfRepository.deleteSchoolMapping(schoolId, role.getStaffTypeId());
        stpRepository.flush();
        permissions.forEach(p -> stpRepository.save(new StaffTypePermissionEntity(schoolId, role.getStaffTypeId(), p.getPermissionId())));

        Set<String> features = new TreeSet<>();
        if (featureCodes != null) {
            features.addAll(featureCodes);
        } else {
            // Derived: the base role's default features, plus each granted permission's feature
            StaffTypeEntity base = role.getBaseStaffType() != null ? role.getBaseStaffType() : role;
            stfRepository.findDefaults(base.getStaffTypeId())
                    .forEach(m -> featureRepository.findById(m.getFeatureId()).ifPresent(f -> features.add(f.getFeatureCode())));
            permissions.stream().filter(p -> p.getFeature() != null).forEach(p -> features.add(p.getFeature().getFeatureCode()));
            features.addAll(UNIVERSAL_FEATURES);
        }
        List<FeatureEntity> featureRows = featureRepository.findByFeatureCodeIn(features);
        if (featureRows.size() != features.size()) {
            throw new IllegalArgumentException("Unknown feature codes in " + features);
        }
        featureRows.forEach(f -> stfRepository.save(new StaffTypeFeatureEntity(schoolId, role.getStaffTypeId(), f.getFeatureId())));
        stfRepository.flush();
    }

    private StaffTypeEntity editableRole(Long actorUserId, Long schoolId, Long staffTypeId) {
        access.requirePermission(actorUserId, schoolId, SchoolAccessService.ROLE_MANAGE);
        StaffTypeEntity role = staffTypeRepository.findById(staffTypeId)
                .filter(r -> r.isBuiltIn() || schoolId.equals(r.getSchoolId()))
                .orElseThrow(() -> new NoSuchElementException("Role not found in this branch: " + staffTypeId));
        if (StaffTypeEntity.SUPER_USER.equals(role.getTypeCode())) {
            throw new SecurityException("SUPER_USER is not a branch role.");
        }
        if (role.getLevel() >= access.maxLevel(actorUserId, schoolId)) {
            throw new SecurityException("You can only change roles below your own (" + role.getTypeCode() + " is level " + role.getLevel() + ").");
        }
        return role;
    }

    private void requireAny(Long actorUserId, Long schoolId, String... codes) {
        Set<String> perms = access.permissions(actorUserId, schoolId);
        if (Arrays.stream(codes).noneMatch(perms::contains)) {
            throw new SecurityException("You need one of " + Arrays.toString(codes) + " in this branch.");
        }
    }

    private RoleResponse role(Long schoolId, Long staffTypeId) {
        StaffTypeEntity r = staffTypeRepository.findById(staffTypeId).orElseThrow();
        boolean overridden = overrideRepository.findBySchoolIdAndStaffTypeId(schoolId, staffTypeId).isPresent();
        return toResponse(r, schoolId, overridden,
                effective(schoolId, "permission").getOrDefault(staffTypeId, List.of()),
                effective(schoolId, "feature").getOrDefault(staffTypeId, List.of()));
    }

    private Map<Long, List<String>> effective(Long schoolId, String kind) {
        String sql = "permission".equals(kind)
                ? "SELECT staff_type_id, permission_code FROM effective_role_permission WHERE school_id = :id ORDER BY 2"
                : "SELECT staff_type_id, feature_code FROM effective_role_feature WHERE school_id = :id ORDER BY 2";
        Map<Long, List<String>> result = new HashMap<>();
        jdbc.sql(sql).param("id", schoolId)
                .query((rs, i) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    private static RoleResponse toResponse(StaffTypeEntity r, Long schoolId, boolean customized,
                                           List<String> permissions, List<String> features) {
        return new RoleResponse(r.getStaffTypeId(), r.getSchoolId(), r.getTypeCode(), r.getTypeName(), r.getLevel(),
                r.getBaseRoleCode(), r.getDescription(), r.isBuiltIn(), r.isActive(), customized, permissions, features);
    }
}
