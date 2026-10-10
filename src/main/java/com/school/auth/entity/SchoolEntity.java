package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "SCHOOL", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "school_code", unique = true, nullable = false, length = 50)
    private String schoolCode;

    @Column(name = "school_name", nullable = false, length = 150)
    private String schoolName;

    @Column(name = "host_url", unique = true, nullable = false, length = 255)
    private String hostUrl;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = STATUS_ACTIVE;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "SCHOOL_FEATURE",
            schema = "identity",
            joinColumns = @JoinColumn(name = "school_id"),
            inverseJoinColumns = @JoinColumn(name = "feature_id")
    )
    private List<FeatureEntity> features = new ArrayList<>();

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}