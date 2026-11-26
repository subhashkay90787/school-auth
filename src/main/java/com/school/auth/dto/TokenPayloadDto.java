package com.school.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Shape of the custom access-token claims. The school-app-server reads exactly these names
 * (see school-app-server: com.school.app.core.security.SchoolAccessClaims).
 */
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
            @JsonProperty("branch_code") String branchCode,
            @JsonProperty("branch_name") String branchName,
            @JsonProperty("organization_id") Long organizationId,
            @JsonProperty("org_code") String orgCode,
            @JsonProperty("org_name") String orgName,
            @JsonProperty("institution_type") String institutionType,
            Map<String, Object> terminology,
            String timezone,
            @JsonProperty("host_url") String hostUrl,
            String city,
            String state,
            String country,
            String status,
            @JsonProperty("deployment_model") String deploymentModel,
            @JsonProperty("brand_color") String brandColor,
            @JsonProperty("logo_url") String logoUrl,
            // role codes held in the branch (built-in and custom)
            List<String> roles,
            // built-in roles whose app modules those roles use (custom role -> its base)
            @JsonProperty("base_roles") List<String> baseRoles,
            // true when at least one role comes from an org-wide membership
            @JsonProperty("org_wide") boolean orgWide,
            List<String> permissions,
            @JsonProperty("enabled_features") List<EnabledFeature> enabledFeatures,
            @JsonProperty("perm_version") Long permVersion
    ) {}
}
