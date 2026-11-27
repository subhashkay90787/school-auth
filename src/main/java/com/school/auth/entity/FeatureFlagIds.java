package com.school.auth.entity;

import lombok.*;

import java.io.Serializable;

/** Composite keys for the (owner, feature) entitlement tables. */
public final class FeatureFlagIds {

    private FeatureFlagIds() {}

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class OrganizationFeatureId implements Serializable {
        private Long organizationId;
        private Long featureId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class SchoolFeatureId implements Serializable {
        private Long schoolId;
        private Long featureId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class AppClientSchoolId implements Serializable {
        private Long appClientId;
        private Long schoolId;
    }
}
