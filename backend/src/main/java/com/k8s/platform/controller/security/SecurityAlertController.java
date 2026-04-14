package com.k8s.platform.controller.security;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.dto.request.AcknowledgeAlertRequest;
import com.k8s.platform.dto.request.ResolveAlertRequest;
import com.k8s.platform.dto.response.SecurityAlertResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.security.SecurityAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security/alerts")
@RequiredArgsConstructor
@Slf4j
public class SecurityAlertController {

    private final SecurityAlertService securityAlertService;
    private final UserRepository userRepository;
    private final ResourceAuthorizationHelper authHelper;

    /**
     * Get alerts for a cluster with pagination
     */
    @GetMapping
    public ResponseEntity<Page<SecurityAlertResponse>> getAlerts(
            @RequestParam String clusterUid,
            Pageable pageable) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching alerts for cluster: {}", clusterUid);
        Page<SecurityAlertResponse> alerts = securityAlertService.getAlerts(clusterUid, pageable);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get pending (unacknowledged and unresolved) alerts
     */
    @GetMapping("/pending")
    public ResponseEntity<List<SecurityAlertResponse>> getPendingAlerts(@RequestParam String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching pending alerts for cluster: {}", clusterUid);
        List<SecurityAlertResponse> alerts = securityAlertService.getPendingAlerts(clusterUid);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get recent alerts
     */
    @GetMapping("/recent")
    public ResponseEntity<List<SecurityAlertResponse>> getRecentAlerts(
            @RequestParam String clusterUid,
            @RequestParam(defaultValue = "20") int limit) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching recent {} alerts for cluster: {}", limit, clusterUid);
        List<SecurityAlertResponse> alerts = securityAlertService.getRecentAlerts(clusterUid, limit);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alert by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<SecurityAlertResponse> getAlert(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Fetching alert: {}", id);
        SecurityAlertResponse alert = securityAlertService.getAlertById(id);
        return ResponseEntity.ok(alert);
    }

    /**
     * Search alerts with advanced filters
     */
    @GetMapping("/search")
    public ResponseEntity<Page<SecurityAlertResponse>> searchAlerts(
            @RequestParam String clusterUid,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String podName,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) Long startDateTimestamp,
            Pageable pageable) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Searching alerts for cluster: {} with filters", clusterUid);

        LocalDateTime startDate = startDateTimestamp != null
                ? LocalDateTime.ofEpochSecond(startDateTimestamp / 1000, 0, java.time.ZoneOffset.UTC)
                : null;

        Page<SecurityAlertResponse> alerts = securityAlertService.searchAlerts(
                clusterUid, priority, namespace, podName, acknowledged, resolved, startDate, pageable);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alerts by pod
     */
    @GetMapping("/pod/{podName}")
    public ResponseEntity<Page<SecurityAlertResponse>> getAlertsByPod(
            @RequestParam String clusterUid,
            @PathVariable String podName,
            Pageable pageable) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching alerts for pod: {} in cluster: {}", podName, clusterUid);
        Page<SecurityAlertResponse> alerts = securityAlertService.getAlertsByPod(clusterUid, podName, pageable);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alerts by namespace
     */
    @GetMapping("/namespace/{namespace}")
    public ResponseEntity<Page<SecurityAlertResponse>> getAlertsByNamespace(
            @RequestParam String clusterUid,
            @PathVariable String namespace,
            Pageable pageable) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching alerts for namespace: {} in cluster: {}", namespace, clusterUid);
        Page<SecurityAlertResponse> alerts = securityAlertService.getAlertsByNamespace(clusterUid, namespace, pageable);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Acknowledge alert
     */
    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<SecurityAlertResponse> acknowledgeAlert(
            @PathVariable Long id,
            @RequestBody AcknowledgeAlertRequest request,
            Authentication authentication) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Acknowledging alert: {}", id);
        User user = getUserFromAuthentication(authentication);
        SecurityAlertResponse alert = securityAlertService.acknowledgeAlert(id, request, user);
        return ResponseEntity.ok(alert);
    }

    /**
     * Resolve alert
     */
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<SecurityAlertResponse> resolveAlert(
            @PathVariable Long id,
            @RequestBody ResolveAlertRequest request,
            Authentication authentication) {
        authHelper.checkPagePermissionOrThrow("security");
        log.info("Resolving alert: {}", id);
        User user = getUserFromAuthentication(authentication);
        SecurityAlertResponse alert = securityAlertService.resolveAlert(id, request, user);
        return ResponseEntity.ok(alert);
    }

    /**
     * Get statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(@RequestParam String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "security");
        log.info("Fetching alert statistics for cluster: {}", clusterUid);
        Map<String, Long> stats = new HashMap<>();
        stats.put("pendingAlerts", securityAlertService.getPendingAlertsCount(clusterUid));
        stats.put("criticalAlerts", securityAlertService.getCriticalAlertsCount(clusterUid));
        return ResponseEntity.ok(stats);
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
