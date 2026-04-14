package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.action.ServiceActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/services")
@RequiredArgsConstructor
public class ServiceActionController {
    private final ServiceActionService serviceActionService;

    @PostMapping("/{serviceName}/expose")
    public ApiResponse<String> expose(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String serviceName,
            @RequestParam String type) {
        serviceActionService.expose(clusterId, namespace, serviceName, type);
        return ApiResponse.success("Service exposed successfully");
    }

    @PostMapping("/{serviceName}/unexpose")
    public ApiResponse<String> unexpose(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        serviceActionService.unexpose(clusterId, namespace, serviceName);
        return ApiResponse.success("Service unexposed successfully");
    }
}
