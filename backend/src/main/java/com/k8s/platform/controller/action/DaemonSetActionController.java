package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.action.DaemonSetActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/daemonsets")
@RequiredArgsConstructor
public class DaemonSetActionController {
    private final DaemonSetActionService daemonSetActionService;
    private final ResourceAuthorizationHelper authHelper;

    @PostMapping("/{daemonSetName}/restart")
    public ApiResponse<String> restart(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String daemonSetName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "DaemonSet", daemonSetName, "restart");
        daemonSetActionService.restart(clusterId, namespace, daemonSetName);
        return ApiResponse.success("DaemonSet restart initiated");
    }
}
