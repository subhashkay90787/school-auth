package com.school.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class BacklogDto {

    private BacklogDto() {}

    public record CreateFeatureRequestRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @Pattern(regexp = "NEW_FEATURE|ENHANCEMENT|INTEGRATION|UX|BUG") String category,
            // Branch it is raised from (the caller must hold FEATURE_REQUEST_RAISE there); NULL for the super user
            Long schoolId,
            String featureCode) {}

    public record TriageRequest(
            @Pattern(regexp = "NEW|UNDER_REVIEW|PLANNED|IN_PROGRESS|SHIPPED|DECLINED|DUPLICATE") String status,
            @Pattern(regexp = "P0|P1|P2|P3") String priority,
            Long duplicateOfId,
            String targetRelease,
            String featureCode,
            String institutionTypeCode,
            String note) {}

    public record CommentRequest(@NotBlank String body, Boolean internal) {}

    public record CommentResponse(Long commentId, Long authorUserId, String body, boolean internal, OffsetDateTime createdAt) {}

    public record FeatureRequestResponse(Long featureRequestId, String title, String description, String category,
                                         String status, String priority, String source, String featureCode,
                                         Long requestedBySchoolId, Long duplicateOfId, String targetRelease,
                                         int voteCount, boolean votedByMe, OffsetDateTime createdAt,
                                         OffsetDateTime updatedAt, OffsetDateTime shippedAt,
                                         List<CommentResponse> comments) {}
}
