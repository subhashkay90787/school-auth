package com.school.auth.repository;

import com.school.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @Query("SELECT u FROM UserEntity u WHERE u.phoneNumber = :phone OR u.alternatePhoneNumber = :phone")
    Optional<UserEntity> findByPhoneNumberOrAlternate(@Param("phone") String phone);
}
