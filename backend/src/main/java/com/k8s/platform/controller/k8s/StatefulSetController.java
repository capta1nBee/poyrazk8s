package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.StatefulSet;
import com.k8s.platform.service.k8s.StatefulSetActionsService;
import com.k8s.platform.service.k8s.StatefulSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class StatefulSetController {

    private final StatefulSetService statefulSetService;
    private final StatefulSetActionsService statefulSetActionsService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    /**
     * List all statefulsets - filtered by authorized namespaces
     */
    @GetMapping("/statefulsets")
    public ResponseEntity<List<StatefulSet>> listAllStatefulSets(
            @PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "StatefulSet", "*", "view");
        List<StatefulSet> allStatefulSets = statefulSetService.listStatefulSets(clusterUid, false); // Assuming
                                                                                                    // includeDeleted=false
                                                                                                    // by default for
                                                                                                    // this endpoint

        // Filter by authorized namespaces
        List<StatefulSet> filteredStatefulSets = authHelper.filterAccessibleResources(allStatefulSets, clusterUid, "StatefulSet", "view", item -> item.getNamespace(), item -> item.getName());

        return ResponseEntity.ok(filteredStatefulSets);
    }

    @GetMapping("/namespaces/{namespace}/statefulsets")
    public ResponseEntity<List<StatefulSet>> listStatefulSets(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", "*", "view");
        List<StatefulSet> all = statefulSetService.listStatefulSets(clusterUid, namespace, includeDeleted);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "StatefulSet", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/statefulsets/{name}")
    public ResponseEntity<StatefulSet> getStatefulSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", name, "view");
        return ResponseEntity.ok(statefulSetService.getStatefulSet(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/statefulsets/{name}/scale")
    public ResponseEntity<Map<String, String>> scaleStatefulSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, Integer> request) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", name, "scale");
        int replicas = request.get("replicas");
        statefulSetService.scaleStatefulSet(clusterUid, namespace, name, replicas);
        return ResponseEntity.ok(Map.of("message", "StatefulSet scaled successfully"));
    }

    @PostMapping("/namespaces/{namespace}/statefulsets/{name}/restart")
    public ResponseEntity<Map<String, String>> restartStatefulSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", name, "restart");
        statefulSetService.restartStatefulSet(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "StatefulSet restarted successfully"));
    }

    @DeleteMapping("/namespaces/{namespace}/statefulsets/{name}")
    public ResponseEntity<Map<String, String>> deleteStatefulSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", name, "delete");
        statefulSetService.deleteStatefulSet(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "StatefulSet deleted successfully"));
    }

    @DeleteMapping("/namespaces/{namespace}/statefulsets/{statefulSetName}/pods/{ordinal}")
    public ResponseEntity<Map<String, String>> deletePodByOrdinal(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String statefulSetName,
            @PathVariable int ordinal) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", statefulSetName, "delete"); // Assuming
                                                                                                            // delete
                                                                                                            // permission
                                                                                                            // for
                                                                                                            // deleting
                                                                                                            // pods
        statefulSetService.deletePodByOrdinal(clusterUid, namespace, statefulSetName, ordinal);
        return ResponseEntity.ok(Map.of("message", "StatefulSet pod deleted successfully"));
    }

    // ==================== ROLLOUT HISTORY ACTIONS ====================

    @GetMapping("/namespaces/{namespace}/statefulsets/{name}/history")
    public ResponseEntity<List<Map<String, Object>>> getRolloutHistory(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "StatefulSet", name, "view-history");
        return ResponseEntity.ok(
                statefulSetActionsService.viewRolloutHistory(clusterUid, namespace, name));
    }

    @GetMapping("/namespaces/{namespace}/statefulsets/{name}/history/{revision}")
    public ResponseEntity<Map<String, Object>> getStatefulSetRevisionDetails(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable Integer revision) {
        return ResponseEntity.ok(
                statefulSetActionsService.getRolloutRevisionDetails(clusterUid, namespace, name, revision));
    }

    @PostMapping("/namespaces/{namespace}/statefulsets/{name}/rollback/{revision}")
    public ResponseEntity<Map<String, String>> rollbackToRevision(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable Integer revision) {
        return ResponseEntity.ok(
                statefulSetActionsService.rollbackToRevision(clusterUid, namespace, name, revision));
    }
}
