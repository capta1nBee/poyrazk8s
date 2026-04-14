package com.k8s.platform.controller.authorization;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.authorization.UIPermission;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.service.authorization.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/authorization")
@RequiredArgsConstructor
public class AuthorizationController {

    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        } else if (authentication.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) authentication.getPrincipal()).getUsername();
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
        } else {
            String username = authentication.getName();
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
        }
    }

    @GetMapping("/permissions/ui")
    public ResponseEntity<UIPermission> getMyUIPermissions(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        return ResponseEntity.ok(authorizationService.getUserUIPermissions(user.getId()));
    }

    /**
     * Get all pages the current user has access to
     */
    @GetMapping("/permissions/ui/all")
    public ResponseEntity<Map<String, Set<String>>> getMyAllUIPermissions(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        Set<String> pages = authorizationService.getUserPages(user);
        return ResponseEntity.ok(Map.of("pages", pages));
    }

    /**
     * Get all active pages (public endpoint)
     */
    @GetMapping("/pages")
    public ResponseEntity<List<String>> getAllActivePages() {
        return ResponseEntity.ok(authorizationService.getAllActivePageNames());
    }

    /**
     * Get all resource kinds from active pages
     */
    @GetMapping("/resource-kinds")
    public ResponseEntity<List<String>> getAllResourceKinds() {
        return ResponseEntity.ok(authorizationService.getAllResourceKinds());
    }

    /**
     * Get all pages with their details (name, displayName, resourceKind)
     */
    @GetMapping("/pages/details")
    public ResponseEntity<List<Map<String, String>>> getAllPagesWithDetails() {
        return ResponseEntity.ok(authorizationService.getAllPagesWithDetails());
    }

    /**
     * Batch check multiple permissions at once
     */
    @PostMapping("/check/batch")
    public ResponseEntity<Map<String, Map<String, Boolean>>> checkBatchPermissions(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        User user = getUserFromAuthentication(authentication);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> checks = (List<Map<String, String>>) request.get("checks");

        Map<String, Map<String, Boolean>> results = new java.util.HashMap<>();

        if (checks != null) {
            for (Map<String, String> check : checks) {
                String key = check.get("key");
                String clusterUid = check.get("clusterUid");
                String namespace = check.get("namespace");
                String resourceKind = check.get("resourceKind");
                String resourceName = check.get("resourceName");
                String action = check.get("action");

                boolean hasPermission = authorizationService.hasPermission(
                        user,
                        clusterUid,
                        namespace,
                        resourceKind,
                        resourceName,
                        action);

                results.put(key, Map.of("hasPermission", hasPermission));
            }
        }

        return ResponseEntity.ok(results);
    }

    @PostMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        User user = getUserFromAuthentication(authentication);

        boolean hasPermission = authorizationService.hasPermission(
                user,
                request.get("clusterUid"),
                request.get("namespace"),
                request.get("resourceKind"),
                request.get("resourceName"),
                request.get("action"));

        return ResponseEntity.ok(Map.of("hasPermission", hasPermission));
    }

    @GetMapping("/check/page/{pageName}")
    public ResponseEntity<Map<String, Boolean>> checkPageAccess(
            Authentication authentication,
            @PathVariable String pageName) {
        User user = getUserFromAuthentication(authentication);
        boolean hasAccess = authorizationService.hasPageAccess(user, pageName);
        return ResponseEntity.ok(Map.of("hasAccess", hasAccess));
    }

    // Admin-only endpoints
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/users/{userId}/permissions/ui")
    public ResponseEntity<UIPermission> getUserUIPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(authorizationService.getUserUIPermissions(userId));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/users/{userId}/permissions/ui")
    public ResponseEntity<UIPermission> createUIPermission(
            @PathVariable Long userId,
            @RequestBody UIPermission permission) {
        permission.setUserId(userId);
        return ResponseEntity.ok(authorizationService.saveUIPermission(permission));
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/users/{userId}/permissions")
    public ResponseEntity<Map<String, String>> deleteUserPermissions(@PathVariable Long userId) {
        authorizationService.deleteUserPermissions(userId);
        return ResponseEntity.ok(Map.of("message", "Permissions deleted successfully"));
    }
}
