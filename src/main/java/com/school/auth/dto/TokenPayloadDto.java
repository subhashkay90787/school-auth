package com.school.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TokenPayloadDto {

    public record UserDetails(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("alternate_phone_number") String alternatePhoneNumber,
            @JsonProperty("user_category") String userCategory,
            @JsonProperty("is_active_flag") boolean isActiveFlag
    ) {}

    public record EnabledFeature(
            @JsonProperty("feature_id") Long featureId,
            @JsonProperty("feature_code") String featureCode,
            @JsonProperty("feature_name") String featureName
    ) {}

    public record SchoolAccess(
            @JsonProperty("school_id") Long schoolId,
            @JsonProperty("school_code") String schoolCode,
            @JsonProperty("school_name") String schoolName,
            @JsonProperty("host_url") String hostUrl,
            String city,
            String state,
            String country,
            String status,
            List<String> roles,
            @JsonProperty("enabled_features") List<EnabledFeature> enabledFeatures
    ) {}
}