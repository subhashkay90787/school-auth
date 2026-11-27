-- =============================================================================
-- school-auth V5 — ER v5 identity schema, part 3
--   devices (push tokens per app client), sessions (one per refresh-token family),
--   login attempts (hashed identifier only).
-- =============================================================================

CREATE TABLE user_device (
    user_device_id BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    client_id      VARCHAR(100) NOT NULL REFERENCES oauth2_registered_client (client_id) ON UPDATE CASCADE,
    platform       VARCHAR(10)  NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    push_token     VARCHAR(500),
    app_version    VARCHAR(30),
    last_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    device_id      VARCHAR(100) NOT NULL,
    device_name    VARCHAR(150),
    first_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_trusted     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_user_device UNIQUE (user_id, client_id, device_id)
);

-- A push token belongs to one device registration per app
CREATE UNIQUE INDEX uq_user_device_push_token ON user_device (client_id, push_token) WHERE push_token IS NOT NULL;

CREATE TABLE user_session (
    session_id              UUID         PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    school_id               BIGINT REFERENCES school (school_id) ON DELETE SET NULL, -- branch chosen at sign-in
    user_device_id          BIGINT REFERENCES user_device (user_device_id) ON DELETE SET NULL,
    client_id               VARCHAR(100),
    -- = oauth2_authorization.id: the authorization row that holds the rotating refresh token
    refresh_token_family_id VARCHAR(100) NOT NULL UNIQUE,
    auth_method             VARCHAR(20)  NOT NULL
        CHECK (auth_method IN ('WHATSAPP_OTP', 'EMAIL_OTP', 'SOCIAL', 'REFRESH')),
    device_id               VARCHAR(100),
    platform                VARCHAR(10) CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    app_version             VARCHAR(30),
    ip_address              VARCHAR(45),
    user_agent              VARCHAR(500),
    login_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    logout_at               TIMESTAMPTZ,
    end_reason              VARCHAR(20)
        CHECK (end_reason IN ('LOGOUT', 'EXPIRED', 'REVOKED_BY_USER', 'REVOKED_BY_ADMIN', 'REFRESH_REUSE', 'USER_DEACTIVATED')),
    revoked_by_user_id      BIGINT REFERENCES app_user (user_id),
    CONSTRAINT ck_session_end CHECK ((logout_at IS NULL) = (end_reason IS NULL))
);

CREATE INDEX idx_user_session_user_active ON user_session (user_id) WHERE logout_at IS NULL;
CREATE INDEX idx_user_session_school ON user_session (school_id, last_seen_at DESC);

CREATE TABLE login_attempt (
    attempt_id      BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES app_user (user_id) ON DELETE SET NULL, -- NULL when the identifier is unknown
    school_id       BIGINT REFERENCES school (school_id) ON DELETE SET NULL,
    identifier_hash VARCHAR(64) NOT NULL,                                   -- SHA-256 of the normalized phone/email
    method          VARCHAR(20) NOT NULL CHECK (method IN ('WHATSAPP_OTP', 'EMAIL_OTP')),
    result          VARCHAR(30) NOT NULL
        CHECK (result IN ('OTP_SENT', 'SUCCESS', 'WRONG_OTP', 'EXPIRED_OTP', 'LOCKED', 'BLOCKED_INACTIVE_BRANCH')),
    ip_address      VARCHAR(45),
    device_id       VARCHAR(100),
    attempted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_attempt_school_time ON login_attempt (school_id, attempted_at DESC);
CREATE INDEX idx_login_attempt_identifier ON login_attempt (identifier_hash, attempted_at DESC);
