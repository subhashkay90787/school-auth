package com.school.auth.controller;

import com.school.auth.service.SchoolInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schools/{schoolId}/invitations")
@RequiredArgsConstructor
public class SchoolInvitationController {

    private final SchoolInvitationService invitationService;

    @PostMapping
    public ResponseEntity<InvitationResponse> createInvitation(
            @PathVariable Long schoolId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateInvitationRequest request) {

        CurrentUser inviter = CurrentUser.from(authentication);

        String inviteLink = invitationService.createInvitation(
                inviter.userId(),
                inviter.superUser(),
                schoolId,
                request.email(),
                request.targetRoleCode()
        );

        return ResponseEntity.ok(new InvitationResponse(
                request.email(),
                request.targetRoleCode(),
                inviteLink,
                "Invitation created and dispatched successfully."
        ));
    }
}

record CreateInvitationRequest(
    @Email(message = "A valid email is required")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Target role code is required")
    String targetRoleCode // e.g., PRINCIPAL, ADMIN, TEACHER, STAFF, PARENT
) {}

record InvitationResponse(
    String email,
    String targetRoleCode,
    String inviteLink,
    String message
) {}