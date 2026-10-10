
package com.school.auth.controller;

import com.school.auth.entity.FeatureEntity;
import com.school.auth.entity.SchoolEntity;
import com.school.auth.repository.FeatureRepository;
import com.school.auth.repository.SchoolRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schools/{schoolId}/features")
@RequiredArgsConstructor
public class SchoolFeatureController {

    private final SchoolRepository schoolRepository;
    private final FeatureRepository featureRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<SchoolEntity> assignFeaturesToSchool(
            @PathVariable Long schoolId,
            @Valid @RequestBody AssignFeaturesRequest request) {

        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("School not found: " + schoolId));

        List<FeatureEntity> featuresToAssign = featureRepository.findAllById(request.featureIds());

        if (featuresToAssign.isEmpty()) {
            throw new IllegalArgumentException("No valid features found for the provided feature IDs.");
        }

        // Add features to the school's active feature list (many-to-many relationship)
        for (FeatureEntity feature : featuresToAssign) {
            if (!school.getFeatures().contains(feature)) {
                school.getFeatures().add(feature);
            }
        }

        SchoolEntity updatedSchool = schoolRepository.save(school);
        return ResponseEntity.ok(updatedSchool);
    }
}

record AssignFeaturesRequest(
    @NotEmpty(message = "At least one feature ID must be provided")
    List<@NotNull(message = "Feature ID cannot be null") Long> featureIds
) {}