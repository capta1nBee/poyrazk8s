package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.action.CronJobActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/cronjobs")
@RequiredArgsConstructor
public class CronJobActionController {
    private final CronJobActionService cronJobActionService;
    private final ResourceAuthorizationHelper authHelper;

    @PostMapping("/{cronJobName}/run")
    public ApiResponse<String> runNow(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String cronJobName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "CronJob", cronJobName, "run-now");
        cronJobActionService.runNow(clusterId, namespace, cronJobName);
        return ApiResponse.success("CronJob triggered successfully");
    }

    @PostMapping("/{cronJobName}/suspend")
    public ApiResponse<String> suspend(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String cronJobName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "CronJob", cronJobName, "suspend");
        cronJobActionService.suspend(clusterId, namespace, cronJobName);
        return ApiResponse.success("CronJob suspended successfully");
    }

    @PostMapping("/{cronJobName}/resume")
    public ApiResponse<String> resume(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String cronJobName) {
        authHelper.checkPermissionByClusterIdOrThrow(clusterId, namespace, "CronJob", cronJobName, "resume");
        cronJobActionService.resume(clusterId, namespace, cronJobName);
        return ApiResponse.success("CronJob resumed successfully");
    }
}
