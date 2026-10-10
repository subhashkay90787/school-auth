-- =============================================================================
-- IDENTITY & MULTI-TENANT SCHEMA (PostgreSQL DDL)
-- Flyway runs this with search_path = identity (see spring.flyway.default-schema)
-- =============================================================================

-- Master Table for Schools (Multi-Tenant Locations)
CREATE TABLE school (
                        school_id BIGSERIAL PRIMARY KEY,
                        school_code VARCHAR(50) UNIQUE NOT NULL,
                        school_name VARCHAR(150) NOT NULL,
                        host_url VARCHAR(255) UNIQUE NOT NULL,
                        city VARCHAR(100) NOT NULL,
                        state VARCHAR(100) NOT NULL,
                        country VARCHAR(100) NOT NULL,
                        status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Master Table for Platform Features
CREATE TABLE feature (
                         feature_id BIGSERIAL PRIMARY KEY,
                         feature_code VARCHAR(50) UNIQUE NOT NULL,
                         feature_name VARCHAR(100) NOT NULL,
                         description TEXT,
                         is_active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Many-to-Many Mapping: Features enabled per School
CREATE TABLE school_feature (
                                school_id BIGINT NOT NULL,
                                feature_id BIGINT NOT NULL,
                                enabled_date DATE DEFAULT CURRENT_DATE NOT NULL,
                                is_enabled BOOLEAN DEFAULT TRUE NOT NULL,
                                PRIMARY KEY (school_id, feature_id),
                                CONSTRAINT fk_sf_school FOREIGN KEY (school_id) REFERENCES school(school_id) ON DELETE CASCADE,
                                CONSTRAINT fk_sf_feature FOREIGN KEY (feature_id) REFERENCES feature(feature_id) ON DELETE CASCADE
);

-- Master User Entity (Supports Phone OTP, Email & Social Logins)
-- Named app_user because USER is a reserved word in PostgreSQL.
CREATE TABLE app_user (
                          user_id BIGSERIAL PRIMARY KEY,
                          first_name VARCHAR(50) NOT NULL,
                          last_name VARCHAR(50) NOT NULL,
                          phone_number VARCHAR(20) UNIQUE NOT NULL,
                          alternate_phone_number VARCHAR(20),
                          email VARCHAR(100) UNIQUE NOT NULL,
                          is_active_flag BOOLEAN DEFAULT TRUE NOT NULL,
                          is_super_user BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE INDEX idx_user_alternate_phone_number ON app_user(alternate_phone_number);

-- Master Table for Staff Types / Platform Roles
CREATE TABLE staff_type (
                            staff_type_id BIGSERIAL PRIMARY KEY,
                            type_code VARCHAR(50) UNIQUE NOT NULL, -- seeded in V2__seed_default_roles.sql
                            type_name VARCHAR(100) NOT NULL,
                            is_active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Junction Table: Multi-School & Multi-Role Assignments per User
CREATE TABLE user_school_staff_type (
                                        user_school_staff_id BIGSERIAL PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        school_id BIGINT NOT NULL,
                                        staff_type_id BIGINT NOT NULL,
                                        assigned_date DATE DEFAULT CURRENT_DATE NOT NULL,
                                        is_active BOOLEAN DEFAULT TRUE NOT NULL,
                                        CONSTRAINT fk_usst_user FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
                                        CONSTRAINT fk_usst_school FOREIGN KEY (school_id) REFERENCES school(school_id) ON DELETE CASCADE,
                                        CONSTRAINT fk_usst_staff_type FOREIGN KEY (staff_type_id) REFERENCES staff_type(staff_type_id) ON DELETE CASCADE,
                                        CONSTRAINT uq_user_school_role UNIQUE (user_id, school_id, staff_type_id)
);

-- Invitations sent by super users / school admins
CREATE TABLE school_invitation (
                                   invitation_id BIGSERIAL PRIMARY KEY,
                                   school_id BIGINT NOT NULL,
                                   email VARCHAR(255) NOT NULL,
                                   target_role_code VARCHAR(255) NOT NULL,
                                   invited_by_user_id BIGINT NOT NULL,
                                   token VARCHAR(255) UNIQUE NOT NULL,
                                   expires_at TIMESTAMP(6) NOT NULL,
                                   is_used BOOLEAN DEFAULT FALSE NOT NULL,
                                   created_at TIMESTAMP(6) NOT NULL,
                                   CONSTRAINT fk_si_school FOREIGN KEY (school_id) REFERENCES school(school_id) ON DELETE CASCADE,
                                   CONSTRAINT fk_si_invited_by FOREIGN KEY (invited_by_user_id) REFERENCES app_user(user_id)
);

-- =============================================================================
-- Spring Authorization Server: issued tokens (JdbcOAuth2AuthorizationService)
-- Adapted from oauth2-authorization-schema.sql (blob -> text, timestamp -> timestamptz)
-- =============================================================================
CREATE TABLE oauth2_authorization (
                                      id varchar(100) NOT NULL,
                                      registered_client_id varchar(100) NOT NULL,
                                      principal_name varchar(200) NOT NULL,
                                      authorization_grant_type varchar(100) NOT NULL,
                                      authorized_scopes varchar(1000) DEFAULT NULL,
                                      attributes text DEFAULT NULL,
                                      state varchar(500) DEFAULT NULL,
                                      authorization_code_value text DEFAULT NULL,
                                      authorization_code_issued_at timestamptz DEFAULT NULL,
                                      authorization_code_expires_at timestamptz DEFAULT NULL,
                                      authorization_code_metadata text DEFAULT NULL,
                                      access_token_value text DEFAULT NULL,
                                      access_token_issued_at timestamptz DEFAULT NULL,
                                      access_token_expires_at timestamptz DEFAULT NULL,
                                      access_token_metadata text DEFAULT NULL,
                                      access_token_type varchar(100) DEFAULT NULL,
                                      access_token_scopes varchar(1000) DEFAULT NULL,
                                      oidc_id_token_value text DEFAULT NULL,
                                      oidc_id_token_issued_at timestamptz DEFAULT NULL,
                                      oidc_id_token_expires_at timestamptz DEFAULT NULL,
                                      oidc_id_token_metadata text DEFAULT NULL,
                                      refresh_token_value text DEFAULT NULL,
                                      refresh_token_issued_at timestamptz DEFAULT NULL,
                                      refresh_token_expires_at timestamptz DEFAULT NULL,
                                      refresh_token_metadata text DEFAULT NULL,
                                      user_code_value text DEFAULT NULL,
                                      user_code_issued_at timestamptz DEFAULT NULL,
                                      user_code_expires_at timestamptz DEFAULT NULL,
                                      user_code_metadata text DEFAULT NULL,
                                      device_code_value text DEFAULT NULL,
                                      device_code_issued_at timestamptz DEFAULT NULL,
                                      device_code_expires_at timestamptz DEFAULT NULL,
                                      device_code_metadata text DEFAULT NULL,
                                      PRIMARY KEY (id)
);
