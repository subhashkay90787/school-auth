package com.school.auth.service;

import com.school.auth.dto.TokenPayloadDto;
import com.school.auth.entity.UserSchoolStaffTypeEntity;
import com.school.auth.repository.UserSchoolStaffTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolGrantService {

    private final UserSchoolStaffTypeRepository userSchoolStaffTypeRepository;

    @Transactional(readOnly = true)
    public List<TokenPayloadDto.SchoolAccess> getSchoolGrantsForUser(Long userId) {
        List<UserSchoolStaffTypeEntity> mappings = userSchoolStaffTypeRepository.findActiveGrantsByUserId(userId);

        // Group mappings by School to aggregate multiple roles and features per school.
        // Disabled schools are dropped, so they vanish from the next token issued or refreshed.
        Map<Long, List<UserSchoolStaffTypeEntity>> groupedBySchool = mappings.stream()
                .filter(m -> m.getSchool().isActive())
                .collect(Collectors.groupingBy(m -> m.getSchool().getSchoolId()));

        return groupedBySchool.values().stream().map(schoolMappings -> {
            var school = schoolMappings.get(0).getSchool();

            // Extract all roles/staff types for this specific school
            List<String> roles = schoolMappings.stream()
                    .map(m -> m.getStaffType().getTypeCode())
                    .distinct()
                    .collect(Collectors.toList());

            // Extract enabled features for this school
            List<TokenPayloadDto.EnabledFeature> features = school.getFeatures().stream()
                    .filter(f -> f.isActive())
                    .map(f -> new TokenPayloadDto.EnabledFeature(
                            f.getFeatureId(),
                            f.getFeatureCode(),
                            f.getFeatureName()
                    ))
                    .collect(Collectors.toList());

            return new TokenPayloadDto.SchoolAccess(
                    school.getSchoolId(),
                    school.getSchoolCode(),
                    school.getSchoolName(),
                    school.getHostUrl(),
                    school.getCity(),
                    school.getState(),
                    school.getCountry(),
                    school.getStatus(),
                    roles,
                    features
            );
        }).collect(Collectors.toList());
    }
}