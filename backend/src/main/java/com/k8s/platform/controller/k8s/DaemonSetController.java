package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.DaemonSet;
import com.k8s.platform.service.k8s.DaemonSetActionsService;
import com.k8s.platform.service.k8s.DaemonSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class DaemonSetController {

    private final DaemonSetService daemonSetService;
    private final DaemonSetActionsService daemonSetActionsService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    /**
     * List all daemonsets - filtered by authorized namespaces
     */
    @GetMapping("/daemonsets")
    public ResponseEntity<List<DaemonSet>> listAllDaemonSets(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "DaemonSet", "*", "view");
        List<DaemonSet> allDaemonSets = daemonSetService.listDaemonSets(clusterUid, includeDeleted);

        // Filter by authorized namespaces
        List<DaemonSet> filteredDaemonSets = authHelper.filterAccessibleResources(allDaemonSets, clusterUid, "DaemonSet", "view", item -> item.getNamespace(), item -> item.getName());

        return ResponseEntity.ok(filteredDaemonSets);
    }

    @GetMapping("/namespaces/{namespace}/daemonsets")
    public ResponseEntity<List<DaemonSet>> listDaemonSets(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", "*", "view");
        List<DaemonSet> all = daemonSetService.listDaemonSets(clusterUid, namespace, includeDeleted);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "DaemonSet", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/daemonsets/{name}")
    public ResponseEntity<DaemonSet> getDaemonSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", name, "view");
        return ResponseEntity.ok(daemonSetService.getDaemonSet(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/daemonsets/{name}/restart")
    public ResponseEntity<Map<String, String>> restartDaemonSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", name, "restart");
        daemonSetService.restartDaemonSet(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "DaemonSet restarted successfully"));
    }

    @PostMapping("/namespaces/{namespace}/daemonsets/{name}/pause")
    public ResponseEntity<Map<String, String>> pauseDaemonSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", name, "pause");
        daemonSetService.pauseDaemonSet(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "DaemonSet paused successfully"));
    }

    @PostMapping("/namespaces/{namespace}/daemonsets/{name}/resume")
    public ResponseEntity<Map<String, String>> resumeDaemonSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", name, "resume");
        daemonSetService.resumeDaemonSet(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "DaemonSet resumed successfully"));
    }

    @DeleteMapping("/namespaces/{namespace}/daemonsets/{name}")
    public ResponseEntity<Map<String, String>> deleteDaemonSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", name, "delete");
        daemonSetService.deleteDaemonSet(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "DaemonSet deleted successfully"));
    }

    // ==================== ROLLOUT HISTORY ACTIONS ====================

    @GetMapping("/namespaces/{namespace}/daemonsets/{name}/history")
    public ResponseEntity<List<Map<String, Object>>> getDaemonSetHistory(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "DaemonSet", name, "view-history");
        return ResponseEntity.ok(
                daemonSetActionsService.viewRolloutHistory(clusterUid, namespace, name));
    }

    @GetMapping("/namespaces/{namespace}/daemonsets/{name}/history/{revision}")
    public ResponseEntity<Map<String, Object>> getDaemonSetRevisionDetails(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable Integer revision) {
        return ResponseEntity.ok(
                daemonSetActionsService.getRolloutRevisionDetails(clusterUid, namespace, name, revision));
    }

    @PostMapping("/namespaces/{namespace}/daemonsets/{name}/rollback/{revision}")
    public ResponseEntity<Map<String, String>> rollbackToRevision(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable Integer revision) {
        return ResponseEntity.ok(
                daemonSetActionsService.rollbackToRevision(clusterUid, namespace, name, revision));
    }
}
