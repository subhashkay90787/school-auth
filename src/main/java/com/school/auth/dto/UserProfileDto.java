package com.school.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UserProfileDto {

    public record UpdateProfileRequest(
            @Size(min = 1, max = 50, message = "First name must be 1-50 characters")
            @Pattern(regexp = ".*\\S.*", message = "First name cannot be blank")
            String firstName,

            @Size(min = 1, max = 50, message = "Last name must be 1-50 characters")
            @Pattern(regexp = ".*\\S.*", message = "Last name cannot be blank")
            String lastName,

            @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$", message = "Invalid phone number")
            String phoneNumber,

            // Required when phoneNumber changes; obtained via POST /api/v1/otp/send for the new number
            String phoneOtp,

            // Send "" to remove the alternate number
            @Pattern(regexp = "^$|^\\+?[0-9\\s\\-()]{7,20}$", message = "Invalid alternate phone number")
            String alternatePhoneNumber,

            // Required when alternatePhoneNumber is set to a new number
            String alternatePhoneOtp
    ) {}

    public record ProfileResponse(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("alternate_phone_number") String alternatePhoneNumber,
            @JsonProperty("super_user") boolean superUser,
            @JsonProperty("school_access") List<TokenPayloadDto.SchoolAccess> schoolAccess
    ) {}
}
