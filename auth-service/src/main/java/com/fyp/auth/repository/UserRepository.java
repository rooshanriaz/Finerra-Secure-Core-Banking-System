package com.fyp.auth.repository;

import com.fyp.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by username or email.
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Find user by Fineract user ID.
     */
    Optional<User> findByFineractUserId(Long fineractUserId);

    /**
     * Check if username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Find all enabled users.
     */
    List<User> findByEnabledTrue();

    /**
     * Find all locked users.
     */
    List<User> findByAccountNonLockedFalse();

    /**
     * Find users by role name.
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findByRoleName(@Param("roleName") String roleName);

    /**
     * Update last login info.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime, u.lastLoginIp = :ip, u.failedLoginAttempts = 0 WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime, @Param("ip") String ip);

    /**
     * Increment failed login attempts.
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedAttempts(@Param("userId") Long userId);

    /**
     * Lock user account.
     */
    @Modifying
    @Query("UPDATE User u SET u.accountNonLocked = false, u.lockedAt = :lockedAt WHERE u.id = :userId")
    void lockAccount(@Param("userId") Long userId, @Param("lockedAt") LocalDateTime lockedAt);

    /**
     * Unlock user account.
     */
    @Modifying
    @Query("UPDATE User u SET u.accountNonLocked = true, u.lockedAt = null, u.failedLoginAttempts = 0 WHERE u.id = :userId")
    void unlockAccount(@Param("userId") Long userId);
}
