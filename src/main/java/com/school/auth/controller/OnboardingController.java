package com.school.auth.controller;

import com.school.auth.dto.CompleteOnboardingRequest;
import com.school.auth.entity.SchoolInvitation;
import com.school.auth.repository.SchoolInvitationRepository;
import com.school.auth.service.UserOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final SchoolInvitationRepository invitationRepository;
    private final UserOnboardingService onboardingService;

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        SchoolInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token."));

        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("This invitation has expired or already been used.");
        }

        return ResponseEntity.ok(new InvitationDetailsResponse(
                invitation.getEmail(),
                invitation.getTargetRoleCode(),
                invitation.getSchool().getSchoolName()
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeOnboarding(@Valid @RequestBody CompleteOnboardingRequest request) {
        var user = onboardingService.completeOnboarding(
                request.token(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber()
        );
        return ResponseEntity.ok(user);
    }
}

record InvitationDetailsResponse(String email, String targetRoleCode, String schoolName) {}