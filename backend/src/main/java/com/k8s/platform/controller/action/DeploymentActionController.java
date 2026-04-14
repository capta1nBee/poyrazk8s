package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.action.DeploymentActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/deployments")
@RequiredArgsConstructor
public class DeploymentActionController {
    private final DeploymentActionService deploymentActionService;
    private final ResourceAuthorizationHelper authHelper;

    @PostMapping("/{deploymentName}/scale")
    public ApiResponse<String> scale(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String deploymentName,
            @RequestParam Integer replicas) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "Deployment", deploymentName, "scale");
        deploymentActionService.scale(clusterId, namespace, deploymentName, replicas);
        return ApiResponse.success("Deployment scaled successfully");
    }

    @PostMapping("/{deploymentName}/restart")
    public ApiResponse<String> restart(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String deploymentName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "Deployment", deploymentName, "restart");
        deploymentActionService.rolloutRestart(clusterId, namespace, deploymentName);
        return ApiResponse.success("Deployment restart initiated");
    }

    @PostMapping("/{deploymentName}/pause")
    public ApiResponse<String> pause(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String deploymentName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "Deployment", deploymentName, "pause");
        deploymentActionService.pause(clusterId, namespace, deploymentName);
        return ApiResponse.success("Deployment paused successfully");
    }

    @PostMapping("/{deploymentName}/resume")
    public ApiResponse<String> resume(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String deploymentName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "Deployment", deploymentName, "resume");
        deploymentActionService.resume(clusterId, namespace, deploymentName);
        return ApiResponse.success("Deployment resumed successfully");
    }

    @PostMapping("/{deploymentName}/rollback")
    public ApiResponse<String> rollback(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String deploymentName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "Deployment", deploymentName, "rollback");
        deploymentActionService.rollback(clusterId, namespace, deploymentName);
        return ApiResponse.success("Deployment rollback initiated");
    }
}
