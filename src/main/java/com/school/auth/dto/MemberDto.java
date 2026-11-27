package com.school.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public final class MemberDto {

    private MemberDto() {}

    public record CreateInvitationRequest(
            @Email(message = "A valid email is required") @NotBlank(message = "Email is required") String email,
            // Built-in code (PRINCIPAL, TEACHER, PARENT ...) or the branch's custom role code
            @NotBlank(message = "Target role code is required") String targetRoleCode) {}

    public record InvitationResponse(String email, String targetRoleCode, String target, String inviteLink, String message) {}

    public record InvitationDetailsResponse(String email, String targetRoleCode, String targetName, boolean orgWide) {}

    public record MemberRole(String roleCode, String roleName, boolean orgWide, LocalDate assignedDate) {}

    public record MemberResponse(Long userId, String firstName, String lastName, String email, String phoneNumber,
                                 String userCategory, boolean active, List<MemberRole> roles) {}

    public record RemoveUserResponse(Long userId, Long schoolId, List<String> removedRoles) {}

    public record OrgMemberResponse(Long userId, String firstName, String lastName, String email,
                                    String roleCode, LocalDate assignedDate) {}
}
