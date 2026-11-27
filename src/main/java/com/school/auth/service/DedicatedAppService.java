package com.school.auth.service;

import com.school.auth.configuration.OAuthClientBootstrap;
import com.school.auth.dto.AppDto.*;
import com.school.auth.entity.AppClientEntity;
import com.school.auth.entity.AppClientSchoolEntity;
import com.school.auth.repository.AppClientRepository;
import com.school.auth.repository.AppClientSchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Dedicated (organization-branded) apps, premium DEDICATED_APP. Each app is its own public OAuth client;
 * which branches it may sign into is enforced at token issuance (app_client_effective_school), not by the
 * app binary.
 */
@Service
@RequiredArgsConstructor
public class DedicatedAppService {

    private final AppClientRepository appClientRepository;
    private final AppClientSchoolRepository appClientSchoolRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final JdbcClient jdbc;

    @Value("${oauth2.token.access-token-time-to-live-minutes}")
    private long accessTokenTtlMinutes;

    @Value("${oauth2.token.refresh-token-time-to-live-days}")
    private long refreshTokenTtlDays;

    @Transactional(readOnly = true)
    public List<AppResponse> list(Long organizationId) {
        List<AppClientEntity> apps = organizationId == null ? appClientRepository.findAll()
                : appClientRepository.findByOrganizationId(organizationId);
        return apps.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppResponse get(Long appClientId) {
        return toResponse(find(appClientId));
    }

    @Transactional
    public AppResponse create(Long actorUserId, CreateAppRequest r) {
        if (registeredClientRepository.findByClientId(r.clientId()) != null) {
            throw new IllegalStateException("OAuth client " + r.clientId() + " already exists.");
        }
        registeredClientRepository.save(OAuthClientBootstrap.publicAppClient(r.clientId(), r.clientId(), r.appName(),
                r.redirectUri(), accessTokenTtlMinutes, refreshTokenTtlDays));
        AppClientEntity app = appClientRepository.saveAndFlush(AppClientEntity.builder()
                .clientId(r.clientId())
                .organizationId(r.organizationId())
                .appName(r.appName())
                .androidPackage(r.androidPackage())
                .iosBundleId(r.iosBundleId())
                .iosTeamId(r.iosTeamId())
                .redirectUri(r.redirectUri())
                .deepLinkDomain(r.deepLinkDomain())
                .apiBaseUrl(r.apiBaseUrl())
                .branding(r.branding() == null ? new HashMap<>() : r.branding())
                .pushCredentialsRef(r.pushCredentialsRef())
                .storeAccountOwner(r.storeAccountOwner() == null ? "ORGANIZATION" : r.storeAccountOwner())
                .minSupportedVersion(r.minSupportedVersion())
                .createdByUserId(actorUserId)
                .build());
        if (r.schoolIds() != null) {
            pin(app, r.schoolIds());
        }
        return toResponse(app);
    }

    @Transactional
    public AppResponse update(Long appClientId, UpdateAppRequest r) {
        AppClientEntity app = find(appClientId);
        if (r.appName() != null) app.setAppName(r.appName());
        if (r.apiBaseUrl() != null) app.setApiBaseUrl(r.apiBaseUrl());
        if (r.branding() != null) app.setBranding(r.branding());
        if (r.pushCredentialsRef() != null) app.setPushCredentialsRef(r.pushCredentialsRef());
        if (r.minSupportedVersion() != null) app.setMinSupportedVersion(r.minSupportedVersion());
        if (r.deepLinkDomain() != null) app.setDeepLinkDomain(r.deepLinkDomain());
        appClientRepository.flush();
        return toResponse(app);
    }

    /** ACTIVE requires the DEDICATED_APP entitlement (DB trigger); SUSPENDED / RETIRED require a reason. */
    @Transactional
    public AppResponse setStatus(Long appClientId, AppStatusRequest r) {
        AppClientEntity app = find(appClientId);
        app.setStatus(r.status());
        app.setStatusReason("DRAFT".equals(r.status()) || "ACTIVE".equals(r.status()) ? null : r.reason());
        appClientRepository.flush();
        return toResponse(find(appClientId));
    }

    @Transactional
    public AppResponse setPinnedSchools(Long appClientId, List<Long> schoolIds) {
        AppClientEntity app = find(appClientId);
        appClientSchoolRepository.deleteByAppClientId(appClientId);
        appClientSchoolRepository.flush();
        pin(app, schoolIds);
        return toResponse(app);
    }

    /** Public: what a dedicated build needs at start-up. Unknown client ids are treated as the generic app. */
    @Transactional(readOnly = true)
    public Optional<AppConfigResponse> config(String clientId) {
        return appClientRepository.findByClientId(clientId).map(app -> new AppConfigResponse(app.getClientId(),
                app.getAppName(), app.getStatus(), app.getApiBaseUrl(), app.getBranding(), app.getMinSupportedVersion(),
                jdbc.sql("""
                                SELECT e.school_id, s.school_name, s.branch_name, s.host_url, e.sort_order
                                FROM app_client_effective_school e JOIN school s ON s.school_id = e.school_id
                                WHERE e.client_id = :clientId ORDER BY e.sort_order, s.branch_name
                                """)
                        .param("clientId", clientId)
                        .query((rs, i) -> new PinnedSchool(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)))
                        .list()));
    }

    private void pin(AppClientEntity app, List<Long> schoolIds) {
        int order = 0;
        for (Long schoolId : new LinkedHashSet<>(schoolIds)) {
            AppClientSchoolEntity pinned = new AppClientSchoolEntity();
            pinned.setAppClientId(app.getAppClientId());
            pinned.setSchoolId(schoolId);
            pinned.setOrganizationId(app.getOrganizationId()); // composite FKs refuse other organizations' branches
            pinned.setSortOrder(order++);
            appClientSchoolRepository.save(pinned);
        }
        appClientSchoolRepository.flush();
    }

    private AppClientEntity find(Long appClientId) {
        return appClientRepository.findById(appClientId)
                .orElseThrow(() -> new NoSuchElementException("App not found: " + appClientId));
    }

    private AppResponse toResponse(AppClientEntity a) {
        return new AppResponse(a.getAppClientId(), a.getClientId(), a.getOrganizationId(), a.getAppName(),
                a.getAndroidPackage(), a.getIosBundleId(), a.getIosTeamId(), a.getRedirectUri(), a.getDeepLinkDomain(),
                a.getApiBaseUrl(), a.getBranding(), a.getStoreAccountOwner(), a.getMinSupportedVersion(), a.getStatus(),
                a.getStatusReason(), a.getStatusChangedAt(),
                appClientSchoolRepository.findByAppClientIdOrderBySortOrder(a.getAppClientId()).stream()
                        .map(AppClientSchoolEntity::getSchoolId).toList());
    }
}
