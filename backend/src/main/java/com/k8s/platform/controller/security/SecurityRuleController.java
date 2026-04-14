package com.k8s.platform.controller.security;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.domain.repository.SecurityRuleRepository;
import com.k8s.platform.dto.request.CreateSecurityRuleRequest;
import com.k8s.platform.dto.request.UpdateSecurityRuleRequest;
import com.k8s.platform.dto.response.SecurityRuleResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.security.SecurityRuleService;
import com.k8s.platform.service.security.MonitoringConfigService;
import com.k8s.platform.dto.response.MonitoringConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security/rules")
@RequiredArgsConstructor
@Slf4j
public class SecurityRuleController {

    private final SecurityRuleService securityRuleService;
    private final MonitoringConfigService monitoringConfigService;
    private final UserRepository userRepository;
    private final ResourceAuthorizationHelper authHelper;

    /**
     * Get all rules for a cluster (UI endpoint)
     */
    @GetMapping
    public ResponseEntity<Page<SecurityRuleResponse>> getRules(
            @RequestParam String clusterUid,
            Pageable pageable) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching rules for cluster: {}", clusterUid);
        Page<SecurityRuleResponse> rules = securityRuleService.getClusterRules(clusterUid, pageable);
        return ResponseEntity.ok(rules);
    }

    /**
     * Search rules
     */
    @GetMapping("/search")
    public ResponseEntity<Page<SecurityRuleResponse>> searchRules(
            @RequestParam String clusterUid,
            @RequestParam String searchTerm,
            Pageable pageable) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Searching rules for cluster: {} with term: {}", clusterUid, searchTerm);
        Page<SecurityRuleResponse> rules = securityRuleService.searchRules(clusterUid, searchTerm, pageable);
        return ResponseEntity.ok(rules);
    }

    /**
     * Get rule by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<SecurityRuleResponse> getRule(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Fetching rule: {}", id);
        SecurityRuleResponse rule = securityRuleService.getRuleById(id);
        return ResponseEntity.ok(rule);
    }

    /**
     * Create new rule
     */
    @PostMapping
    public ResponseEntity<SecurityRuleResponse> createRule(
            @RequestBody CreateSecurityRuleRequest request,
            Authentication authentication) {
        authHelper.checkPagePermissionOrThrow(request.getClusterUid(), "security");
        log.info("Creating rule: {} for cluster: {}", request.getName(), request.getClusterUid());
        User user = getUserFromAuthentication(authentication);
        SecurityRuleResponse rule = securityRuleService.createRule(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * Update rule
     */
    @PutMapping("/{id}")
    public ResponseEntity<SecurityRuleResponse> updateRule(
            @PathVariable Long id,
            @RequestBody UpdateSecurityRuleRequest request) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Updating rule: {}", id);
        SecurityRuleResponse rule = securityRuleService.updateRule(id, request);
        return ResponseEntity.ok(rule);
    }

    /**
     * Delete rule
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRule(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Deleting rule: {}", id);
        securityRuleService.deleteRule(id);
        return ResponseEntity.ok(Map.of("message", "Rule deleted successfully"));
    }

    /**
     * Toggle rule enabled/disabled
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<SecurityRuleResponse> toggleRule(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Toggling rule: {}", id);
        SecurityRuleResponse rule = securityRuleService.toggleRule(id);
        return ResponseEntity.ok(rule);
    }

    /**
     * Get rules by priority
     */
    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<SecurityRuleResponse>> getRulesByPriority(
            @RequestParam String clusterUid,
            @PathVariable String priority) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching {} priority rules for cluster: {}", priority, clusterUid);
        List<SecurityRuleResponse> rules = securityRuleService.getRulesByPriority(clusterUid, priority);
        return ResponseEntity.ok(rules);
    }

    /**
     * Get statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(@RequestParam String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching rule statistics for cluster: {}", clusterUid);
        Map<String, Long> stats = new HashMap<>();
        stats.put("activeRules", securityRuleService.getActiveRulesCount(clusterUid));
        stats.put("enabledRules", securityRuleService.getEnabledRulesCount(clusterUid));
        return ResponseEntity.ok(stats);
    }

    /**
     * Get monitoring config
     */
    @GetMapping("/monitoring-config")
    public ResponseEntity<MonitoringConfigResponse> getMonitoringConfig(@RequestParam String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching monitoring config for cluster: {}", clusterUid);
        MonitoringConfigResponse config = monitoringConfigService.getMonitoringConfigResponse(clusterUid);
        return ResponseEntity.ok(config);
    }

    /**
     * Helper to get user from authentication
     */
    private User getUserFromAuthentication(Authentication authentication) {
        final String username;
        if (authentication.getPrincipal() instanceof UserDetails) {
            username = ((UserDetails) authentication.getPrincipal()).getUsername();
        } else {
            username = authentication.getPrincipal().toString();
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
}
