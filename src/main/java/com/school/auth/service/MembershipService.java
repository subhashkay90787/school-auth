package com.school.auth.service;

import com.school.auth.dto.MemberDto.*;
import com.school.auth.entity.*;
import com.school.auth.otp.OtpService;
import com.school.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Who belongs where: invitations into a branch or org-wide, onboarding (accepting an invitation), member
 * lists and removal.
 *
 * <p>Rules: inside a branch, a member with USER_MANAGE may invite/remove roles below their own level, and
 * PARENT_INVITE allows parents and adult students. The super user may only invite a branch's or
 * organization's first admins (SCHOOL_ADMIN, PRINCIPAL, ADMIN): onboarding, not running a school.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    public static final Set<String> SETUP_ROLES = Set.of("SCHOOL_ADMIN", "SYS_ADMIN", "PRINCIPAL", "ADMIN");

    private final SchoolInvitationRepository invitationRepository;
    private final SchoolRepository schoolRepository;
    private final OrganizationRepository organizationRepository;
    private final StaffTypeRepository staffTypeRepository;
    private final UserRepository userRepository;
    private final UserSchoolStaffTypeRepository grantRepository;
    private final OrganizationMemberRepository orgMemberRepository;
    private final SchoolAccessService access;
    private final OtpService otpService;
    private final JdbcClient jdbc;

    @Value("${app.frontend.base-url:http://localhost:8080}")
    private String frontendBaseUrl;

    // ------------------------------------------------------------------ invitations

    @Transactional
    public InvitationResponse inviteToSchool(Long actorUserId, boolean actorIsSuperUser, Long schoolId,
                                             String email, String roleCode) {
        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new NoSuchElementException("School not found: " + schoolId));
        StaffTypeEntity role = staffTypeRepository.findForSchool(schoolId, roleCode)
                .filter(StaffTypeEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role code: " + roleCode));
        if (StaffTypeEntity.SUPER_USER.equals(role.getTypeCode())) {
            throw new IllegalArgumentException("SUPER_USER cannot be assigned in a branch.");
        }
        if (actorIsSuperUser) {
            if (!SETUP_ROLES.contains(role.getTypeCode())) {
                throw new SecurityException("The super user can only invite a branch's admins (" + SETUP_ROLES + ").");
            }
        } else {
            if (!school.isActive()) {
                throw new IllegalStateException("This school is not active.");
            }
            if (!access.canManage(actorUserId, schoolId, role)) {
                throw new SecurityException("You do not have permission to invite a " + role.getTypeCode() + " to this school.");
            }
        }
        SchoolInvitation invitation = invitationRepository.save(SchoolInvitation.builder()
                .school(school)
                .email(OtpService.normalizeEmail(email))
                .targetRoleCode(role.getTypeCode())
                .invitedByUserId(actorUserId)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());
        return new InvitationResponse(invitation.getEmail(), role.getTypeCode(), school.getSchoolName(),
                link(invitation), "Invitation created.");
    }

    @Transactional
    public InvitationResponse inviteToOrganization(Long actorUserId, boolean actorIsSuperUser, Long organizationId,
                                                   String email, String roleCode) {
        OrganizationEntity org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NoSuchElementException("Organization not found: " + organizationId));
        StaffTypeEntity role = staffTypeRepository.findBuiltIn(roleCode.toUpperCase())
                .filter(StaffTypeEntity::isActive)
                .filter(r -> !StaffTypeEntity.SUPER_USER.equals(r.getTypeCode()))
                .orElseThrow(() -> new IllegalArgumentException("Org-wide roles must be built-in branch roles: " + roleCode));
        if (actorIsSuperUser) {
            if (!SETUP_ROLES.contains(role.getTypeCode())) {
                throw new SecurityException("The super user can only invite an organization's admins (" + SETUP_ROLES + ").");
            }
        } else {
            int actorLevel = orgMemberRepository.findActive(organizationId, actorUserId).stream()
                    .mapToInt(m -> m.getStaffType().getLevel()).max().orElse(0);
            boolean actorIsOrgAdmin = orgMemberRepository.findActive(organizationId, actorUserId).stream()
                    .anyMatch(m -> SETUP_ROLES.contains(m.getStaffType().getTypeCode()));
            if (!actorIsOrgAdmin || actorLevel <= role.getLevel()) {
                throw new SecurityException("Only an org-wide admin with a higher role can invite an org-wide " + role.getTypeCode() + ".");
            }
        }
        SchoolInvitation invitation = invitationRepository.save(SchoolInvitation.builder()
                .organization(org)
                .email(OtpService.normalizeEmail(email))
                .targetRoleCode(role.getTypeCode())
                .invitedByUserId(actorUserId)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());
        return new InvitationResponse(invitation.getEmail(), role.getTypeCode(), org.getOrgName() + " (all branches)",
                link(invitation), "Invitation created.");
    }

    @Transactional(readOnly = true)
    public InvitationDetailsResponse validate(String token) {
        SchoolInvitation invitation = usableInvitation(token);
        return new InvitationDetailsResponse(invitation.getEmail(), invitation.getTargetRoleCode(),
                invitation.targetName(), invitation.isOrgWide());
    }

    /** Accept an invitation: verify the phone by OTP, find or create the user, grant the role. */
    @Transactional
    public UserEntity completeOnboarding(String token, String firstName, String lastName, String rawPhone, String otp) {
        SchoolInvitation invitation = usableInvitation(token);

        String verifiedPhone = OtpService.normalizePhone(rawPhone);
        if (!otpService.verifyOtp(verifiedPhone, otp)) {
            throw new IllegalArgumentException("Invalid or expired OTP for the phone number.");
        }
        userRepository.findByPhoneNumberOrAlternate(verifiedPhone)
                .filter(existing -> existing.getEmail() == null || !existing.getEmail().equalsIgnoreCase(invitation.getEmail()))
                .ifPresent(existing -> {
                    throw new IllegalStateException("This phone number is already registered to another account.");
                });

        StaffTypeEntity role = invitation.isOrgWide()
                ? staffTypeRepository.findBuiltIn(invitation.getTargetRoleCode()).orElse(null)
                : staffTypeRepository.findForSchool(invitation.getSchool().getSchoolId(), invitation.getTargetRoleCode()).orElse(null);
        if (role == null || !role.isActive()) {
            throw new IllegalStateException("Role is no longer available: " + invitation.getTargetRoleCode());
        }
        String category = categoryFor(role.getBaseRoleCode());

        UserEntity user = userRepository.findByEmailIgnoreCase(invitation.getEmail())
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .email(invitation.getEmail())
                        .phoneNumber(verifiedPhone)
                        .firstName(firstName.trim())
                        .lastName(lastName.trim())
                        .userCategory(category)
                        .build()));
        if (!user.isActiveFlag()) {
            throw new IllegalStateException("This account has been deactivated. Contact the platform administrator.");
        }
        // Staff outranks parent: a teacher who is also a parent is STAFF
        if (UserEntity.CATEGORY_STAFF.equals(category)) {
            user.setUserCategory(UserEntity.CATEGORY_STAFF);
        }

        if (invitation.isOrgWide()) {
            Long orgId = invitation.getOrganization().getOrganizationId();
            orgMemberRepository.findGrant(orgId, user.getUserId(), role.getTypeCode())
                    .ifPresentOrElse(m -> {
                        m.setActive(true);
                        m.setAssignedDate(LocalDate.now());
                    }, () -> orgMemberRepository.save(OrganizationMemberEntity.builder()
                            .organizationId(orgId).user(user).staffType(role).build()));
        } else {
            SchoolEntity school = invitation.getSchool();
            grantRepository.findGrant(user.getUserId(), school.getSchoolId(), role.getStaffTypeId())
                    .ifPresentOrElse(existing -> {
                        if (!existing.isActive()) {
                            existing.setActive(true);
                            existing.setAssignedDate(LocalDate.now());
                        }
                    }, () -> grantRepository.save(UserSchoolStaffTypeEntity.builder()
                            .user(user).school(school).staffType(role).build()));
        }
        invitation.setUsed(true);
        return user;
    }

    // ------------------------------------------------------------------ members

    @Transactional(readOnly = true)
    public List<MemberResponse> members(Long actorUserId, Long schoolId, String roleCode, String query) {
        Set<String> perms = access.permissions(actorUserId, schoolId);
        if (!perms.contains(SchoolAccessService.USER_MANAGE) && !perms.contains(SchoolAccessService.SESSION_MONITOR)) {
            throw new SecurityException("You need USER_MANAGE or SESSION_MONITOR to list members.");
        }
        record Row(long userId, String first, String last, String email, String phone, String category, boolean active,
                   String roleCode, String roleName, boolean orgWide, LocalDate assigned) {}
        List<Row> rows = jdbc.sql("""
                        SELECT u.user_id, u.first_name, u.last_name, u.email, u.phone_number, u.user_category, u.is_active_flag,
                               st.type_code, st.type_name, x.org_wide, x.assigned_date
                        FROM (
                            SELECT g.user_id, g.staff_type_id, FALSE AS org_wide, g.assigned_date FROM user_school_staff_type g
                            WHERE g.school_id = :schoolId AND g.is_active
                            UNION ALL
                            SELECT m.user_id, m.staff_type_id, TRUE, m.assigned_date FROM organization_member m
                            JOIN school s ON s.organization_id = m.organization_id
                            WHERE s.school_id = :schoolId AND m.is_active
                        ) x
                        JOIN app_user u ON u.user_id = x.user_id
                        JOIN staff_type st ON st.staff_type_id = x.staff_type_id
                        WHERE (CAST(:role AS TEXT) IS NULL OR st.type_code = :role)
                          AND (CAST(:q AS TEXT) IS NULL OR lower(u.first_name || ' ' || u.last_name) LIKE '%' || lower(:q) || '%'
                               OR u.phone_number LIKE '%' || :q || '%' OR lower(u.email) LIKE '%' || lower(:q) || '%')
                        ORDER BY u.first_name, u.last_name, st.level DESC
                        LIMIT 2000
                        """)
                .param("schoolId", schoolId)
                .param("role", roleCode == null ? null : roleCode.toUpperCase())
                .param("q", query == null || query.isBlank() ? null : query.trim())
                .query((rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBoolean(7), rs.getString(8), rs.getString(9), rs.getBoolean(10),
                        rs.getObject(11, LocalDate.class)))
                .list();
        Map<Long, MemberResponse> byUser = new LinkedHashMap<>();
        for (Row r : rows) {
            byUser.computeIfAbsent(r.userId(), id -> new MemberResponse(id, r.first(), r.last(), r.email(), r.phone(),
                            r.category(), r.active(), new ArrayList<>()))
                    .roles().add(new MemberRole(r.roleCode(), r.roleName(), r.orgWide(), r.assigned()));
        }
        return List.copyOf(byUser.values());
    }

    /**
     * Revokes a user's branch role(s); with roleCode = null every branch role is revoked. Org-wide roles are
     * removed through the organization. Grants are deactivated, not deleted, so the user can be re-invited.
     */
    @Transactional
    public RemoveUserResponse removeFromSchool(Long actorUserId, boolean actorIsSuperUser, Long schoolId,
                                               Long targetUserId, String roleCode) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot remove yourself from a school.");
        }
        schoolRepository.findById(schoolId).orElseThrow(() -> new NoSuchElementException("School not found: " + schoolId));
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetUserId));

        List<UserSchoolStaffTypeEntity> grants = grantRepository.findActiveGrants(targetUserId, schoolId).stream()
                .filter(g -> roleCode == null || g.getStaffType().getTypeCode().equalsIgnoreCase(roleCode))
                .toList();
        if (grants.isEmpty()) {
            throw new NoSuchElementException(roleCode == null
                    ? "User " + targetUserId + " has no active branch role in school " + schoolId
                    : "User " + targetUserId + " does not hold role " + roleCode.toUpperCase() + " in school " + schoolId);
        }
        if (actorIsSuperUser) {
            grants.stream().map(g -> g.getStaffType().getTypeCode()).filter(c -> !SETUP_ROLES.contains(c)).findFirst()
                    .ifPresent(c -> {
                        throw new SecurityException("The super user can only remove a branch's admins.");
                    });
        } else {
            if (target.isSuperUser()) {
                throw new SecurityException("Only the super user can remove a super user.");
            }
            grants.stream().map(UserSchoolStaffTypeEntity::getStaffType)
                    .filter(role -> !access.canManage(actorUserId, schoolId, role))
                    .findFirst()
                    .ifPresent(role -> {
                        throw new SecurityException("You do not have permission to remove a " + role.getTypeCode() + " from this school.");
                    });
        }
        grants.forEach(g -> g.setActive(false));
        return new RemoveUserResponse(targetUserId, schoolId, grants.stream().map(g -> g.getStaffType().getTypeCode()).toList());
    }

    @Transactional(readOnly = true)
    public List<OrgMemberResponse> orgMembers(Long actorUserId, boolean actorIsSuperUser, Long organizationId) {
        if (!actorIsSuperUser && orgMemberRepository.findActive(organizationId, actorUserId).isEmpty()) {
            throw new SecurityException("Only org-wide members can list org-wide members.");
        }
        return orgMemberRepository.findActiveByOrganization(organizationId).stream()
                .map(m -> new OrgMemberResponse(m.getUser().getUserId(), m.getUser().getFirstName(), m.getUser().getLastName(),
                        m.getUser().getEmail(), m.getStaffType().getTypeCode(), m.getAssignedDate()))
                .toList();
    }

    @Transactional
    public void removeOrgMember(Long actorUserId, boolean actorIsSuperUser, Long organizationId, Long targetUserId, String roleCode) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot remove yourself.");
        }
        OrganizationMemberEntity member = orgMemberRepository.findGrant(organizationId, targetUserId, roleCode.toUpperCase())
                .filter(OrganizationMemberEntity::isActive)
                .orElseThrow(() -> new NoSuchElementException("No active org-wide " + roleCode + " role for user " + targetUserId));
        if (actorIsSuperUser) {
            if (!SETUP_ROLES.contains(member.getStaffType().getTypeCode())) {
                throw new SecurityException("The super user can only remove an organization's admins.");
            }
        } else {
            int actorLevel = orgMemberRepository.findActive(organizationId, actorUserId).stream()
                    .mapToInt(m -> m.getStaffType().getLevel()).max().orElse(0);
            if (actorLevel <= member.getStaffType().getLevel()) {
                throw new SecurityException("You can only remove org-wide roles below your own.");
            }
        }
        member.setActive(false);
    }

    // ------------------------------------------------------------------ helpers

    private SchoolInvitation usableInvitation(String token) {
        SchoolInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token."));
        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This invitation has expired or has already been used.");
        }
        if (invitation.targetIsDisabled()) {
            throw new IllegalStateException("This school or organization is disabled.");
        }
        return invitation;
    }

    private String link(SchoolInvitation invitation) {
        return frontendBaseUrl + "/onboard?token=" + invitation.getToken();
    }

    static String categoryFor(String baseRoleCode) {
        return switch (baseRoleCode) {
            case "PARENT" -> UserEntity.CATEGORY_PARENT;
            case "STUDENT" -> UserEntity.CATEGORY_STUDENT;
            default -> UserEntity.CATEGORY_STAFF;
        };
    }
}
