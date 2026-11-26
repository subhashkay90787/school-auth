#!/usr/bin/env python3
"""Generates school-auth V6 seed (features, roles, permissions, default mappings) from the
Role-based UX v2 access matrix (section 6), extended with LIBRARY, MESS, SECURITY, RECORDS."""
import sys

# feature_code, name, description, parent module (permission group) or None
FEATURES = [
    ("ATTENDANCE", "Student attendance", "Registers, lecture attendance, day-scholar leave", None),
    ("STAFF_PRESENCE", "Staff presence", "Punches, shifts, daily presence, corrections", None),
    ("ACADEMICS", "Academics", "Programs, sections, subjects, marks and results", None),
    ("FEE_MANAGEMENT", "Fees", "Fee structures, demands, payments, concessions, refunds", None),
    ("HOSTEL", "Hostel", "Rooms, allocation, roll call, outings, inspections", None),
    ("CUSTODY", "Custody", "Phone and valuables custody with chain of custody", None),
    ("GATE", "Gate & visitors", "Gate passes, visitors, pickup people, parcels", None),
    ("INVENTORY", "Inventory & procurement", "Stock, assets, requisitions, POs, GRN", None),
    ("EXPENSES", "Expenses & budgets", "Expenses, petty cash, budgets, other income", None),
    ("HR_PAYROLL", "HR, leave & payroll", "Employee profiles, leave, rosters, payroll", None),
    ("COMMUNICATION", "Communication", "Notifications, conversations, polls, PTM", None),
    ("HEALTH", "Health", "Medical profiles, infirmary, medication", None),
    ("ADMISSIONS", "Admissions", "Enquiries, applications, assessments, seat plans", None),
    ("TRANSPORT", "Transport", "Routes, trips, boarding, live tracking (phase 2)", None),
    ("LIBRARY", "Library", "Catalog, copies, borrowing, fines", None),
    ("MESS", "Mess", "Menus, subscriptions, meal counts, rebates", None),
    ("SECURITY", "Security", "Posts, patrols, checkpoints, occurrences, keys", None),
    ("RECORDS", "Records & certificates", "Documents, certificates, no-dues clearance, ID cards", None),
    # entitlement-only features, gated by their parent module's permissions
    ("EXAMS", "Exams", "Exam sessions, hall tickets, seating, invigilation", "ACADEMICS"),
    ("TIMETABLE", "Timetable", "Periods, timetable versions, substitutions", "ACADEMICS"),
    ("HOMEWORK", "Homework", "Homework and submissions", "ACADEMICS"),
    ("LESSON_PLANS", "Lesson plans & syllabus", "Syllabus units, lesson plans, coverage", "ACADEMICS"),
    ("ONLINE_QUIZ", "Quizzes & content", "Question bank, quizzes, learning content", "ACADEMICS"),
    ("REPORT_CARDS", "Report cards", "Templates, co-scholastic grades, publishing", "ACADEMICS"),
    ("COURSE_REGISTRATION", "Course registration", "Electives and course offerings (college/university)", "ACADEMICS"),
    ("BATCH_MANAGEMENT", "Batch management", "Course batches (institutes, tuition centres)", "ACADEMICS"),
    ("PLACEMENTS", "Placements", "Companies, drives, applications", "ACADEMICS"),
    ("ALUMNI", "Alumni", "Alumni profiles", "RECORDS"),
    ("DISCIPLINE", "Discipline", "Incidents and actions", "HOSTEL"),
    ("FACILITY", "Facility tickets", "Maintenance tickets", "INVENTORY"),
    ("STORE_WALLET", "Store & wallet", "Student wallet and campus store", "FEE_MANAGEMENT"),
    ("NEWS_FEED", "News feed", "School news posts (30 days)", "COMMUNICATION"),
    ("CALENDAR", "Calendar", "Holidays, events, PTM days", "COMMUNICATION"),
    ("PTM", "Parent-teacher meetings", "PTM sessions and slot booking", "COMMUNICATION"),
    ("REPORTS", "Reports & exports", "Saved reports and statutory exports", None),
    ("BULK_IMPORT", "Bulk import", "Spreadsheet imports", None),
    ("CONSENT", "Consent forms", "Consent forms and records (always on)", None),
    ("DEDICATED_APP", "Dedicated app (premium)", "Organization-branded mobile app", None),
    ("ASSISTANT_MCP", "Voice & AI assistants", "Read-only MCP access for assistants", None),
]

