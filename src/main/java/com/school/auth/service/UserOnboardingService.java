package com.school.auth.service;

import com.school.auth.entity.*;
import com.school.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserOnboardingService {

    private final SchoolInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final StaffTypeRepository staffTypeRepository;
    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;

    @Transactional
    public UserEntity completeOnboarding(String token, String firstName, String lastName, String verifiedPhone) {
        SchoolInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token."));

        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This invitation has expired or has already been used.");
        }

        // 1. Find or create master user record
        UserEntity user = userRepository.findByEmail(invitation.getEmail())
                .orElseGet(() -> userRepository.save(
                        UserEntity.builder()
                                .email(invitation.getEmail())
                                .phoneNumber(verifiedPhone)
                                .firstName(firstName)
                                .lastName(lastName)
                                .isActiveFlag(true)
                                .build()
                ));

        // 2. Fetch target staff role
        StaffTypeEntity staffType = staffTypeRepository.findByTypeCode(invitation.getTargetRoleCode())
                .orElseThrow(() -> new IllegalStateException("Role code not configured in master data: " + invitation.getTargetRoleCode()));

        // 3. Map user to school with role
        boolean alreadyMapped = userSchoolStaffTypeRepository.findActiveGrantsByUserId(user.getUserId()).stream()
                .anyMatch(g -> g.getSchool().getSchoolId().equals(invitation.getSchool().getSchoolId()) &&
                        g.getStaffType().getTypeCode().equals(invitation.getTargetRoleCode()));

        if (!alreadyMapped) {
            userSchoolStaffTypeRepository.save(
                    UserSchoolStaffTypeEntity.builder()
                            .user(user)
                            .school(invitation.getSchool())
                            .staffType(staffType)
                            .assignedDate(LocalDate.now())
                            .isActive(true)
                            .build()
            );
        }

        // 4. Mark invitation as used
        invitation.setUsed(true);
        invitationRepository.save(invitation);

        return user;
    }
}