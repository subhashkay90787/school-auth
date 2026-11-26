package com.school.auth.controller;

import com.school.auth.dto.BacklogDto.*;
import com.school.auth.service.FeatureRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/** Feature backlog (main page). Triage lives at PATCH /api/v1/platform/feature-requests/{id}. */
@RestController
@RequestMapping("/api/v1/feature-requests")
@RequiredArgsConstructor
public class FeatureRequestController {

    private final FeatureRequestService service;

    @GetMapping
    public Page<FeatureRequestResponse> list(JwtAuthenticationToken auth,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String category,
                                             @PageableDefault(size = 50, sort = "voteCount", direction = Sort.Direction.DESC) Pageable pageable) {
        CurrentUser user = CurrentUser.from(auth);
        return service.list(user.userId(), user.superUser(), status, category, pageable);
    }

    @GetMapping("/{id}")
    public FeatureRequestResponse get(JwtAuthenticationToken auth, @PathVariable Long id) {
        CurrentUser user = CurrentUser.from(auth);
        return service.get(user.userId(), user.superUser(), id);
    }

    @PostMapping
    public ResponseEntity<FeatureRequestResponse> create(JwtAuthenticationToken auth, @Valid @RequestBody CreateFeatureRequestRequest request) {
        CurrentUser user = CurrentUser.from(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), user.superUser(), request));
    }

    @PostMapping("/{id}/votes")
    public ResponseEntity<Void> vote(JwtAuthenticationToken auth, @PathVariable Long id, @RequestParam(required = false) Long schoolId) {
        service.vote(CurrentUser.from(auth).userId(), id, schoolId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/votes")
    public ResponseEntity<Void> unvote(JwtAuthenticationToken auth, @PathVariable Long id) {
        service.unvote(CurrentUser.from(auth).userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/comments")
    public CommentResponse comment(JwtAuthenticationToken auth, @PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        CurrentUser user = CurrentUser.from(auth);
        return service.comment(user.userId(), user.superUser(), id, request);
    }
}
