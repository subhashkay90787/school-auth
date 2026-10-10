package com.school.auth.service;

import com.school.auth.dto.UserProfileDto;
import com.school.auth.entity.UserEntity;
import com.school.auth.otp.OtpService;
import com.school.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final SchoolGrantService schoolGrantService;
    private final OtpService otpService;

    @Transactional(readOnly = true)
    public UserProfileDto.ProfileResponse getProfile(Long userId) {
        return toProfile(findActiveUser(userId));
    }

    /**
     * Partial update: null fields are left unchanged. Email is the login identity (OTP principal and the
     * social-login match key), so it cannot be changed here. Both phone numbers can be used for OTP login,
     * so changing either one requires an OTP sent to the new number.
     */
    @Transactional
    public UserProfileDto.ProfileResponse updateProfile(Long userId, UserProfileDto.UpdateProfileRequest request) {
        UserEntity user = findActiveUser(userId);

        String newPhone = request.phoneNumber() == null ? user.getPhoneNumber() : OtpService.normalizePhone(request.phoneNumber());
        String newAlternate = request.alternatePhoneNumber() == null
                ? user.getAlternatePhoneNumber()
                : StringUtils.hasText(request.alternatePhoneNumber()) ? OtpService.normalizePhone(request.alternatePhoneNumber()) : null;

        boolean phoneChanged = !newPhone.equals(user.getPhoneNumber());
        boolean alternateChanged = !Objects.equals(newAlternate, user.getAlternatePhoneNumber());

        // Validate everything before consuming any OTP
        if (newPhone.equals(newAlternate)) {
            throw new IllegalArgumentException("Phone number and alternate phone number must be different.");
        }
        if (phoneChanged) {
            ensurePhoneAvailable(newPhone, userId);
        }
        if (alternateChanged && newAlternate != null) {
            ensurePhoneAvailable(newAlternate, userId);
        }

        if (phoneChanged && !otpService.verifyOtp(newPhone, request.phoneOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP for the new phone number.");
        }
        // Removing the alternate number needs no OTP; setting a new one does
        if (alternateChanged && newAlternate != null && !otpService.verifyOtp(newAlternate, request.alternatePhoneOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP for the new alternate phone number.");
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        user.setPhoneNumber(newPhone);
        user.setAlternatePhoneNumber(newAlternate);

        return toProfile(user);
    }

    /**
     * Platform-wide removal (super user only). The account is deactivated rather than deleted: it can no longer
     * log in or refresh tokens (CustomJwtTokenCustomizerConfig rejects inactive users), and history is kept.
     */
    @Transactional
    public void deactivateUser(Long actorUserId, Long targetUserId) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        }
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetUserId));
        if (target.isSuperUser()) {
            throw new SecurityException("A super user account cannot be deactivated through the API.");
        }
        target.setActiveFlag(false);
    }

    private UserEntity findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(UserEntity::isActiveFlag)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    private void ensurePhoneAvailable(String phone, Long userId) {
        userRepository.findByPhoneNumberOrAlternate(phone)
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw new IllegalStateException("Phone number " + phone + " is already registered to another account.");
                });
    }

    private UserProfileDto.ProfileResponse toProfile(UserEntity user) {
        return new UserProfileDto.ProfileResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAlternatePhoneNumber(),
                user.isSuperUser(),
                schoolGrantService.getSchoolGrantsForUser(user.getUserId())
        );
    }
}