# Features every role gets when the branch has them
UNIVERSAL_FEATURES = ["COMMUNICATION", "NEWS_FEED", "CALENDAR", "CONSENT", "ASSISTANT_MCP"]

# type_code, name, level, description
ROLES = [
    ("SUPER_USER", "Super User", 1000, "Platform owner; never assigned in a branch"),
    ("SCHOOL_ADMIN", "School Admin", 100, "Office head: setup, records, imports"),
    ("SYS_ADMIN", "System Administrator (legacy)", 100, "Legacy alias of SCHOOL_ADMIN"),
    ("ADMIN", "Administrator", 80, "Management / correspondent / trustee"),
    ("PRINCIPAL", "Principal", 80, "Runs the branch day to day"),
    ("ACCOUNTANT", "Accountant", 60, "Fee counter and books"),
    ("HR_MANAGER", "HR Manager", 60, "People, leave, rosters, payroll"),
    ("ADMISSION_OFFICER", "Admission Officer", 60, "Enquiry and admission pipeline"),
    ("CHIEF_WARDEN", "Chief Warden", 60, "Hostel head"),
    ("SECURITY_SUPERVISOR", "Security Supervisor", 60, "Guard rosters, patrols, occurrences"),
    ("TRANSPORT_MANAGER", "Transport Manager", 60, "Routes, vehicles, drivers (phase 2)"),
    ("TEACHER", "Teacher", 50, "Teaching staff; duties add class teacher, invigilator, escort"),
    ("STAFF", "Staff", 50, "Librarian and general staff"),
    ("RECEPTIONIST", "Receptionist", 50, "Front office"),
    ("WARDEN", "Warden", 50, "Room / block / night / assistant warden duties"),
    ("MATRON", "Matron", 50, "Sick bay, medication, laundry"),
    ("NURSE", "Nurse", 50, "Infirmary"),
    ("MESS_MANAGER", "Mess Manager", 50, "Menus, meal counts, provisions"),
    ("SECURITY", "Security Guard", 50, "Gate, patrol, welfare checks"),
    ("STORE_KEEPER", "Store Keeper", 50, "Stock, GRN, issues, audits"),
    ("DRIVER", "Driver", 50, "Trips and boarding (phase 2)"),
    ("PARENT", "Parent", 10, "Guardian of one or more students"),
    ("STUDENT", "Student", 10, "Adult / college students only"),
]

COLS = "SU ADM PRI SA ACC HR ADMN RCP TCH PAR STU CW WAR MAT NUR MESS SEC SSUP STF STK TM DRV".split()
COL_ROLE = dict(zip(COLS, ["SUPER_USER", "ADMIN", "PRINCIPAL", "SCHOOL_ADMIN", "ACCOUNTANT", "HR_MANAGER",
                           "ADMISSION_OFFICER", "RECEPTIONIST", "TEACHER", "PARENT", "STUDENT", "CHIEF_WARDEN",
                           "WARDEN", "MATRON", "NURSE", "MESS_MANAGER", "SECURITY", "SECURITY_SUPERVISOR",
                           "STAFF", "STORE_KEEPER", "TRANSPORT_MANAGER", "DRIVER"]))

# UX v2 section 6 (rows copied in column order); last four rows are additions
MATRIX = """
ATTENDANCE      - R R F - - - - S S O R - - - - - - - - - -
STAFF_PRESENCE  - R F F - F - - O - - R O O O O O R O O O O
ACADEMICS       - R F F - - - - S S O - - - - - - - - - - -
FEE_MANAGEMENT  - A A R F - R - - S O - - - - - - - - - - -
HOSTEL          - R A R - - - - - S O F S S R R S R - - - -
CUSTODY         - - R - - - - - - S O F S - - - R - S - - -
GATE            - - R R - - - F - S - R R - - - S F - - - -
INVENTORY       - A A R R - - - - - - R - - - S - - S F - -
EXPENSES        - A A - F - - - - - - - - - - - - - - - R -
HR_PAYROLL      - A A R S F - - O - - A O O O O O A O O O O
COMMUNICATION   - F F F - - S - S S O S S - - - - - - - - -
HEALTH          - - R - - - - - R S O R R F F - - - - - - -
ADMISSIONS      - R R F - - F S - - - - - - - - - - - - - -
TRANSPORT       - R R R - - - - - S O - - - - - - - - - F S
LIBRARY         - - R R - - - - O S O - - - - - - - F - - -
MESS            - - R - R - - - - S O R R R - F - - - S - -
SECURITY        - - R R - - - - - - - R R - - - S F - - - -
RECORDS         - R A F - - S - - S O - - - - - - - - - - -
"""

