package com.school.auth.controller;

import com.school.auth.dto.UserProfileDto;
import com.school.auth.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService userAccountService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto.ProfileResponse> getMyProfile(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(userAccountService.getProfile(CurrentUser.from(authentication).userId()));
    }

    /**
     * Updates the caller's own profile. Claims in existing access tokens refresh on the next token refresh.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileDto.ProfileResponse> updateMyProfile(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UserProfileDto.UpdateProfileRequest request) {
        return ResponseEntity.ok(userAccountService.updateProfile(CurrentUser.from(authentication).userId(), request));
    }

    /**
     * Deactivates an account across all schools. Restricted to SUPER_USER in AuthorizationServerConfig.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateUser(JwtAuthenticationToken authentication, @PathVariable Long userId) {
        userAccountService.deactivateUser(CurrentUser.from(authentication).userId(), userId);
        return ResponseEntity.noContent().build();
    }
}
