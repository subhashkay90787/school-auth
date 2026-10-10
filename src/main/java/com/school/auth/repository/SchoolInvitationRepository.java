package com.school.auth.repository;

import com.school.auth.entity.SchoolInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolInvitationRepository extends JpaRepository<SchoolInvitation, Long> {
    Optional<SchoolInvitation> findByToken(String token);
}