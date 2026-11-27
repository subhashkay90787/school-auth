package com.school.auth.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public final class RoleDto {

    private RoleDto() {}

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,50}$") String typeCode,
            @NotBlank @Size(max = 100) String typeName,
            // Built-in role whose app module (screens) this role uses, e.g. TEACHER, WARDEN, STAFF
            @NotBlank String baseRoleCode,
            @Min(1) @Max(99) int level,
            String description,
            // NULL = start from the base role's effective permissions in this branch
            List<String> permissionCodes) {}

    public record UpdateRoleRequest(@Size(max = 100) String typeName, String description,
                                    @Min(1) @Max(99) Integer level, Boolean active) {}

    public record SetPermissionsRequest(
            @NotNull List<@NotBlank String> permissionCodes,
            // NULL = derived: the base role's default features plus the features of the granted permissions
            List<String> featureCodes) {}

    public record PermissionResponse(Long permissionId, String permissionCode, String permissionName,
                                     String description, String featureCode) {}
}
