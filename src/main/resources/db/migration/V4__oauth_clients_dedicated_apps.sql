-- =============================================================================
-- school-auth V4 — ER v5 identity schema, part 2
--   JDBC OAuth client store (replaces InMemoryRegisteredClientRepository),
--   dedicated apps (premium DEDICATED_APP), pinned branches per app.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Spring Authorization Server JDBC stores (oauth2-registered-client-schema.sql,
--    oauth2-authorization-consent-schema.sql; timestamp -> timestamptz for Postgres)
-- ---------------------------------------------------------------------------
CREATE TABLE oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL PRIMARY KEY,
    client_id                     VARCHAR(100)  NOT NULL UNIQUE,
    client_id_issued_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                 VARCHAR(200),
    client_secret_expires_at      TIMESTAMPTZ,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000),
    post_logout_redirect_uris     VARCHAR(1000),
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization (principal_name);

-- ---------------------------------------------------------------------------
-- 2. Dedicated apps: one OAuth client per organization build (premium)
-- ---------------------------------------------------------------------------
CREATE TABLE app_client (
    app_client_id         BIGSERIAL PRIMARY KEY,
    client_id             VARCHAR(100) NOT NULL UNIQUE
        REFERENCES oauth2_registered_client (client_id) ON UPDATE CASCADE,
    organization_id       BIGINT       NOT NULL REFERENCES organization (organization_id) ON DELETE CASCADE,
    app_name              VARCHAR(100) NOT NULL,
    android_package       VARCHAR(150) UNIQUE,
    ios_bundle_id         VARCHAR(150) UNIQUE,
    ios_team_id           VARCHAR(20),
    redirect_uri          VARCHAR(300) NOT NULL UNIQUE,
    deep_link_domain      VARCHAR(255) UNIQUE,
    api_base_url          VARCHAR(500),          -- self-hosted school service; NULL = hosted
    branding              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    push_credentials_ref  VARCHAR(300),          -- pointer into the secrets store, never the secret
    store_account_owner   VARCHAR(20)  NOT NULL DEFAULT 'ORGANIZATION'
        CHECK (store_account_owner IN ('ORGANIZATION', 'PLATFORM')),
    min_supported_version VARCHAR(20),
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')),
    status_reason         VARCHAR(30)
        CHECK (status_reason IN ('PAYMENT_OVERDUE', 'ENTITLEMENT_REVOKED', 'STORE_REMOVED', 'ORG_REQUEST', 'OTHER')),
    status_changed_at     TIMESTAMPTZ,
    created_by_user_id    BIGINT REFERENCES app_user (user_id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_client_org UNIQUE (organization_id, app_client_id),
    CONSTRAINT ck_app_client_platform CHECK (android_package IS NOT NULL OR ios_bundle_id IS NOT NULL),
    CONSTRAINT ck_app_client_reason CHECK (status NOT IN ('SUSPENDED', 'RETIRED') OR status_reason IS NOT NULL)
);

CREATE TABLE app_client_school (
    app_client_id   BIGINT      NOT NULL,
    school_id       BIGINT      NOT NULL,
    organization_id BIGINT      NOT NULL,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (app_client_id, school_id),
    -- Both composite FKs carry organization_id: an app can only pin branches of its own organization
    CONSTRAINT fk_acs_app_same_org FOREIGN KEY (organization_id, app_client_id)
        REFERENCES app_client (organization_id, app_client_id) ON DELETE CASCADE,
    CONSTRAINT fk_acs_school_same_org FOREIGN KEY (organization_id, school_id)
        REFERENCES school (organization_id, school_id) ON DELETE CASCADE
);

-- ACTIVE needs the DEDICATED_APP entitlement on the organization
CREATE OR REPLACE FUNCTION enforce_app_client_entitlement() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'ACTIVE' AND NOT EXISTS (
        SELECT 1 FROM organization_feature ofe JOIN feature f ON f.feature_id = ofe.feature_id
        WHERE ofe.organization_id = NEW.organization_id AND f.feature_code = 'DEDICATED_APP' AND ofe.is_enabled
    ) THEN
        RAISE EXCEPTION 'Organization % is not entitled to DEDICATED_APP', NEW.organization_id
            USING ERRCODE = 'check_violation';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.status_changed_at := now();
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
END $$;

CREATE TRIGGER trg_app_client_entitlement BEFORE INSERT OR UPDATE ON app_client
    FOR EACH ROW EXECUTE FUNCTION enforce_app_client_entitlement();

-- Revoking DEDICATED_APP suspends the organization's live apps
CREATE OR REPLACE FUNCTION suspend_apps_on_entitlement_revoke() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_org BIGINT := CASE WHEN TG_OP = 'DELETE' THEN OLD.organization_id ELSE NEW.organization_id END;
    v_feature BIGINT := CASE WHEN TG_OP = 'DELETE' THEN OLD.feature_id ELSE NEW.feature_id END;
BEGIN
    IF (TG_OP = 'DELETE' OR (OLD.is_enabled AND NOT NEW.is_enabled))
       AND EXISTS (SELECT 1 FROM feature WHERE feature_id = v_feature AND feature_code = 'DEDICATED_APP') THEN
        UPDATE app_client SET status = 'SUSPENDED', status_reason = 'ENTITLEMENT_REVOKED'
        WHERE organization_id = v_org AND status = 'ACTIVE';
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_app_client_revoke
    AFTER UPDATE OF is_enabled OR DELETE ON organization_feature
    FOR EACH ROW EXECUTE FUNCTION suspend_apps_on_entitlement_revoke();

-- Branches that can get tokens through a dedicated app right now (read by the token customizer)
CREATE VIEW app_client_effective_school AS
SELECT ac.client_id, ac.app_client_id, ac.organization_id, acs.school_id, acs.sort_order
FROM app_client ac
JOIN app_client_school acs ON acs.app_client_id = ac.app_client_id
JOIN school s ON s.school_id = acs.school_id
JOIN organization o ON o.organization_id = ac.organization_id
WHERE ac.status = 'ACTIVE'
  AND s.status = 'ACTIVE'
  AND o.status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM organization_feature ofe JOIN feature f ON f.feature_id = ofe.feature_id
              WHERE ofe.organization_id = ac.organization_id AND f.feature_code = 'DEDICATED_APP' AND ofe.is_enabled);
