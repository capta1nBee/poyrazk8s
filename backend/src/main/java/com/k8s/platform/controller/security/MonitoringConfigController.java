package com.k8s.platform.controller.security;

import com.k8s.platform.domain.entity.MonitoringConfig;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.dto.response.MonitoringConfigResponse;
import com.k8s.platform.service.security.MonitoringConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/security/monitoring-config")
@RequiredArgsConstructor
@Slf4j
public class MonitoringConfigController {

    private final MonitoringConfigService monitoringConfigService;
    private final UserRepository userRepository;

    /**
     * Get monitoring config for a cluster
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<MonitoringConfigResponse> getMonitoringConfig(@RequestParam String clusterUid) {
        log.info("Fetching monitoring config for cluster: {}", clusterUid);

        MonitoringConfigResponse config = monitoringConfigService.getMonitoringConfigResponse(clusterUid);
        return ResponseEntity.ok(config);
    }

    /**
     * Update monitoring config
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<MonitoringConfigResponse> updateMonitoringConfig(
            @RequestParam String clusterUid,
            @RequestBody MonitoringConfig newConfig,
            Authentication authentication) {
        log.info("Updating monitoring config for cluster: {}", clusterUid);

        User user = getUserFromAuthentication(authentication);
        MonitoringConfigResponse config = monitoringConfigService.createOrUpdateConfig(clusterUid, newConfig, user);

        return ResponseEntity.ok(config);
    }

    /**
     * Enable specific tracepoint
     */
    @PatchMapping("/tracepoint/{tracepointType}/enable")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<MonitoringConfigResponse> enableTracepoint(
            @RequestParam String clusterUid,
            @PathVariable String tracepointType,
            Authentication authentication) {
        log.info("Enabling tracepoint: {} for cluster: {}", tracepointType, clusterUid);

        User user = getUserFromAuthentication(authentication);
        MonitoringConfigResponse config = monitoringConfigService.enableTracepoint(clusterUid, tracepointType, user);

        return ResponseEntity.ok(config);
    }

    /**
     * Disable specific tracepoint
     */
    @PatchMapping("/tracepoint/{tracepointType}/disable")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<MonitoringConfigResponse> disableTracepoint(
            @RequestParam String clusterUid,
            @PathVariable String tracepointType,
            Authentication authentication) {
        log.info("Disabling tracepoint: {} for cluster: {}", tracepointType, clusterUid);

        User user = getUserFromAuthentication(authentication);
        MonitoringConfigResponse config = monitoringConfigService.disableTracepoint(clusterUid, tracepointType, user);

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
