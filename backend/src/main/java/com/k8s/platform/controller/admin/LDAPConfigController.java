package com.k8s.platform.controller.admin;

import com.k8s.platform.service.auth.LDAPAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ldap")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class LDAPConfigController {

    private final LDAPAuthenticationService ldapAuthenticationService;

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(ldapAuthenticationService.getLDAPConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, String>> updateConfig(@RequestBody Map<String, String> ldapConfig) {
        ldapAuthenticationService.configureLDAP(ldapConfig);
        return ResponseEntity.ok(Map.of("message", "LDAP configuration updated successfully"));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection() {
        boolean success = ldapAuthenticationService.testLDAPConnection();
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "LDAP connection successful" : "LDAP connection failed"));
    }

    @PostMapping("/enable")
    public ResponseEntity<Map<String, String>> enableLDAP(@RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", false);
        ldapAuthenticationService.setLDAPEnabled(enabled);
        return ResponseEntity.ok(Map.of(
                "message", enabled ? "LDAP enabled" : "LDAP disabled"));
    }

    @PostMapping("/sync-users")
    public ResponseEntity<Map<String, Object>> syncUsers() {
        int count = ldapAuthenticationService.syncUsersFromLDAP();
        return ResponseEntity.ok(Map.of(
                "message", "User sync completed",
                "count", count));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean enabled = ldapAuthenticationService.isLDAPEnabled();
        boolean connected = false;
        
        // Only test connection if LDAP is enabled
        if (enabled) {
            try {
                connected = ldapAuthenticationService.testLDAPConnection();
            } catch (Exception e) {
                connected = false;
            }
        }
        
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "connected", connected));
    }

    /**
     * Search LDAP users by query string
     */
    @GetMapping("/search-users")
    public ResponseEntity<List<Map<String, String>>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        List<Map<String, String>> users = ldapAuthenticationService.searchLDAPUsers(query, limit);
        return ResponseEntity.ok(users);
    }
}
