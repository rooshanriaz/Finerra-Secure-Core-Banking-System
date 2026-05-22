package com.fyp.auth.repository;

import com.fyp.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for Role entity operations.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Find role by name.
     */
    Optional<Role> findByName(String name);

    /**
     * Find role by Fineract role ID.
     */
    Optional<Role> findByFineractRoleId(Long fineractRoleId);

    /**
     * Check if role name exists.
     */
    boolean existsByName(String name);

    /**
     * Find all enabled roles.
     */
    List<Role> findByEnabledTrue();

    /**
     * Find roles synced from Fineract.
     */
    List<Role> findBySyncedFromFineractTrue();

    /**
     * Find non-system roles (can be deleted).
     */
    List<Role> findBySystemRoleFalse();

    /**
     * Find roles by names.
     */
    Set<Role> findByNameIn(Set<String> names);

    /**
     * Find roles that have a specific permission.
     */
    @Query("SELECT r FROM Role r JOIN r.permissions p WHERE p.code = :permissionCode")
    List<Role> findByPermissionCode(@Param("permissionCode") String permissionCode);

    /**
     * Count users with a specific role.
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersWithRole(@Param("roleId") Long roleId);
}
