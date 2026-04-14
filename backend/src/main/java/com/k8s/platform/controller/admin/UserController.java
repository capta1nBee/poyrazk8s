package com.k8s.platform.controller.admin;

import com.k8s.platform.dto.request.AssignPermissionsRequest;
import com.k8s.platform.dto.request.CreateUserRequest;
import com.k8s.platform.dto.request.UpdateUserRequest;
import com.k8s.platform.dto.response.UserPermissionsResponse;
import com.k8s.platform.dto.response.UserResponse;
import com.k8s.platform.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final com.k8s.platform.service.audit.AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Fetching all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        log.info("Fetching user: {}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        log.info("Creating user: {}", request.getUsername());
        UserResponse user = userService.createUser(request);
        auditLogService.log(request.getUsername(), "create user", "New user created by administrator");
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        log.info("Updating user: {}", id);
        UserResponse user = userService.updateUser(id, request);
        auditLogService.log("user: " + id, "update user", "User details updated");
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("Deleting user: {}", id);
        userService.deleteUser(id);
        auditLogService.log("user: " + id, "delete user", "User deleted by administrator");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserPermissionsResponse> assignPermissions(
            @PathVariable Long id,
            @RequestBody AssignPermissionsRequest request) {
        log.info("Assigning permissions to user: {}", id);
        request.setUserId(id);
        UserPermissionsResponse permissions = userService.assignPermissions(request);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserPermissionsResponse> getUserPermissions(@PathVariable Long id) {
        log.info("Fetching permissions for user: {}", id);
        UserPermissionsResponse permissions = userService.getUserPermissions(id);
        return ResponseEntity.ok(permissions);
    }
}
