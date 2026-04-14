package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.action.NodeActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/nodes")
@RequiredArgsConstructor
public class NodeActionController {
    private final NodeActionService nodeActionService;
    private final ResourceAuthorizationHelper authHelper;

    @PostMapping("/{nodeName}/cordon")
    public ApiResponse<String> cordon(
            @PathVariable Long clusterId,
            @PathVariable String nodeName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, null, "Node", nodeName, "cordon");
        nodeActionService.cordon(clusterId, nodeName);
        return ApiResponse.success("Node cordoned successfully");
    }

    @PostMapping("/{nodeName}/uncordon")
    public ApiResponse<String> uncordon(
            @PathVariable Long clusterId,
            @PathVariable String nodeName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, null, "Node", nodeName, "uncordon");
        nodeActionService.uncordon(clusterId, nodeName);
        return ApiResponse.success("Node uncordoned successfully");
    }

    @PostMapping("/{nodeName}/drain")
    public ApiResponse<String> drain(
            @PathVariable Long clusterId,
            @PathVariable String nodeName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, null, "Node", nodeName, "drain");
        nodeActionService.drain(clusterId, nodeName);
        return ApiResponse.success("Node drained successfully");
    }
}
