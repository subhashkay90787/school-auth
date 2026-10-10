package com.school.auth.configuration;

import com.school.auth.entity.UserEntity;
import com.school.auth.otp.OtpService;
import com.school.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Ensures the platform super user exists. The super user then logs in like anyone else
 * (OTP or social login) and receives "roles": ["SUPER_USER"] in the access token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperUserBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;

    @Value("${app.super-user.email:}")
    private String email;

    @Value("${app.super-user.phone:}")
    private String phone;

    @Value("${app.super-user.first-name:Super}")
    private String firstName;

    @Value("${app.super-user.last-name:User}")
    private String lastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email)) {
            log.warn("app.super-user.email is not set; no super user will be bootstrapped.");
            return;
        }

        UserEntity user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            if (!StringUtils.hasText(phone)) {
                throw new IllegalStateException("app.super-user.phone is required to create the super user " + email);
            }
            user = UserEntity.builder()
                    .email(email.trim().toLowerCase())
                    .phoneNumber(OtpService.normalizePhone(phone))
                    .firstName(firstName)
                    .lastName(lastName)
                    .build();
            log.info("Creating super user {}", email);
        }

        if (!user.isSuperUser()) {
            user.setSuperUser(true);
            user.setActiveFlag(true);
            userRepository.save(user);
            log.info("Granted super user access to {}", email);
        }
    }
}
