package com.school.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteOnboardingRequest(
    @NotBlank(message = "Invitation token is required")
    String token,

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Verified phone number is required")
    String phoneNumber
) {}