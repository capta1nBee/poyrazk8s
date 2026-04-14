package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.action.StatefulSetActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/statefulsets")
@RequiredArgsConstructor
public class StatefulSetActionController {
    private final StatefulSetActionService statefulSetActionService;
    private final ResourceAuthorizationHelper authHelper;

    @PostMapping("/{statefulSetName}/scale")
    public ApiResponse<String> scale(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String statefulSetName,
            @RequestParam Integer replicas) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "StatefulSet", statefulSetName, "scale");
        statefulSetActionService.scale(clusterId, namespace, statefulSetName, replicas);
        return ApiResponse.success("StatefulSet scaled successfully");
    }

    @PostMapping("/{statefulSetName}/restart")
    public ApiResponse<String> restart(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String statefulSetName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "StatefulSet", statefulSetName, "restart");
        statefulSetActionService.restart(clusterId, namespace, statefulSetName);
        return ApiResponse.success("StatefulSet restart initiated");
    }
}
