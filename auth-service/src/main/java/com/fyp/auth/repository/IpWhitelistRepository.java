package com.fyp.auth.repository;

import com.fyp.auth.entity.IpWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for IP Whitelist entity operations.
 */
@Repository
public interface IpWhitelistRepository extends JpaRepository<IpWhitelist, Long> {

    /**
     * Find by IP address.
     */
    Optional<IpWhitelist> findByIpAddress(String ipAddress);

    /**
     * Check if IP address exists.
     */
    boolean existsByIpAddress(String ipAddress);

    /**
     * Find all enabled entries.
     */
    List<IpWhitelist> findByEnabledTrue();

    /**
     * Find global entries (not restricted to user or role).
     */
    @Query("SELECT i FROM IpWhitelist i WHERE i.enabled = true AND i.user IS NULL AND i.role IS NULL")
    List<IpWhitelist> findGlobalEntries();

    /**
     * Find entries for a specific user.
     */
    @Query("SELECT i FROM IpWhitelist i WHERE i.enabled = true AND i.user.id = :userId")
    List<IpWhitelist> findByUserId(@Param("userId") Long userId);

    /**
     * Find entries for a specific role.
     */
    @Query("SELECT i FROM IpWhitelist i WHERE i.enabled = true AND i.role.id = :roleId")
    List<IpWhitelist> findByRoleId(@Param("roleId") Long roleId);

    /**
     * Find entries applicable to a user (global + user-specific + role-specific).
     */
    @Query("SELECT DISTINCT i FROM IpWhitelist i " +
           "LEFT JOIN i.user u " +
           "LEFT JOIN i.role r " +
           "LEFT JOIN r.users ru " +
           "WHERE i.enabled = true " +
           "AND (i.user IS NULL AND i.role IS NULL " +
           "     OR i.user.id = :userId " +
           "     OR ru.id = :userId)")
    List<IpWhitelist> findApplicableToUser(@Param("userId") Long userId);

    /**
     * Find by type.
     */
    List<IpWhitelist> findByType(IpWhitelist.IpType type);
}
