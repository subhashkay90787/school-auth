-- =============================================================================
-- Default roles available in every school.
-- Roles are platform-wide master data; a user's role in a specific school is
-- recorded in user_school_staff_type (user_id, school_id, staff_type_id).
-- =============================================================================
INSERT INTO staff_type (type_code, type_name) VALUES
    ('SYS_ADMIN', 'System Administrator'),
    ('PRINCIPAL', 'Principal'),
    ('ADMIN', 'Administrator'),
    ('TEACHER', 'Teacher'),
    ('STAFF', 'Staff'),
    ('PARENT', 'Parent')
ON CONFLICT (type_code) DO NOTHING;
