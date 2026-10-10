-- =============================================================================
-- IDENTITY & MULTI-TENANT SCHEMA (PostgreSQL DDL)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS identity;

-- Master Table for Schools (Multi-Tenant Locations)
CREATE TABLE identity.SCHOOL (
                                 school_id SERIAL PRIMARY KEY,
                                 school_code VARCHAR(50) UNIQUE NOT NULL,
                                 school_name VARCHAR(150) NOT NULL,
                                 host_url VARCHAR(255) NOT NULL,
                                 city VARCHAR(100) NOT NULL,
                                 state VARCHAR(100) NOT NULL,
                                 country VARCHAR(100) NOT NULL,
                                 status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Master Table for Platform Features
CREATE TABLE identity.FEATURE (
                                  feature_id SERIAL PRIMARY KEY,
                                  feature_code VARCHAR(50) UNIQUE NOT NULL,
                                  feature_name VARCHAR(100) NOT NULL,
                                  description TEXT,
                                  is_active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Many-to-Many Mapping: Features enabled per School
CREATE TABLE identity.SCHOOL_FEATURE (
                                         school_id INT NOT NULL,
                                         feature_id INT NOT NULL,
                                         enabled_date DATE DEFAULT CURRENT_DATE NOT NULL,
                                         is_enabled BOOLEAN DEFAULT TRUE NOT NULL,
                                         PRIMARY KEY (school_id, feature_id),
                                         CONSTRAINT fk_sf_school FOREIGN KEY (school_id) REFERENCES identity.SCHOOL(school_id) ON DELETE CASCADE,
                                         CONSTRAINT fk_sf_feature FOREIGN KEY (feature_id) REFERENCES identity.FEATURE(feature_id) ON DELETE CASCADE
);

-- Master User Entity (Supports Phone OTP, Email & Social Logins)
CREATE TABLE identity."USER" (
                                 user_id SERIAL PRIMARY KEY,
                                 first_name VARCHAR(50) NOT NULL,
                                 last_name VARCHAR(50) NOT NULL,
                                 phone_number VARCHAR(20) UNIQUE NOT NULL,
                                 alternate_phone_number VARCHAR(20),
                                 email VARCHAR(100) UNIQUE NOT NULL,
                                 is_active_flag BOOLEAN DEFAULT TRUE NOT NULL
);

CREATE INDEX idx_user_phone_number ON identity."USER"(phone_number);
CREATE INDEX idx_user_alternate_phone_number ON identity."USER"(alternate_phone_number);
CREATE INDEX idx_user_email ON identity."USER"(email);

-- Master Table for Staff Types / Platform Roles
CREATE TABLE identity.STAFF_TYPE (
                                     staff_type_id SERIAL PRIMARY KEY,
                                     type_code VARCHAR(50) UNIQUE NOT NULL, -- e.g., TEACHER, PRINCIPAL, ADMIN, SCHOOL_ADMIN, PARENT, STAFF, LIBRARIAN
                                     type_name VARCHAR(100) NOT NULL,
                                     is_active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Junction Table: Multi-School & Multi-Role Assignments per User
CREATE TABLE identity.USER_SCHOOL_STAFF_TYPE (
                                                 user_school_staff_id SERIAL PRIMARY KEY,
                                                 user_id INT NOT NULL,
                                                 school_id INT NOT NULL,
                                                 staff_type_id INT NOT NULL,
                                                 assigned_date DATE DEFAULT CURRENT_DATE NOT NULL,
                                                 is_active BOOLEAN DEFAULT TRUE NOT NULL,
                                                 CONSTRAINT fk_usst_user FOREIGN KEY (user_id) REFERENCES identity."USER"(user_id) ON DELETE CASCADE,
                                                 CONSTRAINT fk_usst_school FOREIGN KEY (school_id) REFERENCES identity.SCHOOL(school_id) ON DELETE CASCADE,
                                                 CONSTRAINT fk_usst_staff_type FOREIGN KEY (staff_type_id) REFERENCES identity.STAFF_TYPE(staff_type_id) ON DELETE CASCADE,
                                                 CONSTRAINT uq_user_school_role UNIQUE (user_id, school_id, staff_type_id)
);