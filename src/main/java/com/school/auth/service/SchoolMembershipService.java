package com.school.auth.service;

import com.school.auth.entity.SchoolEntity;
import com.school.auth.entity.UserEntity;
import com.school.auth.entity.UserSchoolStaffTypeEntity;
import com.school.auth.repository.SchoolRepository;
import com.school.auth.repository.UserRepository;
import com.school.auth.repository.UserSchoolStaffTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SchoolMembershipService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;
    private final RoleHierarchy roleHierarchy;

    /**
     * Revokes a user's role(s) in a school. With roleCode = null every role the user holds in the school is
     * revoked. The caller must be allowed (by RoleHierarchy) to manage each role being revoked; the super user
     * may revoke anything. Grants are deactivated rather than deleted, so the user can be re-invited later.
     *
     * @return the role codes that were revoked
     */
    @Transactional
    public List<String> removeUserFromSchool(Long actorUserId, boolean actorIsSuperUser,
                                             Long schoolId, Long targetUserId, String roleCode) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot remove yourself from a school.");
        }

        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new NoSuchElementException("School not found: " + schoolId));
        if (!school.isActive() && !actorIsSuperUser) {
            throw new IllegalStateException("This school is disabled.");
        }

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetUserId));

        List<UserSchoolStaffTypeEntity> grants = userSchoolStaffTypeRepository.findActiveGrants(targetUserId, schoolId).stream()
                .filter(g -> roleCode == null || g.getStaffType().getTypeCode().equalsIgnoreCase(roleCode))
                .toList();
        if (grants.isEmpty()) {
            throw new NoSuchElementException(roleCode == null
                    ? "User " + targetUserId + " has no active role in school " + schoolId
                    : "User " + targetUserId + " does not hold role " + roleCode.toUpperCase() + " in school " + schoolId);
        }

        if (!actorIsSuperUser) {
            if (target.isSuperUser()) {
                throw new SecurityException("Only the super user can remove a super user.");
            }
            List<String> actorRoles = userSchoolStaffTypeRepository.findActiveGrants(actorUserId, schoolId).stream()
                    .map(g -> g.getStaffType().getTypeCode())
                    .toList();
            grants.stream()
                    .map(g -> g.getStaffType().getTypeCode())
                    .filter(role -> !roleHierarchy.canManage(actorRoles, role))
                    .findFirst()
                    .ifPresent(role -> {
                        throw new SecurityException("You do not have permission to remove a " + role + " from this school.");
                    });
        }

        grants.forEach(g -> g.setActive(false));

        return grants.stream().map(g -> g.getStaffType().getTypeCode()).toList();
    }
}
