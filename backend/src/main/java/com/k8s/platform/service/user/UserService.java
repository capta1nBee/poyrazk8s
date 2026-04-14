package com.k8s.platform.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.Role;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.UserPolicy;
import com.k8s.platform.domain.repository.RoleRepository;
import com.k8s.platform.domain.repository.UserPolicyRepository;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.dto.request.AssignPermissionsRequest;
import com.k8s.platform.dto.request.CreateUserRequest;
import com.k8s.platform.dto.request.UpdateUserRequest;
import com.k8s.platform.dto.response.UserPermissionsResponse;
import com.k8s.platform.dto.response.UserResponse;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final CasbinPermissionService casbinPermissionService;

    /** Build a UserResponse enriched with Casbin-assigned roles (distinct, sorted). */
    private UserResponse toResponse(User user) {
        List<String> casbinRoles = casbinPermissionService
                .getUserRoleBindings(user.getUsername())
                .stream()
                .map(rule -> rule.getV1())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        // Fall back to entity roles if Casbin returns nothing and entity has something
        List<String> entityRoles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList());
        List<String> roles = casbinRoles.isEmpty() ? entityRoles : casbinRoles;
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .authType(user.getAuthType())
                .isActive(user.getIsActive())
                .isSuperadmin(user.getIsSuperadmin())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user: {}", request.getUsername());

        // Validate unique username and email
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        // Create user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null)
                .authType(request.getAuthType() != null ? request.getAuthType() : "LOCAL")
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .isSuperadmin(request.getIsSuperadmin() != null ? request.getIsSuperadmin() : false)
                .roles(new HashSet<>())
                .build();

        // Assign roles
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> roles = request.getRoles().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseThrow(() -> new RuntimeException("Role not found: " + roleName)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        user = userRepository.save(user);
        log.info("User created successfully: {}", user.getId());

        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        log.info("Updating user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        // Update fields
        if (request.getUsername() != null) {
            if (!request.getUsername().equals(user.getUsername()) && 
                userRepository.existsByUsername(request.getUsername())) {
                throw new RuntimeException("Username already exists: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null) {
            if (!request.getEmail().equals(user.getEmail()) && 
                userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        if (request.getIsSuperadmin() != null) {
            user.setIsSuperadmin(request.getIsSuperadmin());
        }

        // Update roles
        if (request.getRoles() != null) {
            Set<Role> roles = request.getRoles().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseThrow(() -> new RuntimeException("Role not found: " + roleName)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        user = userRepository.save(user);
        log.info("User updated successfully: {}", user.getId());

        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        user.setDeletedAt(java.time.LocalDateTime.now());
        user.setIsActive(false);
        userRepository.save(user);

        // Deactivate user policies
        List<UserPolicy> policies = userPolicyRepository.findByUserIdAndIsActiveTrue(id);
        policies.forEach(policy -> policy.setIsActive(false));
        userPolicyRepository.saveAll(policies);

        log.info("User deleted successfully: {}", id);
    }

    @Transactional
    public UserPermissionsResponse assignPermissions(AssignPermissionsRequest request) {
        log.info("Assigning permissions to user: {}", request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));

        try {
            // Convert request to JSON
            String assignmentsJson = objectMapper.writeValueAsString(request.getAssignments());
            String uiPermissionsJson = objectMapper.writeValueAsString(request.getUiPermissions());
            String rolesJson = objectMapper.writeValueAsString(request.getRoles());

            // Deactivate existing policies
            List<UserPolicy> existingPolicies = userPolicyRepository.findByUserIdAndIsActiveTrue(user.getId());
            existingPolicies.forEach(policy -> policy.setIsActive(false));
            userPolicyRepository.saveAll(existingPolicies);

            // Create new policy
            UserPolicy policy = UserPolicy.builder()
                    .userId(user.getId())
                    .subjectType("user")
                    .subjectName(user.getUsername())
                    .assignmentsJson(assignmentsJson)
                    .uiPermissionsJson(uiPermissionsJson)
                    .rolesJson(rolesJson)
                    .isActive(true)
                    .build();

            userPolicyRepository.save(policy);

            log.info("Permissions assigned successfully to user: {}", user.getId());

            return getUserPermissions(user.getId());
        } catch (Exception e) {
            log.error("Failed to assign permissions", e);
            throw new RuntimeException("Failed to assign permissions: " + e.getMessage());
        }
    }

    public UserPermissionsResponse getUserPermissions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<UserPolicy> policies = userPolicyRepository.findByUserIdAndIsActiveTrue(userId);

        if (policies.isEmpty()) {
            return UserPermissionsResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .subject(UserPermissionsResponse.Subject.builder()
                            .type("user")
                            .name(user.getUsername())
                            .build())
                    .assignments(List.of())
                    .uiPermissions(UserPermissionsResponse.UIPermissions.builder()
                            .pages(List.of())
                            .features(List.of())
                            .build())
                    .roles(List.of())
                    .build();
        }

        try {
            UserPolicy policy = policies.get(0); // Get the latest active policy

            List<UserPermissionsResponse.Assignment> assignments = List.of();
            if (policy.getAssignmentsJson() != null && !policy.getAssignmentsJson().isEmpty()) {
                assignments = objectMapper.readValue(
                        policy.getAssignmentsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, UserPermissionsResponse.Assignment.class));
            }

            UserPermissionsResponse.UIPermissions uiPermissions = UserPermissionsResponse.UIPermissions.builder()
                    .pages(List.of())
                    .features(List.of())
                    .build();
            if (policy.getUiPermissionsJson() != null && !policy.getUiPermissionsJson().isEmpty()) {
                uiPermissions = objectMapper.readValue(
                        policy.getUiPermissionsJson(),
                        UserPermissionsResponse.UIPermissions.class);
            }

            List<String> roles = List.of();
            if (policy.getRolesJson() != null && !policy.getRolesJson().isEmpty()) {
                roles = objectMapper.readValue(
                        policy.getRolesJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            return UserPermissionsResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .subject(UserPermissionsResponse.Subject.builder()
                            .type("user")
                            .name(user.getUsername())
                            .build())
                    .assignments(assignments)
                    .uiPermissions(uiPermissions)
                    .roles(roles)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse user permissions", e);
            throw new RuntimeException("Failed to parse user permissions: " + e.getMessage());
        }
    }
}

