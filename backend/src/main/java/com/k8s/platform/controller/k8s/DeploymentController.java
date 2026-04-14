package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.Deployment;
import com.k8s.platform.domain.entity.k8s.Pod;
import com.k8s.platform.dto.response.DeploymentResponseDTO;
import com.k8s.platform.service.k8s.DeploymentActionsService;
import com.k8s.platform.service.k8s.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentActionsService deploymentActionsService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    /**
     * List all deployments - filtered by authorized namespaces
     */
    @GetMapping("/deployments")
    public ResponseEntity<List<DeploymentResponseDTO>> listAllDeployments(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "Deployment", "*", "view");
        List<DeploymentResponseDTO> allDeployments = deploymentService.listDeployments(clusterUid, includeDeleted)
                .stream()
                .map(DeploymentResponseDTO::fromEntity)
                .collect(Collectors.toList());

        // Filter by authorized namespaces
        List<DeploymentResponseDTO> filteredDeployments = authHelper.filterAccessibleResources(allDeployments,
                clusterUid, "Deployment", "view", DeploymentResponseDTO::getNamespace, DeploymentResponseDTO::getName);

        return ResponseEntity.ok(filteredDeployments);
    }

    @GetMapping("/namespaces/{namespace}/deployments")
    public ResponseEntity<List<DeploymentResponseDTO>> listDeployments(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", "*", "view");
        List<DeploymentResponseDTO> all = deploymentService.listDeployments(clusterUid, namespace, includeDeleted)
                .stream()
                .map(DeploymentResponseDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Deployment", "view",
                DeploymentResponseDTO::getNamespace, DeploymentResponseDTO::getName));
    }

    @GetMapping("/namespaces/{namespace}/deployments/{name}")
    public ResponseEntity<Deployment> getDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "view");
        return ResponseEntity.ok(deploymentService.getDeployment(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/deployments/{name}/scale")
    public ResponseEntity<Map<String, String>> scaleDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, Integer> request) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "scale");
        int replicas = request.get("replicas");
        deploymentService.scaleDeployment(clusterUid, namespace, name, replicas);
        return ResponseEntity.ok(Map.of("message", "Deployment scaled successfully"));
    }

    @PostMapping("/namespaces/{namespace}/deployments/{name}/restart")
    public ResponseEntity<Map<String, String>> restartDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "restart");
        deploymentService.restartDeployment(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Deployment restarted successfully"));
    }

    @PostMapping("/namespaces/{namespace}/deployments/{name}/pause")
    public ResponseEntity<Map<String, String>> pauseDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "pause");
        deploymentService.pauseDeployment(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Deployment paused successfully"));
    }

    @PostMapping("/namespaces/{namespace}/deployments/{name}/resume")
    public ResponseEntity<Map<String, String>> resumeDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "resume");
        deploymentService.resumeDeployment(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Deployment resumed successfully"));
    }

    @PostMapping("/namespaces/{namespace}/deployments/{name}/rollback")
    public ResponseEntity<Map<String, String>> rollbackDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "rollback");
        deploymentService.undoRollout(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Deployment rolled back successfully"));
    }

    @DeleteMapping("/namespaces/{namespace}/deployments/{name}")
    public ResponseEntity<Map<String, String>> deleteDeployment(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "delete");
        deploymentService.deleteDeployment(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Deployment deleted successfully"));
    }

    // ==================== ENHANCED DEPLOYMENT ACTIONS ====================

    @GetMapping("/namespaces/{namespace}/deployments/{name}/history")
    public ResponseEntity<List<Map<String, Object>>> getDeploymentHistory(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "view-history");
        return ResponseEntity.ok(
                deploymentActionsService.viewRolloutHistory(clusterUid, namespace, name));
    }

    @GetMapping("/namespaces/{namespace}/deployments/{name}/history/{revision}")
    public ResponseEntity<Map<String, Object>> getDeploymentRevisionDetails(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable Integer revision) {
        return ResponseEntity.ok(
                deploymentActionsService.getRolloutRevisionDetails(clusterUid, namespace, name, revision));
    }

    @PostMapping("/namespaces/{namespace}/deployments/{name}/rollback/{revision}")
    public ResponseEntity<Map<String, String>> rollbackToRevision(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @PathVariable Integer revision) {
        return ResponseEntity.ok(
                deploymentActionsService.rollbackToRevision(clusterUid, namespace, name, revision));
    }

    @GetMapping("/namespaces/{namespace}/deployments/{name}/pods")
    public ResponseEntity<List<Pod>> getDeploymentPods(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Deployment", name, "view-pods");
        Deployment deployment = deploymentService.getDeployment(clusterUid, namespace, name);
        List<Pod> allResources = deploymentActionsService.viewPods(clusterUid, namespace, name,
                deployment.getClusterId());
        return ResponseEntity.ok(authHelper.filterAccessibleResources(allResources, clusterUid, "Pod", "view",
                Pod::getNamespace, Pod::getName));
    }

}
