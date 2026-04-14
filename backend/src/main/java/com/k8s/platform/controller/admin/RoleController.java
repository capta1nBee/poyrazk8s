package com.k8s.platform.controller.admin;

import com.k8s.platform.domain.entity.Role;
import com.k8s.platform.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manages the simple {@link com.k8s.platform.domain.entity.Role} entities used for
 * exec-audit and user authentication roles (ADMIN, OPERATOR, VIEWER …).
 * <p>
 * NOTE: The Casbin-based role-template RBAC system lives at
 * {@code /api/admin/roles} (see {@code RoleTemplateController}).
 * This controller is intentionally mapped to {@code /api/admin/exec-roles}
 * to avoid URL collision.
 */
@RestController
@RequestMapping("/api/admin/exec-roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<Role>> getAllRoles() {
        log.info("Fetching all roles");
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Role> getRoleById(@PathVariable Long id) {
        log.info("Fetching role: {}", id);
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found: " + id));
        return ResponseEntity.ok(role);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Role> createRole(@RequestBody Role role) {
        log.info("Creating role: {}", role.getName());

        if (roleRepository.existsByName(role.getName())) {
            throw new RuntimeException("Role already exists: " + role.getName());
        }

        Role savedRole = roleRepository.save(role);
        return ResponseEntity.ok(savedRole);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        log.info("Deleting role: {}", id);
        roleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
