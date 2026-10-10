package com.school.auth.service;

import com.school.auth.entity.*;
import com.school.auth.otp.OtpService;
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
    private final OtpService otpService;

    @Transactional
    public UserEntity completeOnboarding(String token, String firstName, String lastName, String rawPhone, String otp) {
        SchoolInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token."));

        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This invitation has expired or has already been used.");
        }
        if (!invitation.getSchool().isActive()) {
            throw new IllegalStateException("This school is disabled.");
        }

        // 1. Prove ownership of the phone number, since it becomes an OTP login credential
        String verifiedPhone = OtpService.normalizePhone(rawPhone);
        if (!otpService.verifyOtp(verifiedPhone, otp)) {
            throw new IllegalArgumentException("Invalid or expired OTP for the phone number.");
        }
        userRepository.findByPhoneNumberOrAlternate(verifiedPhone)
                .filter(existing -> !existing.getEmail().equalsIgnoreCase(invitation.getEmail()))
                .ifPresent(existing -> {
                    throw new IllegalStateException("This phone number is already registered to another account.");
                });

        // 2. Find or create master user record
        UserEntity user = userRepository.findByEmailIgnoreCase(invitation.getEmail())
                .orElseGet(() -> userRepository.save(
                        UserEntity.builder()
                                .email(invitation.getEmail())
                                .phoneNumber(verifiedPhone)
                                .firstName(firstName)
                                .lastName(lastName)
                                .isActiveFlag(true)
                                .build()
                ));
        if (!user.isActiveFlag()) {
            throw new IllegalStateException("This account has been deactivated. Contact the platform administrator.");
        }

        // 3. Fetch target staff role
        StaffTypeEntity staffType = staffTypeRepository.findByTypeCode(invitation.getTargetRoleCode())
                .orElseThrow(() -> new IllegalStateException("Role code not configured in master data: " + invitation.getTargetRoleCode()));

        // 4. Map user to school with role (reactivating it if it was previously removed)
        userSchoolStaffTypeRepository.findGrant(user.getUserId(), invitation.getSchool().getSchoolId(), invitation.getTargetRoleCode())
                .ifPresentOrElse(existing -> {
                    if (!existing.isActive()) {
                        existing.setActive(true);
                        existing.setAssignedDate(LocalDate.now());
                    }
                }, () -> userSchoolStaffTypeRepository.save(
                        UserSchoolStaffTypeEntity.builder()
                                .user(user)
                                .school(invitation.getSchool())
                                .staffType(staffType)
                                .assignedDate(LocalDate.now())
                                .isActive(true)
                                .build()
                ));

        // 5. Mark invitation as used
        invitation.setUsed(true);
        invitationRepository.save(invitation);

        return user;
    }
}