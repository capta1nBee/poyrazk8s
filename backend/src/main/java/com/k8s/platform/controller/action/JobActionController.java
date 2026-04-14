package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.action.JobActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/jobs")
@RequiredArgsConstructor
public class JobActionController {
    private final JobActionService jobActionService;

    @PostMapping("/{jobName}/rerun")
    public ApiResponse<String> rerun(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String jobName) {
        jobActionService.rerun(clusterId, namespace, jobName);
        return ApiResponse.success("Job rerun initiated");
    }

    @PostMapping("/{jobName}/terminate")
    public ApiResponse<String> terminate(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String jobName) {
        jobActionService.terminate(clusterId, namespace, jobName);
        return ApiResponse.success("Job terminated successfully");
    }
}
