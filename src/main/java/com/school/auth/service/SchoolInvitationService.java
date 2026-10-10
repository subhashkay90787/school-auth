package com.school.auth.service;

import com.school.auth.entity.SchoolEntity;
import com.school.auth.entity.SchoolInvitation;
import com.school.auth.entity.StaffTypeEntity;
import com.school.auth.repository.SchoolInvitationRepository;
import com.school.auth.repository.SchoolRepository;
import com.school.auth.repository.StaffTypeRepository;
import com.school.auth.repository.UserSchoolStaffTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolInvitationService {

    private final SchoolInvitationRepository invitationRepository;
    private final SchoolRepository schoolRepository;
    private final StaffTypeRepository staffTypeRepository;
    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;
    private final RoleHierarchy roleHierarchy;

    @Value("${app.frontend.base-url:http://localhost:8080}")
    private String frontendBaseUrl;

    @Transactional
    public String createInvitation(Long inviterUserId, boolean isSuperUser, Long schoolId, String inviteeEmail, String targetRoleCode) {
        String roleCode = targetRoleCode.toUpperCase();
        staffTypeRepository.findByTypeCode(roleCode)
                .filter(StaffTypeEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role code: " + roleCode));

        // 1. Enforce Role Hierarchy Permission Checks using the active grants query
        if (!isSuperUser) {
            List<String> inviterRoles = userSchoolStaffTypeRepository.findActiveGrants(inviterUserId, schoolId).stream()
                    .map(g -> g.getStaffType().getTypeCode())
                    .toList();

            if (!roleHierarchy.canManage(inviterRoles, roleCode)) {
                throw new SecurityException("You do not have permission to invite a " + targetRoleCode + " to this school.");
            }
        }

        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));
        if (!school.isActive() && !isSuperUser) {
            throw new IllegalStateException("This school is disabled.");
        }

        // 2. Generate secure token
        String token = UUID.randomUUID().toString();

        SchoolInvitation invitation = SchoolInvitation.builder()
                .school(school)
                .email(inviteeEmail.trim().toLowerCase())
                .targetRoleCode(roleCode)
                .invitedByUserId(inviterUserId)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isUsed(false)
                .build();

        invitationRepository.save(invitation);

        // 3. Dispatch Invitation Email using externalized base URL
        String inviteLink = frontendBaseUrl + "/onboard?token=" + token;

        return inviteLink;
    }
}