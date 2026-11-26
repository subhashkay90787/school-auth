package com.school.auth.dto;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AppDto {

    private AppDto() {}

    public record CreateAppRequest(
            @NotNull Long organizationId,
            // e.g. app.stmarys; baked into the build
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{2,99}$") String clientId,
            @NotBlank @Size(max = 100) String appName,
            String androidPackage,
            String iosBundleId,
            String iosTeamId,
            @NotBlank String redirectUri,
            String deepLinkDomain,
            String apiBaseUrl,
            Map<String, Object> branding,
            String pushCredentialsRef,
            @Pattern(regexp = "ORGANIZATION|PLATFORM") String storeAccountOwner,
            String minSupportedVersion,
            // Pinned branches, in switcher order; must belong to the organization
            List<Long> schoolIds) {}

    public record UpdateAppRequest(String appName, String apiBaseUrl, Map<String, Object> branding,
                                   String pushCredentialsRef, String minSupportedVersion, String deepLinkDomain) {}

    public record AppStatusRequest(
            @NotBlank @Pattern(regexp = "DRAFT|ACTIVE|SUSPENDED|RETIRED") String status,
            @Pattern(regexp = "PAYMENT_OVERDUE|ENTITLEMENT_REVOKED|STORE_REMOVED|ORG_REQUEST|OTHER") String reason) {}

    public record PinSchoolsRequest(@NotNull List<@NotNull Long> schoolIds) {}

    public record AppResponse(Long appClientId, String clientId, Long organizationId, String appName,
                              String androidPackage, String iosBundleId, String iosTeamId, String redirectUri,
                              String deepLinkDomain, String apiBaseUrl, Map<String, Object> branding,
                              String storeAccountOwner, String minSupportedVersion, String status,
                              String statusReason, OffsetDateTime statusChangedAt, List<Long> pinnedSchoolIds) {}

    /** Public config read by the app at start-up (no secrets). */
    public record AppConfigResponse(String clientId, String appName, String status, String apiBaseUrl,
                                    Map<String, Object> branding, String minSupportedVersion,
                                    List<PinnedSchool> schools) {}

    public record PinnedSchool(Long schoolId, String schoolName, String branchName, String hostUrl, int sortOrder) {}
}
