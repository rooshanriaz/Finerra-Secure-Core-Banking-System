package com.fyp.auth.service;

import com.fyp.auth.config.RevocationProperties;
import com.fyp.auth.entity.RevokedToken;
import com.fyp.auth.repository.RevokedTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service for token revocation with Redis caching and Pub/Sub.
 */
@Slf4j
@Service
public class RevocationService {

    private final RevocationProperties properties;
    private final RevokedTokenRepository revokedTokenRepository;
    private final Optional<RedisTemplate<String, String>> redisTemplate;

    public RevocationService(
            RevocationProperties properties,
            RevokedTokenRepository revokedTokenRepository,
            Optional<RedisTemplate<String, String>> redisTemplate) {
        this.properties = properties;
        this.revokedTokenRepository = revokedTokenRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Revoke a token.
     * 
     * @param jti JWT ID
     * @param userId User ID
     * @param username Username
     * @param expiresAt Token expiration time
     * @param reason Revocation reason
     * @param revokedBy Who revoked (username or "SYSTEM")
     * @param revokedFromIp IP address
     * @param details Additional details
     */
    @Transactional
    public void revokeToken(
            String jti,
            Long userId,
            String username,
            Date expiresAt,
            RevokedToken.RevocationReason reason,
            String revokedBy,
            String revokedFromIp,
            String details) {
        
        log.info("Revoking token {} for user {} ({}). Reason: {}", jti, username, userId, reason);

        // Calculate TTL (time until token would expire)
        long ttlSeconds = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds <= 0) {
            log.debug("Token already expired, no need to revoke");
            return;
        }

        // Store in Redis (primary) - with graceful fallback
        try {
            redisTemplate.ifPresent(template -> {
                String key = properties.getKeyPrefix() + jti;
                template.opsForValue().set(key, reason.name(), Duration.ofSeconds(ttlSeconds));
                log.debug("Token revocation stored in Redis: {} with TTL {} seconds", jti, ttlSeconds);

                // Publish revocation event for instant logout across all instances
                template.convertAndSend(properties.getChannel(), jti);
                log.debug("Published revocation event for token: {}", jti);
            });
        } catch (Exception e) {
            log.warn("Failed to store token revocation in Redis (will use database): {}", e.getMessage());
        }

        // Store in database (backup + audit)
        LocalDateTime expiresAtLocal = LocalDateTime.ofInstant(
                expiresAt.toInstant(), ZoneId.systemDefault());

        RevokedToken revokedToken = RevokedToken.builder()
                .jti(jti)
                .userId(userId)
                .username(username)
                .expiresAt(expiresAtLocal)
                .reason(reason)
                .details(details)
                .revokedBy(revokedBy)
                .revokedFromIp(revokedFromIp)
                .build();

        revokedTokenRepository.save(revokedToken);
        log.info("Token revoked and persisted: {}", jti);
    }

    /**
     * Check if a token is revoked.
     * Gracefully handles Redis connection failures by falling back to database.
     */
    public boolean isTokenRevoked(String jti) {
        // Check Redis first (fast) - with graceful fallback
        try {
            boolean inRedis = redisTemplate
                    .map(template -> Boolean.TRUE.equals(
                            template.hasKey(properties.getKeyPrefix() + jti)))
                    .orElse(false);

            if (inRedis) {
                log.debug("Token {} found in Redis revocation cache", jti);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis check failed for token {}, falling back to database: {}", jti, e.getMessage());
            // Continue to database check
        }

        // Fall back to database
        try {
            boolean inDb = revokedTokenRepository.existsByJti(jti);
            if (inDb) {
                log.debug("Token {} found in database revocation list", jti);
                // Try to re-add to Redis for faster future checks
                try {
                    redisTemplate.ifPresent(template -> {
                        revokedTokenRepository.findByJti(jti).ifPresent(token -> {
                            long ttlSeconds = Duration.between(
                                    LocalDateTime.now(), token.getExpiresAt()).getSeconds();
                            if (ttlSeconds > 0) {
                                template.opsForValue().set(
                                        properties.getKeyPrefix() + jti,
                                        token.getReason().name(),
                                        Duration.ofSeconds(ttlSeconds));
                            }
                        });
                    });
                } catch (Exception e) {
                    log.debug("Failed to cache revocation in Redis: {}", e.getMessage());
                }
            }
            return inDb;
        } catch (Exception e) {
            log.error("Database check failed for token revocation: {}", e.getMessage());
            // If we can't check revocation, allow the request (fail-open for availability)
            return false;
        }
    }

    /**
     * Revoke all tokens for a user.
     * Used when user is disabled, password changed, etc.
     */
    @Transactional
    public void revokeAllTokensForUser(Long userId, String username, 
            RevokedToken.RevocationReason reason, String revokedBy) {
        log.info("Revoking all tokens for user {} ({}). Reason: {}", username, userId, reason);
        
        // Publish user-level revocation event
        redisTemplate.ifPresent(template -> {
            template.convertAndSend(properties.getChannel(), "USER:" + userId);
        });

        // Note: We can't revoke specific tokens without knowing their JTIs
        // The Gateway should listen for user-level revocation and reject all tokens for this user
    }

    /**
     * Get revocation channel topic.
     */
    public ChannelTopic getRevocationChannel() {
        return new ChannelTopic(properties.getChannel());
    }

    /**
     * Cleanup expired revoked tokens from database.
     * Runs every hour.
     */
    @Scheduled(fixedRateString = "${revocation.cleanup-interval:3600000}")
    @Transactional
    public void cleanupExpiredTokens() {
        log.debug("Running revoked token cleanup...");
        int deleted = revokedTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired revoked tokens", deleted);
        }
    }

    /**
     * Get revocation statistics.
     */
    public RevocationStats getStats() {
        long total = revokedTokenRepository.count();
        long logouts = revokedTokenRepository.countByReason(RevokedToken.RevocationReason.LOGOUT);
        long security = revokedTokenRepository.countByReason(RevokedToken.RevocationReason.SECURITY_CONCERN);
        long admin = revokedTokenRepository.countByReason(RevokedToken.RevocationReason.ADMIN_ACTION);
        
        return new RevocationStats(total, logouts, security, admin);
    }

    /**
     * Revocation statistics record.
     */
    public record RevocationStats(long total, long logouts, long security, long adminActions) {}
}