ACTIONS = {
    "VIEW": "View full detail",
    "MANAGE": "Create and change",
    "APPROVE": "Approve requests",
    "REPORT": "Read aggregates and reports",
    "SCOPED": "View and act within own scope (own class, rooms, post, children)",
    "SELF": "Own records only",
}
LETTER_ACTIONS = {
    "F": ["VIEW", "MANAGE", "REPORT"],
    "A": ["VIEW", "APPROVE", "REPORT"],
    "S": ["SCOPED"],
    "R": ["REPORT"],
    "O": ["SELF"],
}

# Platform permissions (feature_id NULL)
PLATFORM_PERMS = [
    ("USER_MANAGE", "Invite and remove members", "Invite or remove members whose role level is below yours"),
    ("PARENT_INVITE", "Invite parents", "Invite PARENT and STUDENT accounts"),
    ("ROLE_VIEW", "View roles", "See roles and their permissions"),
    ("ROLE_MANAGE", "Manage roles", "Create custom roles and change role permissions"),
    ("CONSENT_FORM_MANAGE", "Manage consent forms", "Publish consent form versions"),
    ("SESSION_MONITOR", "Monitor sessions", "See and end branch sessions; failed sign-ins"),
    ("FEATURE_REQUEST_RAISE", "Raise feature requests", "Raise, vote and comment on the backlog"),
]
PLATFORM_GRANTS = {
    "USER_MANAGE": ["SCHOOL_ADMIN", "SYS_ADMIN", "ADMIN", "PRINCIPAL", "HR_MANAGER"],
    "PARENT_INVITE": ["SCHOOL_ADMIN", "SYS_ADMIN", "ADMIN", "PRINCIPAL", "TEACHER", "ADMISSION_OFFICER"],
    "ROLE_VIEW": ["SCHOOL_ADMIN", "SYS_ADMIN", "ADMIN", "PRINCIPAL"],
    "ROLE_MANAGE": ["ADMIN", "PRINCIPAL"],
    "CONSENT_FORM_MANAGE": ["ADMIN", "PRINCIPAL"],
    "SESSION_MONITOR": ["SCHOOL_ADMIN", "SYS_ADMIN", "ADMIN", "PRINCIPAL"],
    "FEATURE_REQUEST_RAISE": ["SCHOOL_ADMIN", "SYS_ADMIN", "ADMIN", "PRINCIPAL"],
}

# institution type -> default features in the onboarding wizard
ALL_CODES = [f[0] for f in FEATURES]
NOT_DEFAULT = {"DEDICATED_APP", "COURSE_REGISTRATION", "BATCH_MANAGEMENT", "PLACEMENTS", "TRANSPORT"}
TYPE_FEATURES = {
    "SCHOOL": [c for c in ALL_CODES if c not in NOT_DEFAULT],
    "COLLEGE": [c for c in ALL_CODES if c not in NOT_DEFAULT | {"REPORT_CARDS", "PTM", "CUSTODY"}]
               + ["COURSE_REGISTRATION", "PLACEMENTS"],
    "UNIVERSITY": [c for c in ALL_CODES if c not in NOT_DEFAULT | {"REPORT_CARDS", "PTM", "CUSTODY"}]
                  + ["COURSE_REGISTRATION", "PLACEMENTS"],
    "INSTITUTE": ["ATTENDANCE", "STAFF_PRESENCE", "ACADEMICS", "EXAMS", "TIMETABLE", "HOMEWORK", "ONLINE_QUIZ",
                  "FEE_MANAGEMENT", "COMMUNICATION", "NEWS_FEED", "CALENDAR", "ADMISSIONS", "RECORDS",
                  "HR_PAYROLL", "EXPENSES", "BATCH_MANAGEMENT", "PLACEMENTS", "REPORTS", "BULK_IMPORT", "CONSENT",
                  "ASSISTANT_MCP"],
    "TUITION_CENTRE": ["ATTENDANCE", "ACADEMICS", "EXAMS", "TIMETABLE", "HOMEWORK", "ONLINE_QUIZ",
                       "FEE_MANAGEMENT", "COMMUNICATION", "NEWS_FEED", "CALENDAR", "PTM", "ADMISSIONS",
                       "BATCH_MANAGEMENT", "REPORTS", "BULK_IMPORT", "CONSENT", "ASSISTANT_MCP"],
}


