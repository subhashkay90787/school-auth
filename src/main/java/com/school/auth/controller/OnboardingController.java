package com.school.auth.controller;

import com.school.auth.dto.CompleteOnboardingRequest;
import com.school.auth.dto.MemberDto.InvitationDetailsResponse;
import com.school.auth.dto.UserCreationDto;
import com.school.auth.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public: accepting an invitation (branch or org-wide). */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final MembershipService membershipService;

    @GetMapping("/validate")
    public InvitationDetailsResponse validateToken(@RequestParam String token) {
        return membershipService.validate(token);
    }

    @PostMapping("/complete")
    public ResponseEntity<UserCreationDto.UserCreatedResponse> completeOnboarding(@Valid @RequestBody CompleteOnboardingRequest request) {
        var user = membershipService.completeOnboarding(request.token(), request.firstName(), request.lastName(),
                request.phoneNumber(), request.otp());
        return ResponseEntity.ok(new UserCreationDto.UserCreatedResponse(user.getUserId(), user.getEmail(),
                user.getPhoneNumber(), user.isActiveFlag() ? "ACTIVE" : "INACTIVE"));
    }
}
