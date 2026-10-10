package com.school.auth.controller;

import com.school.auth.service.SchoolMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schools/{schoolId}/users")
@RequiredArgsConstructor
public class SchoolMembershipController {

    private final SchoolMembershipService membershipService;

    /**
     * Removes a user from a school: all of their roles there, or only roleCode when given.
     * Follows the same hierarchy as invitations.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<RemoveUserResponse> removeUserFromSchool(
            @PathVariable Long schoolId,
            @PathVariable Long userId,
            @RequestParam(required = false) String roleCode,
            JwtAuthenticationToken authentication) {

        CurrentUser actor = CurrentUser.from(authentication);
        List<String> removedRoles = membershipService.removeUserFromSchool(
                actor.userId(), actor.superUser(), schoolId, userId, roleCode);

        return ResponseEntity.ok(new RemoveUserResponse(userId, schoolId, removedRoles));
    }
}

record RemoveUserResponse(Long userId, Long schoolId, List<String> removedRoles) {}
