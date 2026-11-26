package com.school.auth.service;

import com.school.auth.dto.PlatformDto.*;
import com.school.auth.entity.*;
import com.school.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

/**
 * Super-user operations. These touch platform configuration only (organizations, branches, entitlements,
 * feature switches) and never school data.
 */
@Service
@RequiredArgsConstructor
public class PlatformService {

    /** Always on: consent forms gate every other feature. */
    public static final String ALWAYS_ON_FEATURE = "CONSENT";

    private final OrganizationRepository organizationRepository;
    private final SchoolRepository schoolRepository;
    private final InstitutionTypeRepository institutionTypeRepository;
    private final FeatureRepository featureRepository;
    private final OrganizationFeatureRepository organizationFeatureRepository;
    private final SchoolFeatureRepository schoolFeatureRepository;
    private final SessionService sessionService;
    private final UserSessionRepository sessionRepository;
    private final JdbcClient jdbc;

    // ------------------------------------------------------------------ catalog

    @Transactional(readOnly = true)
    public List<InstitutionTypeResponse> institutionTypes() {
        return institutionTypeRepository.findAll().stream()
                .filter(InstitutionTypeEntity::isActive)
                .map(t -> new InstitutionTypeResponse(t.getInstitutionTypeId(), t.getTypeCode(), t.getTypeName(),
                        t.getAcademicModel(), t.getTerminology(), typeDefaultFeatureCodes(t.getInstitutionTypeId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureResponse> features() {
        return featureRepository.findAllByOrderByFeatureCode().stream().map(FeatureResponse::from).toList();
    }

    @Transactional
    public FeatureResponse createFeature(CreateFeatureRequest request) {
        if (featureRepository.findByFeatureCode(request.featureCode()).isPresent()) {
            throw new IllegalStateException("Feature " + request.featureCode() + " already exists.");
        }
        return FeatureResponse.from(featureRepository.save(FeatureEntity.builder()
                .featureCode(request.featureCode()).featureName(request.featureName())
                .description(request.description()).build()));
    }

    @Transactional
    public FeatureResponse updateFeature(Long featureId, UpdateFeatureRequest request) {
        FeatureEntity f = featureRepository.findById(featureId)
                .orElseThrow(() -> new NoSuchElementException("Feature not found: " + featureId));
        if (request.featureName() != null) f.setFeatureName(request.featureName());
        if (request.description() != null) f.setDescription(request.description());
        if (request.active() != null) f.setActive(request.active());
        return FeatureResponse.from(f);
    }

    // ------------------------------------------------------------------ organizations

    @Transactional
    public OrganizationEntity createOrganization(Long actorUserId, CreateOrganizationRequest request) {
        if (organizationRepository.existsByOrgCode(request.orgCode())) {
            throw new IllegalStateException("An organization with code " + request.orgCode() + " already exists.");
        }
        InstitutionTypeEntity type = institutionType(request.institutionTypeCode());
        OrganizationEntity org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(request.orgCode())
                .orgName(request.orgName())
                .legalName(request.legalName())
                .institutionType(type)
                .deploymentModel(request.deploymentModel() == null ? "HOSTED" : request.deploymentModel())
                .branchLimit(request.branchLimit())
                .primaryContactEmail(request.primaryContactEmail())
                .primaryContactPhone(request.primaryContactPhone())
                .createdByUserId(actorUserId)
                .build());
        List<String> codes = request.featureCodes() != null
                ? request.featureCodes()
                : typeDefaultFeatureCodes(type.getInstitutionTypeId());
        setEntitlements(org.getOrganizationId(), codes);
        return org;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> organizations(String status, String query, Pageable pageable) {
        return organizationRepository.search(status, StringUtils.hasText(query) ? query : null, pageable)
                .map(o -> toResponse(o, false));
    }

    @Transactional(readOnly = true)
    public OrganizationResponse organization(Long organizationId) {
        return toResponse(findOrganization(organizationId), true);
    }

    @Transactional
    public OrganizationResponse updateOrganization(Long organizationId, UpdateOrganizationRequest request) {
        OrganizationEntity org = findOrganization(organizationId);
        if (request.orgName() != null) org.setOrgName(request.orgName());
        if (request.legalName() != null) org.setLegalName(request.legalName());
        if (request.deploymentModel() != null) org.setDeploymentModel(request.deploymentModel());
        if (Boolean.TRUE.equals(request.clearBranchLimit())) org.setBranchLimit(null);
        else if (request.branchLimit() != null) org.setBranchLimit(request.branchLimit());
        if (request.primaryContactEmail() != null) org.setPrimaryContactEmail(request.primaryContactEmail());
        if (request.primaryContactPhone() != null) org.setPrimaryContactPhone(request.primaryContactPhone());
        return toResponse(org, true);
    }

    /**
     * Onboard / suspend / reactivate. Suspending (INACTIVE) signs out everyone whose only access was in this
     * organization; other users lose the branches at their next refresh.
     *
     * @return number of sessions ended
     */
    @Transactional
    public int setOrganizationStatus(Long actorUserId, Long organizationId, String status, String reason) {
        OrganizationEntity org = findOrganization(organizationId);
        String newStatus = status.toUpperCase();
        requireReason(newStatus, reason);
        org.setStatus(newStatus);
        organizationRepository.flush();
        return OrganizationEntity.STATUS_INACTIVE.equals(newStatus)
                ? endSessionsWithoutRemainingAccess(actorUserId, organizationId, null)
                : 0;
    }

    @Transactional
    public List<String> setEntitlements(Long organizationId, Collection<String> featureCodes) {
        findOrganization(organizationId);
        Set<String> wanted = new TreeSet<>(featureCodes);
        wanted.add(ALWAYS_ON_FEATURE);
        Map<String, FeatureEntity> byCode = featuresByCode(wanted);

        Map<Long, OrganizationFeatureEntity> existing = new HashMap<>();
        organizationFeatureRepository.findByOrganizationId(organizationId).forEach(of -> existing.put(of.getFeatureId(), of));
        Set<Long> wantedIds = new HashSet<>();
        for (FeatureEntity f : byCode.values()) {
            wantedIds.add(f.getFeatureId());
            OrganizationFeatureEntity of = existing.get(f.getFeatureId());
            if (of == null) {
                organizationFeatureRepository.save(new OrganizationFeatureEntity(organizationId, f.getFeatureId(), LocalDate.now(), true));
            } else if (!of.isEnabled()) {
                of.setEnabled(true);
                of.setEnabledDate(LocalDate.now());
            }
        }
        // Disabling (not deleting) keeps the history; the trigger switches the feature off in every branch
        existing.values().stream()
                .filter(of -> of.isEnabled() && !wantedIds.contains(of.getFeatureId()))
                .forEach(of -> of.setEnabled(false));
        organizationFeatureRepository.flush();
        return List.copyOf(wanted);
    }

    // ------------------------------------------------------------------ branches

    @Transactional
    public SchoolEntity createBranch(Long organizationId, CreateBranchRequest request) {
        OrganizationEntity org = findOrganization(organizationId);
        if (schoolRepository.existsByOrganizationOrganizationIdAndBranchCode(organizationId, request.branchCode())) {
            throw new IllegalStateException("Branch code " + request.branchCode() + " already exists in " + org.getOrgCode());
        }
        String schoolCode = StringUtils.hasText(request.schoolCode())
                ? request.schoolCode() : org.getOrgCode() + "_" + request.branchCode();
        if (schoolRepository.existsBySchoolCode(schoolCode)) {
            throw new IllegalStateException("A school with code " + schoolCode + " already exists.");
        }
        if (schoolRepository.findByHostUrl(request.hostUrl()).isPresent()) {
            throw new IllegalStateException("A school with this host URL already exists.");
        }
        boolean firstBranch = schoolRepository.findByOrganizationOrganizationIdOrderByIsMainBranchDescBranchName(organizationId).isEmpty();
        InstitutionTypeEntity type = StringUtils.hasText(request.institutionTypeCode())
                ? institutionType(request.institutionTypeCode()) : org.getInstitutionType();

        SchoolEntity school = schoolRepository.saveAndFlush(SchoolEntity.builder()
                .organization(org)
                .institutionType(type)
                .schoolCode(schoolCode)
                .schoolName(StringUtils.hasText(request.schoolName()) ? request.schoolName() : org.getOrgName())
                .branchCode(request.branchCode())
                .branchName(request.branchName())
                .isMainBranch(request.mainBranch() != null ? request.mainBranch() : firstBranch)
                .parentSchoolId(request.parentSchoolId())
                .hostUrl(request.hostUrl())
                .city(request.city()).state(request.state()).country(request.country())
                .timezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Asia/Kolkata")
                .board(request.board()).udiseCode(request.udiseCode()).affiliationNumber(request.affiliationNumber())
                .addressLine(request.addressLine()).pincode(request.pincode())
                .brandColor(request.brandColor()).logoUrl(request.logoUrl())
                .status(SchoolEntity.STATUS_ONBOARDING)
                .build());

        Collection<String> codes;
        if (request.featureCodes() != null) {
            codes = request.featureCodes();
        } else if (request.templateSchoolId() != null) {
            SchoolEntity template = findSchool(request.templateSchoolId());
            if (!template.getOrganization().getOrganizationId().equals(organizationId)) {
                throw new IllegalArgumentException("The template branch must belong to the same organization.");
            }
            codes = enabledFeatureCodes(template.getSchoolId());
        } else {
            // Type defaults, limited to what the organization is entitled to
            Set<String> entitled = new HashSet<>(entitledFeatureCodes(organizationId));
            codes = typeDefaultFeatureCodes(type.getInstitutionTypeId()).stream().filter(entitled::contains).toList();
        }
        setBranchFeatures(school.getSchoolId(), codes);
        return school;
    }

    @Transactional(readOnly = true)
    public BranchResponse branch(Long schoolId) {
        SchoolEntity school = findSchool(schoolId);
        return BranchResponse.from(school, enabledFeatureCodes(schoolId));
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> branches(Long organizationId) {
        return schoolRepository.findByOrganizationOrganizationIdOrderByIsMainBranchDescBranchName(organizationId).stream()
                .map(s -> BranchResponse.from(s, enabledFeatureCodes(s.getSchoolId())))
                .toList();
    }

    @Transactional
    public BranchResponse updateBranch(Long schoolId, UpdateBranchRequest r) {
        SchoolEntity s = findSchool(schoolId);
        if (r.branchName() != null) s.setBranchName(r.branchName());
        if (r.schoolName() != null) s.setSchoolName(r.schoolName());
        if (r.hostUrl() != null) s.setHostUrl(r.hostUrl());
        if (r.city() != null) s.setCity(r.city());
        if (r.state() != null) s.setState(r.state());
        if (r.country() != null) s.setCountry(r.country());
        if (r.timezone() != null) s.setTimezone(r.timezone());
        if (r.board() != null) s.setBoard(r.board());
        if (r.udiseCode() != null) s.setUdiseCode(r.udiseCode());
        if (r.affiliationNumber() != null) s.setAffiliationNumber(r.affiliationNumber());
        if (r.addressLine() != null) s.setAddressLine(r.addressLine());
        if (r.pincode() != null) s.setPincode(r.pincode());
        if (r.brandColor() != null) s.setBrandColor(r.brandColor());
        if (r.logoUrl() != null) s.setLogoUrl(r.logoUrl());
        if (r.parentSchoolId() != null) s.setParentSchoolId(r.parentSchoolId());
        schoolRepository.flush();
        return BranchResponse.from(s, enabledFeatureCodes(schoolId));
    }

    /** @return number of sessions ended (suspending signs out users with no other active branch) */
    @Transactional
    public int setBranchStatus(Long actorUserId, Long schoolId, String status, String reason) {
        SchoolEntity school = findSchool(schoolId);
        String newStatus = status.toUpperCase();
        requireReason(newStatus, reason);
        school.setStatus(newStatus);
        schoolRepository.flush();
        return SchoolEntity.STATUS_INACTIVE.equals(newStatus)
                ? endSessionsWithoutRemainingAccess(actorUserId, null, schoolId)
                : 0;
    }

    /** Replaces the branch's switched-on features. Each must be within the organization entitlement (DB trigger). */
    @Transactional
    public List<String> setBranchFeatures(Long schoolId, Collection<String> featureCodes) {
        Set<String> wanted = new TreeSet<>(featureCodes);
        wanted.add(ALWAYS_ON_FEATURE);
        Map<String, FeatureEntity> byCode = featuresByCode(wanted);
        Map<Long, SchoolFeatureEntity> existing = new HashMap<>();
        schoolFeatureRepository.findBySchoolId(schoolId).forEach(sf -> existing.put(sf.getFeatureId(), sf));
        Set<Long> wantedIds = new HashSet<>();
        for (FeatureEntity f : byCode.values()) {
            wantedIds.add(f.getFeatureId());
            SchoolFeatureEntity sf = existing.get(f.getFeatureId());
            if (sf == null) {
                schoolFeatureRepository.save(new SchoolFeatureEntity(schoolId, f.getFeatureId(), LocalDate.now(), true));
            } else if (!sf.isEnabled()) {
                sf.setEnabled(true);
                sf.setEnabledDate(LocalDate.now());
            }
        }
        existing.values().stream()
                .filter(sf -> sf.isEnabled() && !wantedIds.contains(sf.getFeatureId()))
                .forEach(sf -> sf.setEnabled(false));
        schoolFeatureRepository.flush();
        return List.copyOf(wanted);
    }

    // ------------------------------------------------------------------ helpers

    public List<String> enabledFeatureCodes(Long schoolId) {
        return jdbc.sql("SELECT f.feature_code FROM school_feature sf JOIN feature f USING (feature_id) " +
                        "WHERE sf.school_id = :id AND sf.is_enabled ORDER BY 1")
                .param("id", schoolId).query(String.class).list();
    }

    public List<String> entitledFeatureCodes(Long organizationId) {
        return jdbc.sql("SELECT f.feature_code FROM organization_feature o JOIN feature f USING (feature_id) " +
                        "WHERE o.organization_id = :id AND o.is_enabled ORDER BY 1")
                .param("id", organizationId).query(String.class).list();
    }

    private List<String> typeDefaultFeatureCodes(Long institutionTypeId) {
        return jdbc.sql("SELECT f.feature_code FROM institution_type_feature t JOIN feature f USING (feature_id) " +
                        "WHERE t.institution_type_id = :id ORDER BY 1")
                .param("id", institutionTypeId).query(String.class).list();
    }

    private int endSessionsWithoutRemainingAccess(Long actorUserId, Long organizationId, Long schoolId) {
        // Users who lose all access: every active grant is inside the suspended org / branch
        List<UUID> sessionIds = jdbc.sql("""
                        WITH access AS (
                            SELECT g.user_id, s.school_id, s.organization_id FROM user_school_staff_type g
                            JOIN school s ON s.school_id = g.school_id AND s.status <> 'INACTIVE'
                            JOIN organization o ON o.organization_id = s.organization_id AND o.status <> 'INACTIVE'
                            WHERE g.is_active
                            UNION
                            SELECT m.user_id, s.school_id, s.organization_id FROM organization_member m
                            JOIN school s ON s.organization_id = m.organization_id AND s.status <> 'INACTIVE'
                            JOIN organization o ON o.organization_id = s.organization_id AND o.status <> 'INACTIVE'
                            WHERE m.is_active
                        ), affected AS (
                            SELECT g.user_id FROM user_school_staff_type g JOIN school s ON s.school_id = g.school_id
                            WHERE g.is_active AND (s.organization_id = :orgId OR s.school_id = :schoolId)
                            UNION
                            SELECT m.user_id FROM organization_member m JOIN school s ON s.organization_id = m.organization_id
                            WHERE m.is_active AND (m.organization_id = :orgId OR s.school_id = :schoolId)
                        )
                        SELECT us.session_id FROM user_session us
                        JOIN affected a ON a.user_id = us.user_id
                        JOIN app_user u ON u.user_id = us.user_id AND NOT u.is_super_user
                        WHERE us.logout_at IS NULL
                          AND NOT EXISTS (SELECT 1 FROM access x WHERE x.user_id = us.user_id)
                        """)
                .param("orgId", organizationId).param("schoolId", schoolId)
                .query(UUID.class).list();
        sessionIds.forEach(id -> sessionRepository.findById(id)
                .ifPresent(s -> sessionService.end(s, "REVOKED_BY_ADMIN", actorUserId)));
        return sessionIds.size();
    }

    private static void requireReason(String status, String reason) {
        if ("INACTIVE".equals(status) && !StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("A reason (e.g. PAYMENT_OVERDUE) is required to suspend.");
        }
    }

    private Map<String, FeatureEntity> featuresByCode(Collection<String> codes) {
        Map<String, FeatureEntity> byCode = new HashMap<>();
        featureRepository.findByFeatureCodeIn(codes).forEach(f -> byCode.put(f.getFeatureCode(), f));
        List<String> unknown = codes.stream().filter(c -> !byCode.containsKey(c)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown feature codes: " + unknown);
        }
        return byCode;
    }

    private InstitutionTypeEntity institutionType(String code) {
        return institutionTypeRepository.findByTypeCode(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown institution type: " + code));
    }

    OrganizationEntity findOrganization(Long organizationId) {
        return organizationRepository.findWithTypeByOrganizationId(organizationId)
                .orElseThrow(() -> new NoSuchElementException("Organization not found: " + organizationId));
    }

    SchoolEntity findSchool(Long schoolId) {
        return schoolRepository.findWithOrganizationBySchoolId(schoolId)
                .orElseThrow(() -> new NoSuchElementException("School not found: " + schoolId));
    }

    private OrganizationResponse toResponse(OrganizationEntity o, boolean withBranches) {
        return new OrganizationResponse(o.getOrganizationId(), o.getOrgCode(), o.getOrgName(), o.getLegalName(),
                o.getInstitutionType().getTypeCode(), o.getDeploymentModel(), o.getBranchLimit(),
                o.getPrimaryContactEmail(), o.getPrimaryContactPhone(), o.getStatus(), o.getCreatedAt(),
                entitledFeatureCodes(o.getOrganizationId()),
                withBranches ? branches(o.getOrganizationId()) : List.of());
    }
}
