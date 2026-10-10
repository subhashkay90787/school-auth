package com.school.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class UserCreationDto {

    public record UserSchoolAssignmentRequest(
            @NotNull(message = "School ID is required")
            Long schoolId
    ) {}

    public record CreateUserRequest(
            @NotBlank(message = "First name is required")
            String firstName,

            @NotBlank(message = "Last name is required")
            String lastName,

            @NotBlank(message = "Phone number is required")
            String phoneNumber,

            String alternatePhoneNumber,

            @Email(message = "Valid email is required")
            @NotBlank(message = "Email is required")
            String email,

            @NotEmpty(message = "At least one school assignment is required")
            List<UserSchoolAssignmentRequest> schoolAssignments
    ) {}

    public record UserCreatedResponse(
            Long userId,
            String email,
            String phoneNumber,
            String status
    ) {}
}