package com.example.inventory.repository;

import com.example.inventory.entity.RefreshToken;
import com.example.inventory.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    int deleteByUser(User user);

    /** Housekeeping: drops a user's expired or revoked sessions. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user = :user AND (t.revoked = true OR t.expiryDate < :now)")
    int deleteStaleByUser(@Param("user") User user, @Param("now") Instant now);
}
