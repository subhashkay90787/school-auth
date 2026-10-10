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

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Builder.Default
    @Column(name = "is_active_flag", nullable = false)
    private boolean isActiveFlag = true;

    @Builder.Default
    @Column(name = "is_super_user", nullable = false)
    private boolean isSuperUser = false;
}