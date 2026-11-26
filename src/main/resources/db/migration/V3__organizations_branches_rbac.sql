-- =============================================================================
-- school-auth V3 — ER v5 identity schema, part 1
--   institution types, organizations, branches (school), entitlements,
--   built-in + custom roles, permissions, role mappings with per-school overrides,
--   org-wide members, invitations, branch provisioning, feature backlog.
-- Runs with search_path = identity (spring.flyway.default-schema).
-- Existing schools are backfilled as the MAIN branch of their own organization.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Institution types (drive academic model and UI terminology)
-- ---------------------------------------------------------------------------
CREATE TABLE institution_type (
    institution_type_id BIGSERIAL PRIMARY KEY,
    type_code           VARCHAR(30)  NOT NULL UNIQUE
        CHECK (type_code IN ('SCHOOL', 'COLLEGE', 'UNIVERSITY', 'INSTITUTE', 'TUITION_CENTRE')),
    type_name           VARCHAR(100) NOT NULL,
    academic_model      VARCHAR(20)  NOT NULL
        CHECK (academic_model IN ('GRADE_SECTION', 'PROGRAM_TERM', 'COURSE_BATCH')),
    terminology         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO institution_type (type_code, type_name, academic_model, terminology) VALUES
    ('SCHOOL', 'School', 'GRADE_SECTION',
     '{"unit":"Class","learner":"Student","faculty":"Teacher","head":"Principal","term":"Term","guardian":"Parent","period":"Period"}'),
    ('COLLEGE', 'College', 'PROGRAM_TERM',
     '{"unit":"Section","learner":"Student","faculty":"Faculty","head":"Principal","term":"Semester","guardian":"Parent","period":"Lecture"}'),
    ('UNIVERSITY', 'University', 'PROGRAM_TERM',
     '{"unit":"Section","learner":"Student","faculty":"Faculty","head":"Dean","term":"Semester","guardian":"Guardian","period":"Lecture"}'),
    ('INSTITUTE', 'Institute', 'COURSE_BATCH',
     '{"unit":"Batch","learner":"Trainee","faculty":"Trainer","head":"Director","term":"Batch cycle","guardian":"Guardian","period":"Session"}'),
    ('TUITION_CENTRE', 'Tuition centre', 'COURSE_BATCH',
     '{"unit":"Batch","learner":"Student","faculty":"Tutor","head":"Centre head","term":"Batch cycle","guardian":"Parent","period":"Session"}');

CREATE TABLE institution_type_feature (
    institution_type_id BIGINT NOT NULL REFERENCES institution_type (institution_type_id) ON DELETE CASCADE,
    feature_id          BIGINT NOT NULL REFERENCES feature (feature_id) ON DELETE CASCADE,
    PRIMARY KEY (institution_type_id, feature_id)
);

-- ---------------------------------------------------------------------------
-- 2. Organizations (trust / society / group) owning one or more branches
-- ---------------------------------------------------------------------------
CREATE TABLE organization (
    organization_id       BIGSERIAL PRIMARY KEY,
    org_code              VARCHAR(50)  NOT NULL UNIQUE,
    org_name              VARCHAR(150) NOT NULL,
    legal_name            VARCHAR(200),
    institution_type_id   BIGINT       NOT NULL REFERENCES institution_type (institution_type_id),
    deployment_model      VARCHAR(20)  NOT NULL DEFAULT 'HOSTED'
        CHECK (deployment_model IN ('HOSTED', 'SELF_HOSTED')),
    branch_limit          INTEGER CHECK (branch_limit IS NULL OR branch_limit > 0),
    primary_contact_email VARCHAR(255),
    primary_contact_phone VARCHAR(20),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ONBOARDING'
        CHECK (status IN ('ONBOARDING', 'ACTIVE', 'INACTIVE')),
    created_by_user_id    BIGINT REFERENCES app_user (user_id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 3. School = one branch (tenant key)
-- ---------------------------------------------------------------------------
ALTER TABLE school DROP CONSTRAINT IF EXISTS school_status_check;
ALTER TABLE school
    ALTER COLUMN status SET DEFAULT 'ONBOARDING',
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT school_status_check CHECK (status IN ('ONBOARDING', 'ACTIVE', 'INACTIVE')),
    ADD COLUMN organization_id     BIGINT REFERENCES organization (organization_id),
    ADD COLUMN parent_school_id    BIGINT,
    ADD COLUMN institution_type_id BIGINT REFERENCES institution_type (institution_type_id),
    ADD COLUMN branch_code         VARCHAR(50),
    ADD COLUMN branch_name         VARCHAR(150),
    ADD COLUMN is_main_branch      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN udise_code          VARCHAR(20),
    ADD COLUMN board               VARCHAR(20)
        CHECK (board IN ('CBSE', 'ICSE', 'STATE', 'IB', 'CAMBRIDGE', 'UNIVERSITY', 'NONE')),
    ADD COLUMN affiliation_number  VARCHAR(50),
    ADD COLUMN brand_color         VARCHAR(9),
    ADD COLUMN logo_url            VARCHAR(500),
    ADD COLUMN timezone            VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN address_line        VARCHAR(300),
    ADD COLUMN pincode             VARCHAR(10),
    -- Not in ER v5: bumped by every change that alters token contents for the branch (features, role mappings)
    ADD COLUMN perm_version        BIGINT      NOT NULL DEFAULT 1;

-- Backfill: every existing school becomes the MAIN branch of its own organization
INSERT INTO organization (org_code, org_name, institution_type_id, status)
SELECT s.school_code, s.school_name,
       (SELECT institution_type_id FROM institution_type WHERE type_code = 'SCHOOL'),
       s.status
FROM school s;

UPDATE school s
SET organization_id     = o.organization_id,
    institution_type_id = o.institution_type_id,
    branch_code         = 'MAIN',
    branch_name         = s.school_name,
    is_main_branch      = TRUE
FROM organization o
WHERE o.org_code = s.school_code;

ALTER TABLE school
    ALTER COLUMN organization_id SET NOT NULL,
    ALTER COLUMN institution_type_id SET NOT NULL,
    ALTER COLUMN branch_code SET NOT NULL,
    ALTER COLUMN branch_name SET NOT NULL,
    ADD CONSTRAINT uq_school_org_branch_code UNIQUE (organization_id, branch_code),
    ADD CONSTRAINT uq_school_org_school UNIQUE (organization_id, school_id),
    -- A parent campus must belong to the same organization
    ADD CONSTRAINT fk_school_parent_same_org FOREIGN KEY (organization_id, parent_school_id)
        REFERENCES school (organization_id, school_id),
    ADD CONSTRAINT ck_school_parent_not_self CHECK (parent_school_id IS NULL OR parent_school_id <> school_id);

CREATE UNIQUE INDEX uq_school_one_main_branch ON school (organization_id) WHERE is_main_branch;
CREATE INDEX idx_school_organization ON school (organization_id);

CREATE OR REPLACE FUNCTION enforce_branch_limit() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_limit INTEGER;
    v_count INTEGER;
BEGIN
    SELECT branch_limit INTO v_limit FROM organization WHERE organization_id = NEW.organization_id FOR UPDATE;
    IF v_limit IS NOT NULL THEN
        SELECT count(*) INTO v_count FROM school
        WHERE organization_id = NEW.organization_id AND status <> 'INACTIVE'
          AND school_id IS DISTINCT FROM NEW.school_id;
        IF v_count >= v_limit THEN
            RAISE EXCEPTION 'Organization % has reached its branch limit of %', NEW.organization_id, v_limit
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_school_branch_limit
    BEFORE INSERT OR UPDATE OF organization_id, status ON school
    FOR EACH ROW WHEN (NEW.status <> 'INACTIVE')
    EXECUTE FUNCTION enforce_branch_limit();

CREATE OR REPLACE FUNCTION bump_perm_version(p_school_id BIGINT) RETURNS void
LANGUAGE sql AS $$
    UPDATE school SET perm_version = perm_version + 1
    WHERE p_school_id IS NULL OR school_id = p_school_id;
$$;

-- ---------------------------------------------------------------------------
-- 4. Feature entitlements: organization ceiling -> branch switches
-- ---------------------------------------------------------------------------
CREATE TABLE organization_feature (
    organization_id BIGINT  NOT NULL REFERENCES organization (organization_id) ON DELETE CASCADE,
    feature_id      BIGINT  NOT NULL REFERENCES feature (feature_id) ON DELETE CASCADE,
    enabled_date    DATE    NOT NULL DEFAULT CURRENT_DATE,
    is_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (organization_id, feature_id)
);

-- Existing branch features become the organization's entitlement
INSERT INTO organization_feature (organization_id, feature_id, enabled_date, is_enabled)
SELECT s.organization_id, sf.feature_id, min(sf.enabled_date), bool_or(sf.is_enabled)
FROM school_feature sf JOIN school s ON s.school_id = sf.school_id
GROUP BY s.organization_id, sf.feature_id;

-- A branch may only switch on a feature its organization is entitled to
CREATE OR REPLACE FUNCTION enforce_school_feature_entitlement() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.is_enabled AND NOT EXISTS (
        SELECT 1 FROM organization_feature ofe JOIN school s ON s.organization_id = ofe.organization_id
        WHERE s.school_id = NEW.school_id AND ofe.feature_id = NEW.feature_id AND ofe.is_enabled
    ) THEN
        RAISE EXCEPTION 'Feature % is not in the organization entitlement for school %', NEW.feature_id, NEW.school_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_school_feature_entitlement
    BEFORE INSERT OR UPDATE ON school_feature
    FOR EACH ROW EXECUTE FUNCTION enforce_school_feature_entitlement();

-- Revoking an entitlement switches the feature off in every branch of the organization
CREATE OR REPLACE FUNCTION cascade_organization_feature_revoke() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_org BIGINT := CASE WHEN TG_OP = 'DELETE' THEN OLD.organization_id ELSE NEW.organization_id END;
    v_feature BIGINT := CASE WHEN TG_OP = 'DELETE' THEN OLD.feature_id ELSE NEW.feature_id END;
BEGIN
    IF TG_OP = 'DELETE' OR (OLD.is_enabled AND NOT NEW.is_enabled) THEN
        UPDATE school_feature sf SET is_enabled = FALSE
        FROM school s
        WHERE s.school_id = sf.school_id AND s.organization_id = v_org
          AND sf.feature_id = v_feature AND sf.is_enabled;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_organization_feature_revoke
    AFTER UPDATE OF is_enabled OR DELETE ON organization_feature
    FOR EACH ROW EXECUTE FUNCTION cascade_organization_feature_revoke();

CREATE OR REPLACE FUNCTION school_feature_bump_perm_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM bump_perm_version(CASE WHEN TG_OP = 'DELETE' THEN OLD.school_id ELSE NEW.school_id END);
    RETURN NULL;
END $$;

CREATE TRIGGER trg_school_feature_perm_version
    AFTER INSERT OR UPDATE OR DELETE ON school_feature
    FOR EACH ROW EXECUTE FUNCTION school_feature_bump_perm_version();

-- ---------------------------------------------------------------------------
-- 5. Users: category, email optional (WhatsApp-only parents)
-- ---------------------------------------------------------------------------
ALTER TABLE app_user
    ALTER COLUMN email DROP NOT NULL,
    ADD COLUMN user_category VARCHAR(20) NOT NULL DEFAULT 'STAFF'
        CHECK (user_category IN ('STAFF', 'NON_STAFF', 'PARENT', 'STUDENT'));

CREATE UNIQUE INDEX uq_app_user_email_ci ON app_user (lower(email)) WHERE email IS NOT NULL;

-- Users whose only roles are PARENT are parents
UPDATE app_user u SET user_category = 'PARENT'
WHERE EXISTS (SELECT 1 FROM user_school_staff_type g JOIN staff_type st ON st.staff_type_id = g.staff_type_id
              WHERE g.user_id = u.user_id AND st.type_code = 'PARENT')
  AND NOT EXISTS (SELECT 1 FROM user_school_staff_type g JOIN staff_type st ON st.staff_type_id = g.staff_type_id
                  WHERE g.user_id = u.user_id AND st.type_code <> 'PARENT');

-- ---------------------------------------------------------------------------
-- 6. Roles: built-in (school_id NULL) and custom per school
-- ---------------------------------------------------------------------------
ALTER TABLE staff_type DROP CONSTRAINT IF EXISTS staff_type_type_code_key;
ALTER TABLE staff_type
    ADD COLUMN school_id          BIGINT REFERENCES school (school_id) ON DELETE CASCADE,
    ADD COLUMN level              INTEGER NOT NULL DEFAULT 50 CHECK (level BETWEEN 1 AND 1000),
    ADD COLUMN description        TEXT,
    ADD COLUMN created_by_user_id BIGINT REFERENCES app_user (user_id),
    -- Not in ER v5: the built-in role whose app module (role-* package) a custom role reuses
    ADD COLUMN base_staff_type_id BIGINT REFERENCES staff_type (staff_type_id),
    ADD CONSTRAINT ck_custom_role_level CHECK (school_id IS NULL OR level BETWEEN 1 AND 99),
    ADD CONSTRAINT ck_custom_role_has_base CHECK (school_id IS NULL OR base_staff_type_id IS NOT NULL);

CREATE UNIQUE INDEX uq_staff_type_builtin_code ON staff_type (type_code) WHERE school_id IS NULL;
CREATE UNIQUE INDEX uq_staff_type_school_code ON staff_type (school_id, type_code) WHERE school_id IS NOT NULL;

-- A custom role's base must be a built-in role other than SUPER_USER
CREATE OR REPLACE FUNCTION enforce_staff_type_base() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_base_school BIGINT;
    v_base_code   VARCHAR(50);
BEGIN
    IF NEW.base_staff_type_id IS NOT NULL THEN
        SELECT school_id, type_code INTO v_base_school, v_base_code
        FROM staff_type WHERE staff_type_id = NEW.base_staff_type_id;
        IF v_base_school IS NOT NULL OR v_base_code = 'SUPER_USER' THEN
            RAISE EXCEPTION 'Role % must be based on a built-in branch role', NEW.type_code
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_staff_type_base
    BEFORE INSERT OR UPDATE OF base_staff_type_id ON staff_type
    FOR EACH ROW EXECUTE FUNCTION enforce_staff_type_base();

-- ---------------------------------------------------------------------------
-- 7. Permissions and role mappings (platform defaults + per-school mappings)
-- ---------------------------------------------------------------------------
CREATE TABLE permission (
    permission_id   BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(80)  NOT NULL UNIQUE,
    permission_name VARCHAR(150) NOT NULL,
    description     TEXT,
    feature_id      BIGINT REFERENCES feature (feature_id)  -- NULL for platform permissions (USER_MANAGE)
);

CREATE TABLE staff_type_feature (
    staff_type_feature_id BIGSERIAL PRIMARY KEY,
    school_id             BIGINT REFERENCES school (school_id) ON DELETE CASCADE, -- NULL = platform default
    staff_type_id         BIGINT NOT NULL REFERENCES staff_type (staff_type_id) ON DELETE CASCADE,
    feature_id            BIGINT NOT NULL REFERENCES feature (feature_id) ON DELETE CASCADE,
    CONSTRAINT uq_staff_type_feature UNIQUE NULLS NOT DISTINCT (school_id, staff_type_id, feature_id)
);

CREATE TABLE staff_type_permission (
    staff_type_permission_id BIGSERIAL PRIMARY KEY,
    school_id                BIGINT REFERENCES school (school_id) ON DELETE CASCADE, -- NULL = platform default
    staff_type_id            BIGINT NOT NULL REFERENCES staff_type (staff_type_id) ON DELETE CASCADE,
    permission_id            BIGINT NOT NULL REFERENCES permission (permission_id) ON DELETE CASCADE,
    CONSTRAINT uq_staff_type_permission UNIQUE NULLS NOT DISTINCT (school_id, staff_type_id, permission_id)
);

CREATE INDEX idx_stp_school_role ON staff_type_permission (school_id, staff_type_id);
CREATE INDEX idx_stf_school_role ON staff_type_feature (school_id, staff_type_id);

-- The school uses its own mapping (school_id rows) for this built-in role instead of the defaults
CREATE TABLE school_role_override (
    school_role_override_id BIGSERIAL PRIMARY KEY,
    school_id               BIGINT      NOT NULL REFERENCES school (school_id) ON DELETE CASCADE,
    staff_type_id           BIGINT      NOT NULL REFERENCES staff_type (staff_type_id) ON DELETE CASCADE,
    customized_by_user_id   BIGINT REFERENCES app_user (user_id),
    customized_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_school_role_override UNIQUE (school_id, staff_type_id)
);

-- Mapping rows must target a role visible in that school (built-in, or that school's custom role)
CREATE OR REPLACE FUNCTION enforce_mapping_role_scope() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_role_school BIGINT;
BEGIN
    SELECT school_id INTO v_role_school FROM staff_type WHERE staff_type_id = NEW.staff_type_id;
    IF v_role_school IS NOT NULL AND v_role_school IS DISTINCT FROM NEW.school_id THEN
        RAISE EXCEPTION 'Custom role % belongs to school %, not %', NEW.staff_type_id, v_role_school, NEW.school_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_stp_role_scope BEFORE INSERT OR UPDATE ON staff_type_permission
    FOR EACH ROW EXECUTE FUNCTION enforce_mapping_role_scope();
CREATE TRIGGER trg_stf_role_scope BEFORE INSERT OR UPDATE ON staff_type_feature
    FOR EACH ROW EXECUTE FUNCTION enforce_mapping_role_scope();
CREATE TRIGGER trg_sro_role_scope BEFORE INSERT OR UPDATE ON school_role_override
    FOR EACH ROW EXECUTE FUNCTION enforce_mapping_role_scope();

CREATE OR REPLACE FUNCTION mapping_bump_perm_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM bump_perm_version(CASE WHEN TG_OP = 'DELETE' THEN OLD.school_id ELSE NEW.school_id END);
    RETURN NULL;
END $$;

CREATE TRIGGER trg_stp_perm_version AFTER INSERT OR UPDATE OR DELETE ON staff_type_permission
    FOR EACH ROW EXECUTE FUNCTION mapping_bump_perm_version();
CREATE TRIGGER trg_stf_perm_version AFTER INSERT OR UPDATE OR DELETE ON staff_type_feature
    FOR EACH ROW EXECUTE FUNCTION mapping_bump_perm_version();
CREATE TRIGGER trg_sro_perm_version AFTER INSERT OR UPDATE OR DELETE ON school_role_override
    FOR EACH ROW EXECUTE FUNCTION mapping_bump_perm_version();

-- Effective mapping per branch: overridden built-ins and custom roles use school rows, the rest use defaults
CREATE VIEW effective_role_permission AS
SELECT s.school_id, st.staff_type_id, p.permission_id, p.permission_code, p.feature_id
FROM school s
JOIN staff_type st ON st.school_id IS NULL OR st.school_id = s.school_id
JOIN staff_type_permission m ON m.staff_type_id = st.staff_type_id
JOIN permission p ON p.permission_id = m.permission_id
WHERE (m.school_id = s.school_id
       AND (st.school_id IS NOT NULL
            OR EXISTS (SELECT 1 FROM school_role_override o WHERE o.school_id = s.school_id AND o.staff_type_id = st.staff_type_id)))
   OR (m.school_id IS NULL AND st.school_id IS NULL
       AND NOT EXISTS (SELECT 1 FROM school_role_override o WHERE o.school_id = s.school_id AND o.staff_type_id = st.staff_type_id));

CREATE VIEW effective_role_feature AS
SELECT s.school_id, st.staff_type_id, f.feature_id, f.feature_code
FROM school s
JOIN staff_type st ON st.school_id IS NULL OR st.school_id = s.school_id
JOIN staff_type_feature m ON m.staff_type_id = st.staff_type_id
JOIN feature f ON f.feature_id = m.feature_id
WHERE (m.school_id = s.school_id
       AND (st.school_id IS NOT NULL
            OR EXISTS (SELECT 1 FROM school_role_override o WHERE o.school_id = s.school_id AND o.staff_type_id = st.staff_type_id)))
   OR (m.school_id IS NULL AND st.school_id IS NULL
       AND NOT EXISTS (SELECT 1 FROM school_role_override o WHERE o.school_id = s.school_id AND o.staff_type_id = st.staff_type_id));

-- ---------------------------------------------------------------------------
-- 8. Role assignments: branch grants and org-wide members
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_branch_role_assignment() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_role_school BIGINT;
    v_code        VARCHAR(50);
BEGIN
    SELECT school_id, type_code INTO v_role_school, v_code FROM staff_type WHERE staff_type_id = NEW.staff_type_id;
    IF v_code = 'SUPER_USER' THEN
        RAISE EXCEPTION 'SUPER_USER cannot be assigned in a branch' USING ERRCODE = 'check_violation';
    END IF;
    IF v_role_school IS NOT NULL AND v_role_school <> NEW.school_id THEN
        RAISE EXCEPTION 'Custom role % belongs to another branch', v_code USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_usst_role_scope BEFORE INSERT OR UPDATE ON user_school_staff_type
    FOR EACH ROW EXECUTE FUNCTION enforce_branch_role_assignment();

CREATE TABLE organization_member (
    organization_member_id BIGSERIAL PRIMARY KEY,
    organization_id        BIGINT  NOT NULL REFERENCES organization (organization_id) ON DELETE CASCADE,
    user_id                BIGINT  NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    staff_type_id          BIGINT  NOT NULL REFERENCES staff_type (staff_type_id),
    assigned_date          DATE    NOT NULL DEFAULT CURRENT_DATE,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_organization_member UNIQUE (organization_id, user_id, staff_type_id)
);

-- Org-wide roles apply to every branch, so they must be built-in (custom roles are per branch)
CREATE OR REPLACE FUNCTION enforce_org_member_role() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM staff_type WHERE staff_type_id = NEW.staff_type_id
               AND (school_id IS NOT NULL OR type_code = 'SUPER_USER')) THEN
        RAISE EXCEPTION 'Org-wide roles must be built-in branch roles' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_org_member_role BEFORE INSERT OR UPDATE ON organization_member
    FOR EACH ROW EXECUTE FUNCTION enforce_org_member_role();

-- ---------------------------------------------------------------------------
-- 9. Invitations: branch or org-wide
-- ---------------------------------------------------------------------------
ALTER TABLE school_invitation
    ALTER COLUMN school_id DROP NOT NULL,
    ADD COLUMN organization_id BIGINT REFERENCES organization (organization_id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_invitation_target CHECK ((school_id IS NULL) <> (organization_id IS NULL));

CREATE INDEX idx_invitation_email ON school_invitation (lower(email)) WHERE NOT is_used;

-- ---------------------------------------------------------------------------
-- 10. Branch provisioning (onboarding wizard: org + N branches in one batch)
-- ---------------------------------------------------------------------------
CREATE TABLE branch_provisioning (
    provisioning_id      BIGSERIAL PRIMARY KEY,
    batch_id             UUID        NOT NULL,
    organization_id      BIGINT      NOT NULL REFERENCES organization (organization_id) ON DELETE CASCADE,
    school_id            BIGINT,
    template_school_id   BIGINT,
    copy_features        BOOLEAN     NOT NULL DEFAULT TRUE,
    copy_custom_roles    BOOLEAN     NOT NULL DEFAULT FALSE,
    copy_role_mappings   BOOLEAN     NOT NULL DEFAULT FALSE,
    payload              JSONB       NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PROVISIONING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    error_message        TEXT,
    requested_by_user_id BIGINT REFERENCES app_user (user_id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    -- Created and template branches must belong to the same organization
    CONSTRAINT fk_bp_school_same_org FOREIGN KEY (organization_id, school_id)
        REFERENCES school (organization_id, school_id),
    CONSTRAINT fk_bp_template_same_org FOREIGN KEY (organization_id, template_school_id)
        REFERENCES school (organization_id, school_id)
);

CREATE INDEX idx_branch_provisioning_batch ON branch_provisioning (batch_id);

-- ---------------------------------------------------------------------------
-- 11. Feature request backlog (main page)
-- ---------------------------------------------------------------------------
CREATE TABLE feature_request (
    feature_request_id     BIGSERIAL PRIMARY KEY,
    title                  VARCHAR(200) NOT NULL,
    description            TEXT,
    category               VARCHAR(20)  NOT NULL DEFAULT 'NEW_FEATURE'
        CHECK (category IN ('NEW_FEATURE', 'ENHANCEMENT', 'INTEGRATION', 'UX', 'BUG')),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'UNDER_REVIEW', 'PLANNED', 'IN_PROGRESS', 'SHIPPED', 'DECLINED', 'DUPLICATE')),
    priority               VARCHAR(2) CHECK (priority IN ('P0', 'P1', 'P2', 'P3')),
    source                 VARCHAR(20)  NOT NULL DEFAULT 'TENANT'
        CHECK (source IN ('TENANT', 'SUPER_USER', 'SALES', 'SUPPORT')),
    institution_type_id    BIGINT REFERENCES institution_type (institution_type_id),
    feature_id             BIGINT REFERENCES feature (feature_id),
    requested_by_user_id   BIGINT REFERENCES app_user (user_id),
    requested_by_school_id BIGINT REFERENCES school (school_id) ON DELETE SET NULL,
    duplicate_of_id        BIGINT REFERENCES feature_request (feature_request_id),
    target_release         VARCHAR(30),
    vote_count             INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    shipped_at             TIMESTAMPTZ,
    CONSTRAINT ck_fr_duplicate CHECK ((status = 'DUPLICATE') = (duplicate_of_id IS NOT NULL)),
    CONSTRAINT ck_fr_not_self_duplicate CHECK (duplicate_of_id IS DISTINCT FROM feature_request_id)
);

CREATE INDEX idx_feature_request_status_votes ON feature_request (status, vote_count DESC);

CREATE TABLE feature_request_vote (
    feature_request_id BIGINT      NOT NULL REFERENCES feature_request (feature_request_id) ON DELETE CASCADE,
    user_id            BIGINT      NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    school_id          BIGINT REFERENCES school (school_id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (feature_request_id, user_id)
);

CREATE TABLE feature_request_comment (
    comment_id         BIGSERIAL PRIMARY KEY,
    feature_request_id BIGINT      NOT NULL REFERENCES feature_request (feature_request_id) ON DELETE CASCADE,
    author_user_id     BIGINT      NOT NULL REFERENCES app_user (user_id),
    body               TEXT        NOT NULL,
    is_internal        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE feature_request_status_history (
    history_id         BIGSERIAL PRIMARY KEY,
    feature_request_id BIGINT      NOT NULL REFERENCES feature_request (feature_request_id) ON DELETE CASCADE,
    from_status        VARCHAR(20),
    to_status          VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT REFERENCES app_user (user_id),
    note               TEXT,
    changed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION feature_request_vote_count() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE feature_request
    SET vote_count = (SELECT count(*) FROM feature_request_vote v
                      WHERE v.feature_request_id = feature_request.feature_request_id)
    WHERE feature_request_id = CASE WHEN TG_OP = 'DELETE' THEN OLD.feature_request_id ELSE NEW.feature_request_id END;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_fr_vote_count AFTER INSERT OR DELETE ON feature_request_vote
    FOR EACH ROW EXECUTE FUNCTION feature_request_vote_count();

-- changed_by / note come from transaction settings: SET LOCAL app.user_id / app.status_note
CREATE OR REPLACE FUNCTION log_feature_request_status() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO feature_request_status_history (feature_request_id, from_status, to_status, changed_by_user_id, note)
        VALUES (NEW.feature_request_id,
                CASE WHEN TG_OP = 'UPDATE' THEN OLD.status END,
                NEW.status,
                nullif(current_setting('app.user_id', TRUE), '')::BIGINT,
                nullif(current_setting('app.status_note', TRUE), ''));
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_fr_status_history AFTER INSERT OR UPDATE OF status ON feature_request
    FOR EACH ROW EXECUTE FUNCTION log_feature_request_status();
