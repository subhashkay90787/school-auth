package com.school.auth.service;

import com.school.auth.dto.BacklogDto.*;
import com.school.auth.entity.FeatureRequestCommentEntity;
import com.school.auth.entity.FeatureRequestEntity;
import com.school.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/** Feature backlog on the main page: tenants raise and vote; the super user triages. */
@Service
@RequiredArgsConstructor
public class FeatureRequestService {

    private final FeatureRequestRepository requestRepository;
    private final FeatureRequestCommentRepository commentRepository;
    private final FeatureRepository featureRepository;
    private final InstitutionTypeRepository institutionTypeRepository;
    private final SchoolAccessService access;
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public Page<FeatureRequestResponse> list(Long userId, boolean superUser, String status, String category, Pageable pageable) {
        return requestRepository.search(status, category, pageable).map(r -> toResponse(r, userId, superUser, false));
    }

    @Transactional(readOnly = true)
    public FeatureRequestResponse get(Long userId, boolean superUser, Long id) {
        return toResponse(find(id), userId, superUser, true);
    }

    @Transactional
    public FeatureRequestResponse create(Long userId, boolean superUser, CreateFeatureRequestRequest request) {
        if (!superUser) {
            if (request.schoolId() == null) {
                throw new IllegalArgumentException("schoolId is required: the branch you are raising this from.");
            }
            access.requirePermission(userId, request.schoolId(), SchoolAccessService.FEATURE_REQUEST_RAISE);
        }
        FeatureRequestEntity entity = requestRepository.save(FeatureRequestEntity.builder()
                .title(request.title())
                .description(request.description())
                .category(request.category() == null ? "NEW_FEATURE" : request.category())
                .source(superUser ? "SUPER_USER" : "TENANT")
                .featureId(request.featureCode() == null ? null : featureId(request.featureCode()))
                .requestedByUserId(userId)
                .requestedBySchoolId(superUser ? null : request.schoolId())
                .build());
        if (!superUser) {
            vote(userId, entity.getFeatureRequestId(), request.schoolId());
        }
        return toResponse(find(entity.getFeatureRequestId()), userId, superUser, true);
    }

    @Transactional
    public void vote(Long userId, Long id, Long schoolId) {
        find(id);
        jdbc.sql("INSERT INTO feature_request_vote (feature_request_id, user_id, school_id) VALUES (:id, :userId, :schoolId) " +
                        "ON CONFLICT DO NOTHING")
                .param("id", id).param("userId", userId).param("schoolId", schoolId).update();
    }

    @Transactional
    public void unvote(Long userId, Long id) {
        jdbc.sql("DELETE FROM feature_request_vote WHERE feature_request_id = :id AND user_id = :userId")
                .param("id", id).param("userId", userId).update();
    }

    @Transactional
    public CommentResponse comment(Long userId, boolean superUser, Long id, CommentRequest request) {
        find(id);
        FeatureRequestCommentEntity c = commentRepository.save(FeatureRequestCommentEntity.builder()
                .featureRequestId(id).authorUserId(userId).body(request.body())
                .isInternal(superUser && Boolean.TRUE.equals(request.internal())).build());
        return new CommentResponse(c.getCommentId(), userId, c.getBody(), c.isInternal(), OffsetDateTime.now());
    }

    /** Super user only (enforced on /api/v1/platform/**). The status trigger records history with app.user_id. */
    @Transactional
    public FeatureRequestResponse triage(Long userId, Long id, TriageRequest request) {
        FeatureRequestEntity r = find(id);
        // Read by the status-history trigger (transaction-local)
        jdbc.sql("SELECT set_config('app.user_id', :u, true) || set_config('app.status_note', :n, true)")
                .param("u", userId.toString()).param("n", request.note() == null ? "" : request.note())
                .query(String.class).single();
        if (request.status() != null) {
            r.setStatus(request.status());
            r.setDuplicateOfId("DUPLICATE".equals(request.status()) ? request.duplicateOfId() : null);
            if ("SHIPPED".equals(request.status())) {
                r.setShippedAt(OffsetDateTime.now());
            }
        }
        if (request.priority() != null) r.setPriority(request.priority());
        if (request.targetRelease() != null) r.setTargetRelease(request.targetRelease());
        if (request.featureCode() != null) r.setFeatureId(featureId(request.featureCode()));
        if (request.institutionTypeCode() != null) {
            r.setInstitutionTypeId(institutionTypeRepository.findByTypeCode(request.institutionTypeCode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown institution type")).getInstitutionTypeId());
        }
        requestRepository.flush();
        return toResponse(find(id), userId, true, true);
    }

    private FeatureRequestEntity find(Long id) {
        return requestRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Feature request not found: " + id));
    }

    private Long featureId(String code) {
        return featureRepository.findByFeatureCode(code).orElseThrow(() -> new IllegalArgumentException("Unknown feature: " + code))
                .getFeatureId();
    }

    private FeatureRequestResponse toResponse(FeatureRequestEntity r, Long userId, boolean superUser, boolean withComments) {
        String featureCode = r.getFeatureId() == null ? null
                : featureRepository.findById(r.getFeatureId()).map(f -> f.getFeatureCode()).orElse(null);
        boolean voted = Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM feature_request_vote WHERE feature_request_id = :id AND user_id = :u)")
                .param("id", r.getFeatureRequestId()).param("u", userId).query(Boolean.class).single());
        List<CommentResponse> comments = !withComments ? List.of()
                : (superUser ? commentRepository.findByFeatureRequestIdOrderByCreatedAt(r.getFeatureRequestId())
                : commentRepository.findByFeatureRequestIdAndIsInternalFalseOrderByCreatedAt(r.getFeatureRequestId()))
                .stream().map(c -> new CommentResponse(c.getCommentId(), c.getAuthorUserId(), c.getBody(), c.isInternal(), c.getCreatedAt()))
                .toList();
        // vote_count is maintained by a trigger, so read it from the table rather than the cached entity
        int votes = jdbc.sql("SELECT vote_count FROM feature_request WHERE feature_request_id = :id")
                .param("id", r.getFeatureRequestId()).query(Integer.class).single();
        return new FeatureRequestResponse(r.getFeatureRequestId(), r.getTitle(), r.getDescription(), r.getCategory(),
                r.getStatus(), r.getPriority(), r.getSource(), featureCode, r.getRequestedBySchoolId(), r.getDuplicateOfId(),
                r.getTargetRelease(), votes, voted, r.getCreatedAt(), r.getUpdatedAt(), r.getShippedAt(), comments);
    }
}
