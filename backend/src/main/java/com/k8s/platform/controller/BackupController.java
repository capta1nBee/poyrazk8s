package com.k8s.platform.controller;

import com.k8s.platform.domain.entity.Backup;
import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.backup.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupService backupService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;
    private final com.k8s.platform.service.authorization.AuthorizationService authorizationService;
    private final com.k8s.platform.util.SecurityUtils securityUtils;

    /**
     * Get all backups
     */
    @GetMapping
    public ResponseEntity<List<Backup>> getAllBackups(@RequestParam(required = false) String clusterUid) {
        com.k8s.platform.domain.entity.User user = securityUtils.getCurrentUser();
        authHelper.checkPagePermissionOrThrow("backups");

        List<String> allowedClusterUids = authorizationService.getAllowedClusterUids(user, "Backup", "view");
        return ResponseEntity.ok(backupService.getAllBackups(allowedClusterUids, clusterUid));
    }

    /**
     * Get backup by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Backup> getBackupById(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("backups");
        Backup backup = backupService.getBackupById(id)
                .orElseThrow(() -> new RuntimeException("Backup not found: " + id));
        return ResponseEntity.ok(backup);
    }

    /**
     * Get backups for a specific cluster
     */
    @GetMapping("/cluster/{clusterUid}")
    public ResponseEntity<List<Backup>> getBackupsByCluster(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow("backups");
        return ResponseEntity.ok(backupService.getBackupsByClusterUid(clusterUid));
    }

    /**
     * Get backup statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBackupStats(@RequestParam(required = false) String clusterUid) {
        com.k8s.platform.domain.entity.User user = securityUtils.getCurrentUser();
        authHelper.checkPagePermissionOrThrow("backups");

        List<String> allowedClusterUids = authorizationService.getAllowedClusterUids(user, "Backup", "view");
        return ResponseEntity.ok(backupService.getBackupStats(allowedClusterUids, clusterUid));
    }

    /**
     * Trigger backup for all clusters
     */
    @PostMapping("/trigger-all")
    public ResponseEntity<ApiResponse<String>> triggerAllBackups() {
        authHelper.checkPagePermissionOrThrow("backups");
        log.info("Manual backup triggered for all clusters");
        backupService.backupAllClusters("MANUAL");
        return ResponseEntity.ok(ApiResponse.success("Backup triggered for all clusters"));
    }

    /**
     * Trigger backup for a specific cluster
     */
    @PostMapping("/trigger/{clusterUid}")
    public ResponseEntity<ApiResponse<Backup>> triggerClusterBackup(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow("backups");
        log.info("Manual backup triggered for cluster: {}", clusterUid);
        Backup backup = backupService.backupClusterByUid(clusterUid, "MANUAL");
        return ResponseEntity.ok(ApiResponse.success("Backup triggered", backup));
    }

    /**
     * List backup contents (directories and files)
     */
    @GetMapping("/{id}/contents")
    public ResponseEntity<List<Map<String, Object>>> listBackupContents(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String path,
            @RequestParam(required = false) String clusterUid) {
        authHelper.checkPagePermissionOrThrow("backups");
        Backup backup = backupService.getBackupById(id)
                .orElseThrow(() -> new RuntimeException("Backup not found: " + id));

        String effectiveClusterUid = clusterUid != null ? clusterUid : backup.getClusterUid();
        authHelper.logAction(effectiveClusterUid, "Backup", "list contents: " + id + " at path: " + path);

        try {
            return ResponseEntity.ok(backupService.listBackupContents(id, path));
        } catch (IOException e) {
            log.error("Failed to list backup contents: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get backup file content (YAML)
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Map<String, String>> getBackupFileContent(
            @PathVariable Long id,
            @RequestParam String path,
            @RequestParam(required = false) String clusterUid) {
        authHelper.checkPagePermissionOrThrow("backups");
        Backup backup = backupService.getBackupById(id)
                .orElseThrow(() -> new RuntimeException("Backup not found: " + id));

        String effectiveClusterUid = clusterUid != null ? clusterUid : backup.getClusterUid();
        authHelper.logAction(effectiveClusterUid, "Backup", "view yaml: " + id + " at path: " + path);

        try {
            String content = backupService.getBackupFileContent(id, path);
            return ResponseEntity.ok(Map.of("content", content, "path", path));
        } catch (IOException e) {
            log.error("Failed to read backup file: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * Cleanup old backups (keep last N per cluster)
     */
    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> cleanupOldBackups(
            @RequestParam(defaultValue = "10") int keepCount) {
        authHelper.checkPagePermissionOrThrow("backups");
        backupService.cleanupOldBackups(keepCount);
        return ResponseEntity.ok(ApiResponse.success("Cleanup completed"));
    }
}
