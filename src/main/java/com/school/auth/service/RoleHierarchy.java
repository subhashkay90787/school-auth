package com.school.auth.service;

import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Which school roles may manage (invite or remove) which other roles within the same school.
 * SYS_ADMIN, and any role not listed here, can only be managed by the super user.
 */
@Component
public class RoleHierarchy {

    public boolean canManage(Collection<String> actorRoles, String targetRoleCode) {
        return switch (targetRoleCode.toUpperCase()) {
            case "PRINCIPAL", "ADMIN" -> actorRoles.contains("SYS_ADMIN");
            case "TEACHER", "STAFF" -> actorRoles.contains("SYS_ADMIN") ||
                    actorRoles.contains("PRINCIPAL") ||
                    actorRoles.contains("ADMIN");
            case "PARENT" -> actorRoles.contains("SYS_ADMIN") ||
                    actorRoles.contains("PRINCIPAL") ||
                    actorRoles.contains("ADMIN") ||
                    actorRoles.contains("TEACHER");
            default -> false;
        };
    }
}
