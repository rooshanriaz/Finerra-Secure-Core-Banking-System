package com.fyp.auth.repository;

import com.fyp.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Revoked Token entity operations.
 */
@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * Find by JWT ID.
     */
    Optional<RevokedToken> findByJti(String jti);

    /**
     * Check if token is revoked by JTI.
     */
    boolean existsByJti(String jti);

    /**
     * Find revoked tokens for a user.
     */
    List<RevokedToken> findByUserId(Long userId);

    /**
     * Find by revocation reason.
     */
    List<RevokedToken> findByReason(RevokedToken.RevocationReason reason);

    /**
     * Find tokens revoked after a specific time.
     */
    List<RevokedToken> findByRevokedAtAfter(LocalDateTime afterTime);

    /**
     * Find expired revoked tokens (can be cleaned up).
     */
    @Query("SELECT t FROM RevokedToken t WHERE t.expiresAt < :now")
    List<RevokedToken> findExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Delete expired revoked tokens.
     */
    @Modifying
    @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Count revoked tokens by reason.
     */
    long countByReason(RevokedToken.RevocationReason reason);

    /**
     * Count revoked tokens for a user.
     */
    long countByUserId(Long userId);
}
