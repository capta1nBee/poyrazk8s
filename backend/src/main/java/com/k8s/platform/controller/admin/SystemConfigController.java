package com.k8s.platform.controller.admin;

import com.k8s.platform.domain.entity.SystemConfig;
import com.k8s.platform.dto.request.MailConfigRequest;
import com.k8s.platform.service.config.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<List<SystemConfig>> getAllSettings() {
        return ResponseEntity.ok(systemConfigService.getAllConfigs());
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<Map<String, String>> getSettingsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(systemConfigService.getConfigByCategory(category));
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, String>> getSetting(@PathVariable String key) {
        String value = systemConfigService.getConfigValue(key);
        return ResponseEntity.ok(Map.of("key", key, "value", value != null ? value : ""));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> updateSetting(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        String value = (String) request.get("value");
        boolean encrypt = (boolean) request.getOrDefault("encrypt", false);

        systemConfigService.setConfigValue(key, value, encrypt);
        return ResponseEntity.ok(Map.of("message", "Setting updated successfully"));
    }

    @PutMapping("/{key}")
    public ResponseEntity<Map<String, String>> updateSettingValue(
            @PathVariable String key,
            @RequestBody Map<String, String> request) {

        String value = request.get("value");
        systemConfigService.updateConfig(key, value);
        return ResponseEntity.ok(Map.of("message", "Setting updated successfully"));
    }

    // Mail configuration
    @GetMapping("/mail")
    public ResponseEntity<Map<String, String>> getMailConfig() {
        return ResponseEntity.ok(systemConfigService.getMailConfig());
    }

    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> updateMailConfig(@RequestBody Map<String, String> mailConfig) {
        // Username and password are optional - allow empty/null values
        // Convert Map to proper format, handling optional username/password
        Map<String, String> configMap = new HashMap<>();
        mailConfig.forEach((key, value) -> {
            // Allow empty strings for username and password (they are optional)
            if (value != null) {
                configMap.put(key, value);
            } else if (key.contains("username") || key.contains("password")) {
                // Explicitly set to empty string for optional fields
                configMap.put(key, "");
            } else {
                configMap.put(key, value != null ? value : "");
            }
        });
        
        systemConfigService.updateMailConfig(configMap);
        return ResponseEntity.ok(Map.of("message", "Mail configuration updated successfully"));
    }

    @PostMapping("/mail/test")
    public ResponseEntity<Map<String, Object>> testMailConnection() {
        try {
            boolean success = systemConfigService.testMailConnection();
            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "message", success ? "Mail connection successful" : "Mail connection failed"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Mail connection failed: " + e.getMessage()));
        }
    }

    // Cluster configuration
    @GetMapping("/cluster")
    public ResponseEntity<Map<String, String>> getClusterConfig() {
        return ResponseEntity.ok(systemConfigService.getClusterConfig());
    }

    // Watcher configuration
    @GetMapping("/watcher")
    public ResponseEntity<Map<String, String>> getWatcherConfig() {
        return ResponseEntity.ok(systemConfigService.getWatcherConfig());
    }

    // Security configuration
    @GetMapping("/security")
    public ResponseEntity<Map<String, String>> getSecurityConfig() {
        return ResponseEntity.ok(systemConfigService.getSecurityConfig());
    }

    // Read-only mode
    @PostMapping("/read-only-mode")
    public ResponseEntity<Map<String, Object>> setReadOnlyMode(@RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", false);
        systemConfigService.setReadOnlyMode(enabled);
        return ResponseEntity.ok(Map.of(
                "message", enabled ? "Read-only mode enabled" : "Read-only mode disabled",
                "enabled", enabled));
    }

    @GetMapping("/read-only-mode")
    public ResponseEntity<Map<String, Boolean>> getReadOnlyMode() {
        return ResponseEntity.ok(Map.of("enabled", systemConfigService.isReadOnlyMode()));
    }

    // Maintenance mode
    @PostMapping("/maintenance-mode")
    public ResponseEntity<Map<String, Object>> setMaintenanceMode(@RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", false);
        systemConfigService.setMaintenanceMode(enabled);
        return ResponseEntity.ok(Map.of(
                "message", enabled ? "Maintenance mode enabled" : "Maintenance mode disabled",
                "enabled", enabled));
    }

    @GetMapping("/maintenance-mode")
    public ResponseEntity<Map<String, Boolean>> getMaintenanceMode() {
        return ResponseEntity.ok(Map.of("enabled", systemConfigService.isMaintenanceMode()));
    }
}
