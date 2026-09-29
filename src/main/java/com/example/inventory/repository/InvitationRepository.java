package com.example.inventory.repository;

import com.example.inventory.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByToken(String token);
    boolean existsByEmailAndAcceptedAtIsNullAndExpiresAtAfter(String email, LocalDateTime now);
}
