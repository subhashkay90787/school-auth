package com.school.auth.controller;

import com.school.auth.dto.MemberDto.*;
import com.school.auth.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Org-wide members ("All branches" administrators, principals, accountants). */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
@RequiredArgsConstructor
public class OrganizationController {

    private final MembershipService membershipService;

    @GetMapping("/members")
    public List<OrgMemberResponse> members(JwtAuthenticationToken auth, @PathVariable Long organizationId) {
        CurrentUser user = CurrentUser.from(auth);
        return membershipService.orgMembers(user.userId(), user.superUser(), organizationId);
    }

    @PostMapping("/invitations")
    public InvitationResponse invite(JwtAuthenticationToken auth, @PathVariable Long organizationId,
                                     @Valid @RequestBody CreateInvitationRequest request) {
        CurrentUser user = CurrentUser.from(auth);
        return membershipService.inviteToOrganization(user.userId(), user.superUser(), organizationId,
                request.email(), request.targetRoleCode());
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> remove(JwtAuthenticationToken auth, @PathVariable Long organizationId,
                                       @PathVariable Long userId, @RequestParam String roleCode) {
        CurrentUser user = CurrentUser.from(auth);
        membershipService.removeOrgMember(user.userId(), user.superUser(), organizationId, userId, roleCode);
        return ResponseEntity.noContent().build();
    }
}
