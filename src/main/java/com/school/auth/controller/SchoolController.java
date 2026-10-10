package com.school.auth.controller;

import com.school.auth.entity.FeatureEntity;
import com.school.auth.entity.SchoolEntity;
import com.school.auth.repository.SchoolRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolRepository schoolRepository;

    @PostMapping
    public ResponseEntity<SchoolResponse> createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        if (schoolRepository.findByHostUrl(request.hostUrl()).isPresent()) {
            throw new IllegalStateException("A school with this host URL already exists.");
        }
        if (schoolRepository.existsBySchoolCode(request.schoolCode())) {
            throw new IllegalStateException("A school with this code already exists.");
        }

        SchoolEntity school = SchoolEntity.builder()
                .schoolCode(request.schoolCode())
                .schoolName(request.schoolName())
                .hostUrl(request.hostUrl())
                .city(request.city())
                .state(request.state())
                .country(request.country())
                .status("ACTIVE")
                .build();

        SchoolEntity savedSchool = schoolRepository.save(school);
        return ResponseEntity.status(HttpStatus.CREATED).body(SchoolResponse.from(savedSchool));
    }

    /**
     * Enables or disables a school (super user only). Once INACTIVE, its users can no longer obtain or refresh
     * tokens for it, and invitations, onboarding and membership changes are blocked for non-super users.
     */
    @PatchMapping("/{schoolId}/status")
    @Transactional
    public ResponseEntity<SchoolResponse> updateSchoolStatus(
            @PathVariable Long schoolId,
            @Valid @RequestBody UpdateSchoolStatusRequest request) {
        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new NoSuchElementException("School not found: " + schoolId));
        school.setStatus(request.status().toUpperCase());
        return ResponseEntity.ok(SchoolResponse.from(school));
    }
}

record UpdateSchoolStatusRequest(
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
    String status
) {}

record CreateSchoolRequest(
    @NotBlank(message = "School code is required")
    String schoolCode,

    @NotBlank(message = "School name is required")
    String schoolName,

    @NotBlank(message = "Host URL is required")
    String hostUrl,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "State is required")
    String state,

    @NotBlank(message = "Country is required")
    String country
) {}

record SchoolResponse(
    Long schoolId,
    String schoolCode,
    String schoolName,
    String hostUrl,
    String city,
    String state,
    String country,
    String status,
    List<String> enabledFeatureCodes
) {
    static SchoolResponse from(SchoolEntity school) {
        return new SchoolResponse(
                school.getSchoolId(),
                school.getSchoolCode(),
                school.getSchoolName(),
                school.getHostUrl(),
                school.getCity(),
                school.getState(),
                school.getCountry(),
                school.getStatus(),
                school.getFeatures().stream().map(FeatureEntity::getFeatureCode).toList()
        );
    }
}
