package com.fyp.auth.repository;

import com.fyp.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for Permission entity operations.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * Find permission by code.
     */
    Optional<Permission> findByCode(String code);

    /**
     * Check if permission code exists.
     */
    boolean existsByCode(String code);

    /**
     * Find permissions by grouping.
     */
    List<Permission> findByGrouping(String grouping);

    /**
     * Find permissions by entity name.
     */
    List<Permission> findByEntityName(String entityName);

    /**
     * Find permissions by action name.
     */
    List<Permission> findByActionName(String actionName);

    /**
     * Find all enabled permissions.
     */
    List<Permission> findByEnabledTrue();

    /**
     * Find permissions synced from Fineract.
     */
    List<Permission> findBySyncedFromFineractTrue();

    /**
     * Find permissions by codes.
     */
    Set<Permission> findByCodeIn(Set<String> codes);

    /**
     * Get distinct groupings.
     */
    @Query("SELECT DISTINCT p.grouping FROM Permission p WHERE p.grouping IS NOT NULL ORDER BY p.grouping")
    List<String> findDistinctGroupings();

    /**
     * Get permissions for a user (through their roles).
     */
    @Query("SELECT DISTINCT p FROM Permission p " +
           "JOIN p.roles r " +
           "JOIN r.users u " +
           "WHERE u.id = :userId AND p.enabled = true AND r.enabled = true")
    Set<Permission> findByUserId(@Param("userId") Long userId);
}
