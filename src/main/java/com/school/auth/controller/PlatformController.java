package com.school.auth.controller;

import com.school.auth.dto.AppDto.*;
import com.school.auth.dto.BacklogDto.FeatureRequestResponse;
import com.school.auth.dto.BacklogDto.TriageRequest;
import com.school.auth.dto.MemberDto.CreateInvitationRequest;
import com.school.auth.dto.MemberDto.InvitationResponse;
import com.school.auth.dto.PlatformDto.*;
import com.school.auth.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super-user console API (SUPER_USER only, enforced in AuthorizationServerConfig). Platform configuration
 * only: no endpoint here reads or writes data inside a branch.
 */
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;
    private final ProvisioningService provisioningService;
    private final MembershipService membershipService;
    private final RoleService roleService;
    private final DedicatedAppService appService;
    private final FeatureRequestService featureRequestService;

    // ---- catalog

    @GetMapping("/institution-types")
    public List<InstitutionTypeResponse> institutionTypes() {
        return platformService.institutionTypes();
    }

    @GetMapping("/features")
    public List<FeatureResponse> features() {
        return platformService.features();
    }

    @PostMapping("/features")
    public ResponseEntity<FeatureResponse> createFeature(@Valid @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformService.createFeature(request));
    }

    @PatchMapping("/features/{featureId}")
    public FeatureResponse updateFeature(@PathVariable Long featureId, @Valid @RequestBody UpdateFeatureRequest request) {
        return platformService.updateFeature(featureId, request);
    }

    /** Roles master: built-in roles with their default permissions and features. */
    @GetMapping("/roles")
    public List<RoleResponse> builtInRoles() {
        return roleService.builtInRoles();
    }

    // ---- organizations

    @GetMapping("/organizations")
    public Page<OrganizationResponse> organizations(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String q,
                                                    @PageableDefault(size = 50, sort = "orgName") Pageable pageable) {
        return platformService.organizations(status == null ? null : status.toUpperCase(), q, pageable);
    }

    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(JwtAuthenticationToken auth,
                                                                   @Valid @RequestBody CreateOrganizationRequest request) {
        Long id = platformService.createOrganization(CurrentUser.from(auth).userId(), request).getOrganizationId();
        return ResponseEntity.status(HttpStatus.CREATED).body(platformService.organization(id));
    }

    @GetMapping("/organizations/{organizationId}")
    public OrganizationResponse organization(@PathVariable Long organizationId) {
        return platformService.organization(organizationId);
    }

    @PatchMapping("/organizations/{organizationId}")
    public OrganizationResponse updateOrganization(@PathVariable Long organizationId,
                                                   @Valid @RequestBody UpdateOrganizationRequest request) {
        return platformService.updateOrganization(organizationId, request);
    }

    @PatchMapping("/organizations/{organizationId}/status")
    public Map<String, Object> organizationStatus(JwtAuthenticationToken auth, @PathVariable Long organizationId,
                                                  @Valid @RequestBody StatusRequest request) {
        int ended = platformService.setOrganizationStatus(CurrentUser.from(auth).userId(), organizationId,
                request.status(), request.reason());
        return Map.of("organization", platformService.organization(organizationId), "sessionsEnded", ended);
    }

    /** Entitlement ceiling. Removing a feature switches it off in every branch and suspends dedicated apps. */
    @PutMapping("/organizations/{organizationId}/features")
    public FeatureCodesResponse setEntitlements(@PathVariable Long organizationId, @Valid @RequestBody FeatureCodesRequest request) {
        return new FeatureCodesResponse(organizationId, platformService.setEntitlements(organizationId, request.featureCodes()));
    }

    @GetMapping("/organizations/{organizationId}/branches")
    public List<BranchResponse> branches(@PathVariable Long organizationId) {
        return platformService.branches(organizationId);
    }

    @PostMapping("/organizations/{organizationId}/branches")
    public ResponseEntity<BranchResponse> createBranch(@PathVariable Long organizationId,
                                                       @Valid @RequestBody CreateBranchRequest request) {
        Long id = platformService.createBranch(organizationId, request).getSchoolId();
        return ResponseEntity.status(HttpStatus.CREATED).body(platformService.branch(id));
    }

    /** Invite an organization's first org-wide admins (ADMIN, PRINCIPAL, SCHOOL_ADMIN). */
    @PostMapping("/organizations/{organizationId}/invitations")
    public InvitationResponse inviteOrgAdmin(JwtAuthenticationToken auth, @PathVariable Long organizationId,
                                             @Valid @RequestBody CreateInvitationRequest request) {
        return membershipService.inviteToOrganization(CurrentUser.from(auth).userId(), true, organizationId,
                request.email(), request.targetRoleCode());
    }

    // ---- branches

    @GetMapping("/schools/{schoolId}")
    public BranchResponse branch(@PathVariable Long schoolId) {
        return platformService.branch(schoolId);
    }

    @PatchMapping("/schools/{schoolId}")
    public BranchResponse updateBranch(@PathVariable Long schoolId, @Valid @RequestBody UpdateBranchRequest request) {
        return platformService.updateBranch(schoolId, request);
    }

    @PatchMapping("/schools/{schoolId}/status")
    public Map<String, Object> branchStatus(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                            @Valid @RequestBody StatusRequest request) {
        int ended = platformService.setBranchStatus(CurrentUser.from(auth).userId(), schoolId, request.status(), request.reason());
        return Map.of("branch", platformService.branch(schoolId), "sessionsEnded", ended);
    }

    @PutMapping("/schools/{schoolId}/features")
    public FeatureCodesResponse setBranchFeatures(@PathVariable Long schoolId, @Valid @RequestBody FeatureCodesRequest request) {
        return new FeatureCodesResponse(schoolId, platformService.setBranchFeatures(schoolId, request.featureCodes()));
    }

    /** Invite a branch's first admins (SCHOOL_ADMIN, PRINCIPAL, ADMIN). */
    @PostMapping("/schools/{schoolId}/invitations")
    public InvitationResponse inviteBranchAdmin(JwtAuthenticationToken auth, @PathVariable Long schoolId,
                                                @Valid @RequestBody CreateInvitationRequest request) {
        return membershipService.inviteToSchool(CurrentUser.from(auth).userId(), true, schoolId,
                request.email(), request.targetRoleCode());
    }

    // ---- onboarding wizard

    @PostMapping("/provisioning")
    public ResponseEntity<ProvisioningResponse> provision(JwtAuthenticationToken auth,
                                                          @Valid @RequestBody ProvisioningRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(provisioningService.provision(CurrentUser.from(auth).userId(), request));
    }

    @GetMapping("/provisioning/{batchId}")
    public List<ProvisioningRowResponse> provisioningBatch(@PathVariable UUID batchId) {
        return provisioningService.batch(batchId);
    }

    // ---- dedicated apps

    @GetMapping("/apps")
    public List<AppResponse> apps(@RequestParam(required = false) Long organizationId) {
        return appService.list(organizationId);
    }

    @PostMapping("/apps")
    public ResponseEntity<AppResponse> createApp(JwtAuthenticationToken auth, @Valid @RequestBody CreateAppRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appService.create(CurrentUser.from(auth).userId(), request));
    }

    @GetMapping("/apps/{appClientId}")
    public AppResponse app(@PathVariable Long appClientId) {
        return appService.get(appClientId);
    }

    @PatchMapping("/apps/{appClientId}")
    public AppResponse updateApp(@PathVariable Long appClientId, @Valid @RequestBody UpdateAppRequest request) {
        return appService.update(appClientId, request);
    }

    @PatchMapping("/apps/{appClientId}/status")
    public AppResponse appStatus(@PathVariable Long appClientId, @Valid @RequestBody AppStatusRequest request) {
        return appService.setStatus(appClientId, request);
    }

    @PutMapping("/apps/{appClientId}/schools")
    public AppResponse pinSchools(@PathVariable Long appClientId, @Valid @RequestBody PinSchoolsRequest request) {
        return appService.setPinnedSchools(appClientId, request.schoolIds());
    }

    // ---- backlog triage

    @PatchMapping("/feature-requests/{featureRequestId}")
    public FeatureRequestResponse triage(JwtAuthenticationToken auth, @PathVariable Long featureRequestId,
                                         @Valid @RequestBody TriageRequest request) {
        return featureRequestService.triage(CurrentUser.from(auth).userId(), featureRequestId, request);
    }
}
