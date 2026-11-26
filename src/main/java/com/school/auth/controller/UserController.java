package com.school.auth.controller;

import com.school.auth.dto.SessionDto.*;
import com.school.auth.dto.UserProfileDto;
import com.school.auth.repository.UserDeviceRepository;
import com.school.auth.service.SessionService;
import com.school.auth.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService userAccountService;
    private final SessionService sessionService;
    private final UserDeviceRepository deviceRepository;

    @GetMapping("/me")
    public UserProfileDto.ProfileResponse getMyProfile(JwtAuthenticationToken authentication) {
        return userAccountService.getProfile(CurrentUser.from(authentication).userId());
    }

    /** Updates the caller's own profile. Claims in existing access tokens refresh on the next token refresh. */
    @PatchMapping("/me")
    public UserProfileDto.ProfileResponse updateMyProfile(JwtAuthenticationToken authentication,
                                                          @Valid @RequestBody UserProfileDto.UpdateProfileRequest request) {
        return userAccountService.updateProfile(CurrentUser.from(authentication).userId(), request);
    }

    // ---- sessions ("Me > Devices > Sign out")

    @GetMapping("/me/sessions")
    public List<SessionResponse> mySessions(JwtAuthenticationToken authentication) {
        CurrentUser me = CurrentUser.from(authentication);
        return sessionService.openSessions(me.userId()).stream().map(s -> SessionResponse.from(s, me.sessionId())).toList();
    }

    /** Sign out of this device: ends the current session (refresh token stops working). */
    @PostMapping("/me/logout")
    public ResponseEntity<Void> logout(JwtAuthenticationToken authentication) {
        CurrentUser me = CurrentUser.from(authentication);
        if (me.sessionId() != null) {
            sessionService.endSession(me.sessionId(), "LOGOUT", me.userId());
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @Transactional
    public ResponseEntity<Void> endMySession(JwtAuthenticationToken authentication, @PathVariable UUID sessionId) {
        CurrentUser me = CurrentUser.from(authentication);
        boolean mine = sessionService.openSessions(me.userId()).stream().anyMatch(s -> s.getSessionId().equals(sessionId));
        if (!mine) {
            throw new NoSuchElementException("Session not found: " + sessionId);
        }
        sessionService.endSession(sessionId, "REVOKED_BY_USER", me.userId());
        return ResponseEntity.noContent().build();
    }

    /** Sign out everywhere else. */
    @PostMapping("/me/sessions/end-others")
    public Map<String, Integer> endOtherSessions(JwtAuthenticationToken authentication) {
        CurrentUser me = CurrentUser.from(authentication);
        return Map.of("sessionsEnded", sessionService.endAllSessions(me.userId(), "REVOKED_BY_USER", me.userId(), me.sessionId()));
    }

    // ---- devices (push tokens per app)

    @GetMapping("/me/devices")
    @Transactional(readOnly = true)
    public List<DeviceResponse> myDevices(JwtAuthenticationToken authentication) {
        return deviceRepository.findByUserIdAndIsActiveTrueOrderByLastSeenAtDesc(CurrentUser.from(authentication).userId())
                .stream().map(DeviceResponse::from).toList();
    }

    @PutMapping("/me/devices")
    public DeviceResponse registerDevice(JwtAuthenticationToken authentication, @Valid @RequestBody RegisterDeviceRequest request) {
        CurrentUser me = CurrentUser.from(authentication);
        // The token's audience is the OAuth client (generic app or a dedicated app)
        String clientId = authentication.getToken().getAudience().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Token has no client audience"));
        return DeviceResponse.from(sessionService.upsertDevice(me.userId(), clientId, request.deviceId(), request.deviceName(),
                request.platform(), request.appVersion(), request.pushToken()));
    }

    @DeleteMapping("/me/devices/{userDeviceId}")
    @Transactional
    public ResponseEntity<Void> removeDevice(JwtAuthenticationToken authentication, @PathVariable Long userDeviceId) {
        Long me = CurrentUser.from(authentication).userId();
        var device = deviceRepository.findById(userDeviceId).filter(d -> d.getUserId().equals(me))
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + userDeviceId));
        device.setActive(false);
        device.setPushToken(null);
        return ResponseEntity.noContent().build();
    }

    /** Deactivates an account across all schools and ends its sessions. SUPER_USER only. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateUser(JwtAuthenticationToken authentication, @PathVariable Long userId) {
        userAccountService.deactivateUser(CurrentUser.from(authentication).userId(), userId);
        return ResponseEntity.noContent().build();
    }
}
