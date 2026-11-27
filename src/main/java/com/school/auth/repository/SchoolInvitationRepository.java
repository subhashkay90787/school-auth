package com.school.auth.repository;

import com.school.auth.entity.SchoolInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolInvitationRepository extends JpaRepository<SchoolInvitation, Long> {
    @EntityGraph(attributePaths = {"school", "organization"})
    Optional<SchoolInvitation> findByToken(String token);
}
