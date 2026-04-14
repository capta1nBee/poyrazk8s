package com.k8s.platform.controller.action;

import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.k8s.ApplicationService;
import com.k8s.platform.service.k8s.K8sClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterUid}/namespaces/{namespace}/applications")
@RequiredArgsConstructor
public class ApplicationActionController {

    private final ApplicationService service;
    private final K8sClientService k8sClientService;

    @GetMapping("/{name}/yaml")
    public ApiResponse<String> getYaml(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name) {
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        String yaml = service.getYaml(clusterId, namespace, name);
        return ApiResponse.success(yaml);
    }

    @PutMapping("/{name}/yaml")
    public ApiResponse<String> updateYaml(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name, @RequestBody String yaml) {
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        service.updateYaml(clusterId, namespace, name, yaml);
        return ApiResponse.success("Application updated successfully");
    }

    @DeleteMapping("/{name}")
    public ApiResponse<String> delete(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name) {
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        service.deleteApplication(clusterId, namespace, name);
        return ApiResponse.success("Application deleted successfully");
    }
}
