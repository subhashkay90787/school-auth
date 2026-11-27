package com.school.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_user", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    public static final String CATEGORY_STAFF = "STAFF";
    public static final String CATEGORY_NON_STAFF = "NON_STAFF";
    public static final String CATEGORY_PARENT = "PARENT";
    public static final String CATEGORY_STUDENT = "STUDENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "alternate_phone_number", length = 20)
    private String alternatePhoneNumber;

    // Optional: WhatsApp-only users (many parents) have no email
    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Builder.Default
    @Column(name = "user_category", nullable = false, length = 20)
    private String userCategory = CATEGORY_STAFF;

    @Builder.Default
    @Column(name = "is_active_flag", nullable = false)
    private boolean isActiveFlag = true;

    @Builder.Default
    @Column(name = "is_super_user", nullable = false)
    private boolean isSuperUser = false;
}
