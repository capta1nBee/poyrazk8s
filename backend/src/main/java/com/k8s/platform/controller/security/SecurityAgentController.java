package com.k8s.platform.controller.security;

import com.k8s.platform.service.security.SecurityRuleService;
import com.k8s.platform.service.security.MonitoringConfigService;
import com.k8s.platform.service.security.SecurityAlertService;
import com.k8s.platform.dto.response.SecurityAlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Agent endpoints for runtime security
 * These endpoints are called by the runtime security agent
 */
@RestController
@RequestMapping("/api/securityrules")
@RequiredArgsConstructor
@Slf4j
public class SecurityAgentController {

    private final SecurityRuleService securityRuleService;
    private final MonitoringConfigService monitoringConfigService;
    private final SecurityAlertService securityAlertService;

    @Value("${app.agent-api-key}")
    private String agentApiKey;

    private boolean validateAgentAuth(HttpServletRequest request) {
        String providedKey = request.getHeader("X-AGENT-API");
        return agentApiKey != null && agentApiKey.equals(providedKey);
    }

    /**
     * Fetch rules for agent - Used by runtime security agent
     * GET /api/securityrules/rules?cluster=<cluster-uid>
     */
    @GetMapping("/rules")
    public ResponseEntity<?> getRulesForAgent(
            @RequestParam(name = "clusterUid") String clusterUid,
            HttpServletRequest request) {
        
        if (!validateAgentAuth(request)) {
            log.warn("Unauthorized agent access attempt for cluster: {}", clusterUid);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Agent fetching rules for cluster: {}", clusterUid);

        try {
            return ResponseEntity.ok(securityRuleService.getClusterRules(clusterUid));
        } catch (Exception e) {
            log.error("Error fetching rules for agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fetch monitoring configuration - Used by runtime security agent
     * GET /api/securityrules/monitoring-config/<cluster-uid>
     */
    @GetMapping("/monitoring-config/{clusterUid}")
    public ResponseEntity<?> getMonitoringConfig(
            @PathVariable String clusterUid,
            HttpServletRequest request) {

        if (!validateAgentAuth(request)) {
            log.warn("Unauthorized agent access attempt for config: {}", clusterUid);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Agent fetching monitoring config for cluster: {}", clusterUid);

        try {
            return ResponseEntity.ok(monitoringConfigService.getMonitoringConfig(clusterUid));
        } catch (Exception e) {
            log.error("Error fetching monitoring config for agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Post alert - Used by runtime security agent
     * POST /api/securityrules/alerts
     */
    @PostMapping("/alerts")
    public ResponseEntity<?> postAlert(
            @RequestParam(name = "clusterUid") String clusterUid,
            @RequestBody Map<String, Object> alertData,
            HttpServletRequest request) {

        if (!validateAgentAuth(request)) {
            log.warn("Unauthorized agent alert submission for cluster: {}", clusterUid);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Agent posting alert for cluster: {}", clusterUid);

        try {
            SecurityAlertResponse response = securityAlertService.receiveAlert(clusterUid, alertData);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error posting alert from agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check endpoint for agent
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.debug("Health check from agent");
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}
