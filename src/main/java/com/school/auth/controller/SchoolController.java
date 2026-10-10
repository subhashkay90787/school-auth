package com.school.auth.controller;

import com.school.auth.entity.SchoolEntity;
import com.school.auth.repository.SchoolRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolRepository schoolRepository;

    @PostMapping
    public ResponseEntity<SchoolEntity> createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        if (request.hostUrl() != null && schoolRepository.findByHostUrl(request.hostUrl()).isPresent()) {
            throw new IllegalArgumentException("A school with this host URL already exists.");
        }

        SchoolEntity school = SchoolEntity.builder()
                .schoolName(request.schoolName())
                .hostUrl(request.hostUrl())
                .status("ACTIVE")
                .build();

        SchoolEntity savedSchool = schoolRepository.save(school);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedSchool);
    }
}

record CreateSchoolRequest(
    @NotBlank(message = "School name is required")
    String schoolName,
    String hostUrl
) {}