package com.school.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Internal API for school-app-server: refresh of its thin directory cache (directory.school / directory.app_user)
 * and role-holder lookups for approval routing. Field names match the directory upsert functions.
 */
public final class DirectoryDto {

    private DirectoryDto() {}

    public record DirectorySchool(Long schoolId, Long organizationId, String schoolCode, String schoolName,
                                  String branchCode, String branchName, String institutionTypeCode,
                                  Map<String, Object> terminology, String timezone, String status) {}

    public record DirectoryUser(Long userId, String firstName, String lastName, String phoneNumber, String email,
                                String userCategory, boolean active) {}

    public record SchoolsPage(List<DirectorySchool> schools) {}

    /** Users of one branch; cursor-free for now (a branch has thousands, not millions, of users). */
    public record UsersPage(Long schoolId, OffsetDateTime generatedAt, List<DirectoryUser> users) {}

    public record RoleHolder(Long userId, String firstName, String lastName, String roleCode, boolean orgWide) {}
}
