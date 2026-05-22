package com.fyp.auth.config;

import com.fyp.auth.entity.Permission;
import com.fyp.auth.entity.Role;
import com.fyp.auth.entity.User;
import com.fyp.auth.repository.PermissionRepository;
import com.fyp.auth.repository.RoleRepository;
import com.fyp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Initializes default data on application startup.
 * Creates default roles, permissions, and admin user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@2026!";
    private static final String LEGACY_ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing default data...");

        // Create default permissions
        Set<Permission> allPermissions = createDefaultPermissions();
        
        // Create default roles
        Role adminRole = createRole("ADMIN", "System Administrator with full access", 
                true, allPermissions);
        Role userRole = createRole("USER", "Standard user role", 
                false, getBasicPermissions(allPermissions));
        Role loanOfficerRole = createRole("LOAN_OFFICER", "Loan officer responsible for preliminary loan underwriting and recommendations",
                false, getLoanOfficerPermissions(allPermissions));
        Role managerRole = createRole("MANAGER", "Manager with elevated access", 
                false, getManagerPermissions(allPermissions));
        createRole("COMPLIANCE", "Compliance officer focused on KYC, AML, fraud monitoring, and audit oversight",
                false, getCompliancePermissions(allPermissions));

        // Create default admin user
        createDefaultAdmin(adminRole);
        ensureEditableBusinessRoles();

        log.info("Default data initialization completed");
    }

    private void ensureEditableBusinessRoles() {
        roleRepository.findByName("USER").ifPresent(role -> {
            if (role.isSystemRole()) {
                role.setSystemRole(false);
                roleRepository.save(role);
                log.info("Updated USER role to editable (systemRole=false)");
            }
        });
        roleRepository.findByName("MANAGER").ifPresent(role -> {
            if (role.isSystemRole()) {
                role.setSystemRole(false);
                roleRepository.save(role);
                log.info("Updated MANAGER role to editable (systemRole=false)");
            }
        });
        roleRepository.findByName("LOAN_OFFICER").ifPresent(role -> {
            if (role.isSystemRole()) {
                role.setSystemRole(false);
                roleRepository.save(role);
                log.info("Updated LOAN_OFFICER role to editable (systemRole=false)");
            }
        });
        roleRepository.findByName("COMPLIANCE").ifPresent(role -> {
            if (role.isSystemRole()) {
                role.setSystemRole(false);
                roleRepository.save(role);
                log.info("Updated COMPLIANCE role to editable (systemRole=false)");
            }
        });
    }

    private Set<Permission> createDefaultPermissions() {
        Set<Permission> permissions = new HashSet<>();

        // User permissions
        permissions.add(createPermission("CREATE_USER", "Create new users", "user", "USER", "CREATE"));
        permissions.add(createPermission("READ_USER", "View user details", "user", "USER", "READ"));
        permissions.add(createPermission("UPDATE_USER", "Update user information", "user", "USER", "UPDATE"));
        permissions.add(createPermission("DELETE_USER", "Delete users", "user", "USER", "DELETE"));

        // Role permissions
        permissions.add(createPermission("CREATE_ROLE", "Create new roles", "role", "ROLE", "CREATE"));
        permissions.add(createPermission("READ_ROLE", "View role details", "role", "ROLE", "READ"));
        permissions.add(createPermission("UPDATE_ROLE", "Update roles", "role", "ROLE", "UPDATE"));
        permissions.add(createPermission("DELETE_ROLE", "Delete roles", "role", "ROLE", "DELETE"));
        permissions.add(createPermission("ASSIGN_ROLE", "Assign roles to users", "role", "ROLE", "ASSIGN"));

        // Permission permissions
        permissions.add(createPermission("READ_PERMISSION", "View permissions", "permission", "PERMISSION", "READ"));

        // Client permissions (for Fineract integration)
        permissions.add(createPermission("CREATE_CLIENT", "Create clients", "client", "CLIENT", "CREATE"));
        permissions.add(createPermission("READ_CLIENT", "View client details", "client", "CLIENT", "READ"));
        permissions.add(createPermission("UPDATE_CLIENT", "Update client information", "client", "CLIENT", "UPDATE"));
        permissions.add(createPermission("DELETE_CLIENT", "Delete clients", "client", "CLIENT", "DELETE"));

        // Loan permissions
        permissions.add(createPermission("CREATE_LOAN", "Create loans", "loan", "LOAN", "CREATE"));
        permissions.add(createPermission("READ_LOAN", "View loan details", "loan", "LOAN", "READ"));
        permissions.add(createPermission("APPROVE_LOAN", "Approve loans", "loan", "LOAN", "APPROVE"));
        permissions.add(createPermission("DISBURSE_LOAN", "Disburse loans", "loan", "LOAN", "DISBURSE"));

        // Savings permissions
        permissions.add(createPermission("CREATE_SAVINGS", "Create savings accounts", "savings", "SAVINGS", "CREATE"));
        permissions.add(createPermission("READ_SAVINGS", "View savings accounts", "savings", "SAVINGS", "READ"));
        permissions.add(createPermission("DEPOSIT", "Make deposits", "savings", "SAVINGS", "DEPOSIT"));
        permissions.add(createPermission("WITHDRAW", "Make withdrawals", "savings", "SAVINGS", "WITHDRAW"));

        // Audit permissions
        permissions.add(createPermission("READ_AUDIT", "View audit logs", "audit", "AUDIT", "READ"));

        return permissions;
    }

    private Permission createPermission(String code, String description, String grouping, 
                                         String entityName, String actionName) {
        return permissionRepository.findByCode(code)
                .orElseGet(() -> {
                    Permission permission = Permission.builder()
                            .code(code)
                            .description(description)
                            .grouping(grouping)
                            .entityName(entityName)
                            .actionName(actionName)
                            .enabled(true)
                            .build();
                    log.debug("Created permission: {}", code);
                    return permissionRepository.save(permission);
                });
    }

    private Role createRole(String name, String description, boolean systemRole, 
                            Set<Permission> permissions) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name(name)
                            .description(description)
                            .systemRole(systemRole)
                            .enabled(true)
                            .permissions(permissions)
                            .build();
                    log.info("Created role: {}", name);
                    return roleRepository.save(role);
                });
    }

    private void createDefaultAdmin(Role adminRole) {
        String adminUsername = "admin";

        User existingAdmin = userRepository.findByUsername(adminUsername).orElse(null);
        if (existingAdmin != null) {
            if (passwordEncoder.matches(LEGACY_ADMIN_PASSWORD, existingAdmin.getPassword())) {
                existingAdmin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
                userRepository.save(existingAdmin);
                log.warn("Rotated legacy admin password to strong default for user: {}", adminUsername);
            } else {
                log.info("Admin user already exists");
            }
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .email("admin@fyp.com")
                .firstName("System")
                .lastName("Administrator")
                .enabled(true)
                .roles(Set.of(adminRole))
                .build();

        userRepository.save(admin);
        log.info("Created default admin user with strong default password: {}", adminUsername);
    }

    private Set<Permission> getBasicPermissions(Set<Permission> all) {
        Set<Permission> basic = new HashSet<>();
        for (Permission p : all) {
            if (p.getActionName().equals("READ") || 
                p.getCode().equals("DEPOSIT") || 
                p.getCode().equals("WITHDRAW")) {
                basic.add(p);
            }
        }
        return basic;
    }

    private Set<Permission> getManagerPermissions(Set<Permission> all) {
        Set<Permission> manager = new HashSet<>();
        for (Permission p : all) {
            if (!p.getCode().startsWith("DELETE_") && 
                !p.getCode().equals("CREATE_ROLE") &&
                !p.getCode().equals("UPDATE_ROLE")) {
                manager.add(p);
            }
        }
        return manager;
    }

    private Set<Permission> getLoanOfficerPermissions(Set<Permission> all) {
        Set<Permission> loanOfficer = new HashSet<>();
        for (Permission p : all) {
            if (p.getCode().equals("READ_LOAN")
                    || p.getCode().equals("APPROVE_LOAN")
                    || p.getCode().equals("READ_CLIENT")
                    || p.getCode().equals("READ_AUDIT")) {
                loanOfficer.add(p);
            }
        }
        return loanOfficer;
    }

    private Set<Permission> getCompliancePermissions(Set<Permission> all) {
        Set<Permission> compliance = new HashSet<>();
        for (Permission p : all) {
            if (p.getCode().equals("READ_AUDIT")
                    || p.getCode().equals("READ_USER")
                    || p.getCode().equals("READ_CLIENT")
                    || p.getCode().equals("READ_LOAN")
                    || p.getCode().equals("READ_ROLE")
                    || p.getCode().equals("READ_PERMISSION")) {
                compliance.add(p);
            }
        }
        return compliance;
    }
}
