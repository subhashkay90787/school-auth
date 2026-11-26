package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/** A dedicated (organization-branded) app build: its own OAuth client and pinned branches. */
@Entity
@Table(name = "app_client", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "app_client_id")
    private Long appClientId;

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "app_name", nullable = false, length = 100)
    private String appName;

    @Column(name = "android_package", length = 150)
    private String androidPackage;

    @Column(name = "ios_bundle_id", length = 150)
    private String iosBundleId;

    @Column(name = "ios_team_id", length = 20)
    private String iosTeamId;

    @Column(name = "redirect_uri", nullable = false, length = 300)
    private String redirectUri;

    @Column(name = "deep_link_domain")
    private String deepLinkDomain;

    @Column(name = "api_base_url", length = 500)
    private String apiBaseUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "branding", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> branding;

    @Column(name = "push_credentials_ref", length = 300)
    private String pushCredentialsRef;

    @Builder.Default
    @Column(name = "store_account_owner", nullable = false, length = 20)
    private String storeAccountOwner = "ORGANIZATION";

    @Column(name = "min_supported_version", length = 20)
    private String minSupportedVersion;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "status_reason", length = 30)
    private String statusReason;

    @Column(name = "status_changed_at", insertable = false, updatable = false)
    private OffsetDateTime statusChangedAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
