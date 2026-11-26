package com.school.auth.dto;

import com.school.auth.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Super-user API payloads: organizations, branches, entitlements, provisioning, catalog. */
public final class PlatformDto {

    private PlatformDto() {}

    public static final String CODE = "^[A-Z0-9_]{2,50}$";

    public record InstitutionTypeResponse(Long institutionTypeId, String typeCode, String typeName, String academicModel,
                                          Map<String, Object> terminology, List<String> defaultFeatureCodes) {}

    public record FeatureResponse(Long featureId, String featureCode, String featureName, String description, boolean active) {
        public static FeatureResponse from(FeatureEntity f) {
            return new FeatureResponse(f.getFeatureId(), f.getFeatureCode(), f.getFeatureName(), f.getDescription(), f.isActive());
        }
    }

    public record CreateFeatureRequest(
            @NotBlank @Pattern(regexp = CODE) String featureCode,
            @NotBlank @Size(max = 100) String featureName,
            String description) {}

    public record UpdateFeatureRequest(@Size(max = 100) String featureName, String description, Boolean active) {}

    public record CreateOrganizationRequest(
            @NotBlank @Pattern(regexp = CODE) String orgCode,
            @NotBlank @Size(max = 150) String orgName,
            @Size(max = 200) String legalName,
            @NotBlank String institutionTypeCode,
            @Pattern(regexp = "HOSTED|SELF_HOSTED") String deploymentModel,
            @Positive Integer branchLimit,
            @Email String primaryContactEmail,
            String primaryContactPhone,
            // NULL = the institution type's wizard defaults
            List<String> featureCodes) {}

    public record UpdateOrganizationRequest(
            @Size(max = 150) String orgName,
            @Size(max = 200) String legalName,
            @Pattern(regexp = "HOSTED|SELF_HOSTED") String deploymentModel,
            @Positive Integer branchLimit,
            Boolean clearBranchLimit,
            @Email String primaryContactEmail,
            String primaryContactPhone) {}

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "(?i)ONBOARDING|ACTIVE|INACTIVE", message = "Status must be ONBOARDING, ACTIVE or INACTIVE")
            String status,
            // Required when suspending (INACTIVE): PAYMENT_OVERDUE, ORG_REQUEST, OTHER ...; recorded in the session end reason
            String reason) {}

    public record OrganizationResponse(Long organizationId, String orgCode, String orgName, String legalName,
                                       String institutionTypeCode, String deploymentModel, Integer branchLimit,
                                       String primaryContactEmail, String primaryContactPhone, String status,
                                       OffsetDateTime createdAt, List<String> entitledFeatureCodes,
                                       List<BranchResponse> branches) {}

    public record CreateBranchRequest(
            @NotBlank @Pattern(regexp = CODE) String branchCode,
            @NotBlank @Size(max = 150) String branchName,
            // Globally unique; defaults to ORGCODE_BRANCHCODE
            @Pattern(regexp = CODE) String schoolCode,
            @Size(max = 150) String schoolName,
            @NotBlank String hostUrl,
            @NotBlank String city,
            @NotBlank String state,
            @NotBlank String country,
            String institutionTypeCode,
            Long parentSchoolId,
            Boolean mainBranch,
            String timezone,
            String board,
            String udiseCode,
            String affiliationNumber,
            String addressLine,
            String pincode,
            String brandColor,
            String logoUrl,
            // Copy features from this branch of the same organization; else the type defaults within the entitlement
            Long templateSchoolId,
            // Explicit list overrides both (must be within the entitlement)
            List<String> featureCodes) {}

    public record UpdateBranchRequest(
            @Size(max = 150) String branchName,
            @Size(max = 150) String schoolName,
            String hostUrl, String city, String state, String country, String timezone, String board,
            String udiseCode, String affiliationNumber, String addressLine, String pincode,
            String brandColor, String logoUrl, Long parentSchoolId) {}

    public record BranchResponse(Long schoolId, Long organizationId, String schoolCode, String schoolName,
                                 String branchCode, String branchName, boolean mainBranch, Long parentSchoolId,
                                 String institutionTypeCode, String hostUrl, String city, String state, String country,
                                 String status, String timezone, String board, String udiseCode, String brandColor,
                                 String logoUrl, Long permVersion, List<String> enabledFeatureCodes) {
        public static BranchResponse from(SchoolEntity s, List<String> featureCodes) {
            return new BranchResponse(s.getSchoolId(), s.getOrganization().getOrganizationId(), s.getSchoolCode(),
                    s.getSchoolName(), s.getBranchCode(), s.getBranchName(), s.isMainBranch(), s.getParentSchoolId(),
                    s.getInstitutionType().getTypeCode(), s.getHostUrl(), s.getCity(), s.getState(), s.getCountry(),
                    s.getStatus(), s.getTimezone(), s.getBoard(), s.getUdiseCode(), s.getBrandColor(), s.getLogoUrl(),
                    s.getPermVersion(), featureCodes);
        }
    }

    public record FeatureCodesRequest(@NotNull List<@NotBlank String> featureCodes) {}

    public record FeatureCodesResponse(Long ownerId, List<String> featureCodes) {}

    /** One onboarding-wizard run: Organization -> Branches -> Features -> Admins -> go live. */
    public record ProvisioningRequest(
            // Either an existing organization, or a new one
            Long organizationId,
            @Valid CreateOrganizationRequest organization,
            @NotEmpty List<@Valid ProvisionBranch> branches,
            Boolean goLive) {
        public boolean goLiveRequested() {
            return Boolean.TRUE.equals(goLive);
        }
    }

    public record ProvisionBranch(
            @NotNull @Valid CreateBranchRequest branch,
            Boolean copyCustomRoles,
            Boolean copyRoleMappings,
            // Invite emails for the branch's first admins: role code -> emails (SCHOOL_ADMIN, PRINCIPAL, ADMIN)
            Map<String, List<@Email String>> adminInvites) {
        public boolean copyRoles() {
            return Boolean.TRUE.equals(copyCustomRoles);
        }

        public boolean copyMappings() {
            return Boolean.TRUE.equals(copyRoleMappings);
        }
    }

    public record ProvisioningRowResponse(Long provisioningId, UUID batchId, Long organizationId, Long schoolId,
                                          Long templateSchoolId, String status, String errorMessage,
                                          Map<String, Object> payload, OffsetDateTime createdAt, OffsetDateTime completedAt) {
        public static ProvisioningRowResponse from(BranchProvisioningEntity e) {
            return new ProvisioningRowResponse(e.getProvisioningId(), e.getBatchId(), e.getOrganizationId(), e.getSchoolId(),
                    e.getTemplateSchoolId(), e.getStatus(), e.getErrorMessage(), e.getPayload(), e.getCreatedAt(), e.getCompletedAt());
        }
    }

    public record ProvisioningResponse(UUID batchId, Long organizationId, List<ProvisioningRowResponse> branches,
                                       List<String> inviteLinks) {}

    public record RoleResponse(Long staffTypeId, Long schoolId, String typeCode, String typeName, int level,
                               String baseRoleCode, String description, boolean builtIn, boolean active,
                               boolean customized, List<String> permissionCodes, List<String> featureCodes) {}
}
