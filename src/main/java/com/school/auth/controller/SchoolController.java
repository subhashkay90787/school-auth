package com.school.auth.controller;

import com.school.auth.dto.MemberDto.*;
import com.school.auth.dto.PlatformDto.BranchResponse;
import com.school.auth.dto.PlatformDto.RoleResponse;
import com.school.auth.dto.RoleDto.*;
import com.school.auth.dto.SessionDto.LoginAttemptResponse;
import com.school.auth.dto.SessionDto.SessionResponse;
import com.school.auth.repository.LoginAttemptRepository;
import com.school.auth.repository.UserSessionRepository;
import com.school.auth.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Branch-level administration by the branch's own members (School Admin, Principal, Administrator ...).
 * Each operation checks the caller's permission in this branch against the database.
 */
@RestController
@RequestMapping("/api/v1/schools/{schoolId}")
@RequiredArgsConstructor
public class SchoolController {

    private final PlatformService platformService;
    private final MembershipService membershipService;
    private final RoleService roleService;
    private final SchoolAccessService access;
    private final SessionService sessionService;
    private final UserSessionRepository sessionRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    /** Branch profile, for any member of the branch. */
    @GetMapping
    public BranchResponse branch(JwtAuthenticationToken auth, @PathVariable Long schoolId) {
        CurrentUser user = CurrentUser.from(auth);
        if (!user.superUser() && access.roleCodes(user.userId(), schoolId).isEmpty()) {
            throw new SecurityException("You are not a member of this branch.");
        }
        return platformService.branch(schoolId);
    }

    /** My roles and effective permissions here (authoritative; the token may be up to one refresh old). */
    @GetMapping("/me")
    public MyAccess myAccess(JwtAuthenticationToken auth, @PathVariable Long schoolId) {
        CurrentUser user = CurrentUser.from(auth);
        return new MyAccess(schoolId, access.roleCodes(user.userId(), schoolId),
                List.copyOf(access.permissions(user.userId(), schoolId)));
    }

    record MyAccess(Long schoolId, List<String> roles, List<String> permissions) {}

    // ---- members

    @GetMapping("/members")
    public List<MemberResponse> members(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                        @RequestParam(required = false) String roleCode,
                                        @RequestParam(required = false) String q) {
        return membershipService.members(CurrentUser.from(auth).userId(), schoolId, roleCode, q);
    }

    @PostMapping("/invitations")
    public InvitationResponse invite(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                     @Valid @RequestBody CreateInvitationRequest request) {
        CurrentUser user = CurrentUser.from(auth);
        return membershipService.inviteToSchool(user.userId(), user.superUser(), schoolId, request.email(), request.targetRoleCode());
    }

    /** Removes a member's branch roles (all, or only roleCode). Same hierarchy as invitations. */
    @DeleteMapping("/members/{userId}")
    public RemoveUserResponse removeMember(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                           @PathVariable Long userId, @RequestParam(required = false) String roleCode) {
        CurrentUser actor = CurrentUser.from(auth);
        return membershipService.removeFromSchool(actor.userId(), actor.superUser(), schoolId, userId, roleCode);
    }

    // ---- roles & permissions

    @GetMapping("/roles")
    public List<RoleResponse> roles(JwtAuthenticationToken auth, @PathVariable Long schoolId) {
        return roleService.roles(CurrentUser.from(auth).userId(), schoolId);
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> createRole(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                                   @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleService.createCustomRole(CurrentUser.from(auth).userId(), schoolId, request));
    }

    @PatchMapping("/roles/{staffTypeId}")
    public RoleResponse updateRole(JwtAuthenticationToken auth, @PathVariable Long schoolId, @PathVariable Long staffTypeId,
                                   @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.updateRole(CurrentUser.from(auth).userId(), schoolId, staffTypeId, request);
    }

    @PutMapping("/roles/{staffTypeId}/permissions")
    public RoleResponse setPermissions(JwtAuthenticationToken auth, @PathVariable Long schoolId, @PathVariable Long staffTypeId,
                                       @Valid @RequestBody SetPermissionsRequest request) {
        return roleService.setPermissions(CurrentUser.from(auth).userId(), schoolId, staffTypeId, request);
    }

    /** Built-in role back to platform defaults. */
    @DeleteMapping("/roles/{staffTypeId}/permissions")
    public RoleResponse resetPermissions(JwtAuthenticationToken auth, @PathVariable Long schoolId, @PathVariable Long staffTypeId) {
        return roleService.resetToDefaults(CurrentUser.from(auth).userId(), schoolId, staffTypeId);
    }

    @DeleteMapping("/roles/{staffTypeId}")
    public ResponseEntity<Void> deleteRole(JwtAuthenticationToken auth, @PathVariable Long schoolId, @PathVariable Long staffTypeId) {
        roleService.deleteCustomRole(CurrentUser.from(auth).userId(), schoolId, staffTypeId);
        return ResponseEntity.noContent().build();
    }

    // ---- session monitoring (SESSION_MONITOR)

    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public Page<SessionResponse> sessions(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        access.requirePermission(CurrentUser.from(auth).userId(), schoolId, SchoolAccessService.SESSION_MONITOR);
        return sessionRepository.findOpenForSchool(schoolId, PageRequest.of(page, Math.min(size, 200)))
                .map(s -> SessionResponse.from(s, null));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Transactional
    public ResponseEntity<Void> endSession(JwtAuthenticationToken auth, @PathVariable Long schoolId, @PathVariable UUID sessionId) {
        CurrentUser actor = CurrentUser.from(auth);
        access.requirePermission(actor.userId(), schoolId, SchoolAccessService.SESSION_MONITOR);
        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Session not found: " + sessionId));
        if (!session.isOpen()) {
            return ResponseEntity.noContent().build();
        }
        // Only sessions of people below you in this branch
        int targetLevel = access.maxLevel(session.getUserId(), schoolId);
        if (targetLevel == 0 || targetLevel >= access.maxLevel(actor.userId(), schoolId)) {
            throw new SecurityException("You can only end sessions of members below your role in this branch.");
        }
        sessionService.end(session, "REVOKED_BY_ADMIN", actor.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login-attempts")
    @Transactional(readOnly = true)
    public Page<LoginAttemptResponse> failedSignIns(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                                    @RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "0") int page) {
        access.requirePermission(CurrentUser.from(auth).userId(), schoolId, SchoolAccessService.SESSION_MONITOR);
        return loginAttemptRepository.findFailures(schoolId, OffsetDateTime.now().minusDays(Math.min(days, 90)),
                PageRequest.of(page, 100)).map(LoginAttemptResponse::from);
    }
}