def q(s):
    return "NULL" if s is None else "'" + s.replace("'", "''") + "'"


def main(out):
    w = out.write
    w("-- =============================================================================\n")
    w("-- school-auth V6 — seed catalog (generated by tools/gen_auth_seed.py; edit the script, not this file)\n")
    w("--   %d features, %d built-in roles, %d permissions, default role->permission and role->feature\n"
      % (len(FEATURES), len(ROLES), len(PLATFORM_PERMS) + 18 * len(ACTIONS)))
    w("--   mappings from the Role-based UX v2 access matrix (section 6).\n")
    w("-- =============================================================================\n\n")

    w("INSERT INTO feature (feature_code, feature_name, description) VALUES\n")
    w(",\n".join("    (%s, %s, %s)" % (q(c), q(n), q(d)) for c, n, d, _ in FEATURES))
    w("\nON CONFLICT (feature_code) DO UPDATE SET feature_name = EXCLUDED.feature_name, description = EXCLUDED.description;\n\n")

    w("INSERT INTO staff_type (type_code, type_name, level, description) VALUES\n")
    w(",\n".join("    (%s, %s, %d, %s)" % (q(c), q(n), l, q(d)) for c, n, l, d in ROLES))
    w("\nON CONFLICT (type_code) WHERE school_id IS NULL\n")
    w("DO UPDATE SET type_name = EXCLUDED.type_name, level = EXCLUDED.level, description = EXCLUDED.description;\n\n")
    w("UPDATE staff_type SET base_staff_type_id = (SELECT staff_type_id FROM staff_type WHERE type_code = 'SCHOOL_ADMIN' AND school_id IS NULL)\n")
    w("WHERE type_code = 'SYS_ADMIN' AND school_id IS NULL;\n\n")

    modules = []
    for line in MATRIX.strip().splitlines():
        parts = line.split()
        module, letters = parts[0], parts[1:]
        assert len(letters) == len(COLS), (module, len(letters))
        modules.append((module, letters))

    w("INSERT INTO permission (permission_code, permission_name, description, feature_id)\n")
    w("SELECT v.code, v.name, v.description, f.feature_id\nFROM (VALUES\n")
    rows = []
    fname = {c: n for c, n, _, _ in FEATURES}
    for module, _ in modules:
        for act, desc in ACTIONS.items():
            rows.append("    (%s, %s, %s, %s)" % (q(module + "_" + act), q(fname[module] + ": " + desc.lower()),
                                                q(desc), q(module)))
    w(",\n".join(rows))
    w("\n) AS v(code, name, description, feature_code)\nJOIN feature f ON f.feature_code = v.feature_code\n")
    w("ON CONFLICT (permission_code) DO NOTHING;\n\n")

    w("INSERT INTO permission (permission_code, permission_name, description) VALUES\n")
    w(",\n".join("    (%s, %s, %s)" % (q(c), q(n), q(d)) for c, n, d in PLATFORM_PERMS))
    w("\nON CONFLICT (permission_code) DO NOTHING;\n\n")

    grants = []
    for module, letters in modules:
        for col, letter in zip(COLS, letters):
            if letter == "-" or col == "SU":
                continue
            for act in LETTER_ACTIONS[letter]:
                grants.append((COL_ROLE[col], module + "_" + act))
    for perm, roles in PLATFORM_GRANTS.items():
        for r in roles:
            grants.append((r, perm))
    # SYS_ADMIN (legacy) gets exactly what SCHOOL_ADMIN gets
    grants += [("SYS_ADMIN", p) for r, p in list(grants) if r == "SCHOOL_ADMIN"]
    grants = sorted(set(grants))

    w("-- Default role -> permission (school_id NULL = platform default)\n")
    w("INSERT INTO staff_type_permission (school_id, staff_type_id, permission_id)\n")
    w("SELECT NULL, st.staff_type_id, p.permission_id\nFROM (VALUES\n")
    w(",\n".join("    (%s, %s)" % (q(r), q(p)) for r, p in grants))
    w("\n) AS v(role_code, permission_code)\n")
    w("JOIN staff_type st ON st.type_code = v.role_code AND st.school_id IS NULL\n")
    w("JOIN permission p ON p.permission_code = v.permission_code\n")
    w("ON CONFLICT ON CONSTRAINT uq_staff_type_permission DO NOTHING;\n\n")

    w("-- Default role -> feature: a role sees a module's feature (and its sub-features) when it holds any\n")
    w("-- permission in that module; universal features go to every branch role.\n")
    w("INSERT INTO staff_type_feature (school_id, staff_type_id, feature_id)\n")
    w("SELECT DISTINCT NULL::BIGINT, m.staff_type_id, f.feature_id\n")
    w("FROM staff_type_permission m\nJOIN permission p ON p.permission_id = m.permission_id\n")
    w("JOIN feature pf ON pf.feature_id = p.feature_id\n")
    w("JOIN (VALUES\n")
    pairs = [(c, c) for c, _, _, parent in FEATURES if parent is None] + \
            [(parent, c) for c, _, _, parent in FEATURES if parent is not None]
    w(",\n".join("    (%s, %s)" % (q(a), q(b)) for a, b in pairs))
    w("\n) AS g(module_code, feature_code) ON g.module_code = pf.feature_code\n")
    w("JOIN feature f ON f.feature_code = g.feature_code\nWHERE m.school_id IS NULL\n")
    w("ON CONFLICT ON CONSTRAINT uq_staff_type_feature DO NOTHING;\n\n")
    w("INSERT INTO staff_type_feature (school_id, staff_type_id, feature_id)\n")
    w("SELECT NULL, st.staff_type_id, f.feature_id\nFROM staff_type st\nJOIN feature f ON f.feature_code IN (%s)\n"
      % ", ".join(q(c) for c in UNIVERSAL_FEATURES))
    w("WHERE st.school_id IS NULL AND st.type_code <> 'SUPER_USER'\n")
    w("ON CONFLICT ON CONSTRAINT uq_staff_type_feature DO NOTHING;\n")
    w("INSERT INTO staff_type_feature (school_id, staff_type_id, feature_id)\n")
    w("SELECT NULL, st.staff_type_id, f.feature_id\nFROM staff_type st\nJOIN feature f ON f.feature_code IN ('REPORTS', 'BULK_IMPORT')\n")
    w("WHERE st.school_id IS NULL AND st.type_code IN ('SCHOOL_ADMIN', 'SYS_ADMIN', 'ADMIN', 'PRINCIPAL', 'ACCOUNTANT', 'HR_MANAGER')\n")
    w("ON CONFLICT ON CONSTRAINT uq_staff_type_feature DO NOTHING;\n\n")

    w("-- Onboarding wizard defaults per institution type\n")
    w("INSERT INTO institution_type_feature (institution_type_id, feature_id)\n")
    w("SELECT it.institution_type_id, f.feature_id\nFROM (VALUES\n")
    tf = [(t, c) for t, codes in TYPE_FEATURES.items() for c in sorted(set(codes))]
    w(",\n".join("    (%s, %s)" % (q(t), q(c)) for t, c in tf))
    w("\n) AS v(type_code, feature_code)\nJOIN institution_type it ON it.type_code = v.type_code\n")
    w("JOIN feature f ON f.feature_code = v.feature_code\nON CONFLICT DO NOTHING;\n\n")

    w("-- CONSENT is always on: entitle and enable it for existing organizations and branches\n")
    w("INSERT INTO organization_feature (organization_id, feature_id)\n")
    w("SELECT o.organization_id, f.feature_id FROM organization o JOIN feature f ON f.feature_code = 'CONSENT'\n")
    w("ON CONFLICT DO NOTHING;\n")
    w("INSERT INTO school_feature (school_id, feature_id)\n")
    w("SELECT s.school_id, f.feature_id FROM school s JOIN feature f ON f.feature_code = 'CONSENT'\n")
    w("ON CONFLICT DO NOTHING;\n")
    print("grants:", len(grants), "type features:", len(tf), file=sys.stderr)


if __name__ == "__main__":
    main(sys.stdout)
