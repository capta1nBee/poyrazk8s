package com.k8s.platform.controller.action;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.RoleBindingService;
import com.k8s.platform.service.k8s.K8sClientService;
import com.k8s.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clusters/{clusterUid}/namespaces/{namespace}/rolebindings")
@RequiredArgsConstructor
public class RoleBindingActionController {

    private final RoleBindingService service;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    private boolean hasPermission(String clusterUid, String namespace, String resourceName, String action) {
        User user = securityUtils.getCurrentUser();
        if (user == null)
            return false;
        if (user.getIsSuperadmin() != null && user.getIsSuperadmin())
            return true;
        return authorizationService.hasPermission(user, clusterUid, namespace, "RoleBinding", resourceName, action);
    }

    @GetMapping("/{name}/yaml")
    public ApiResponse<String> getYaml(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name) {
        if (!hasPermission(clusterUid, namespace, name, "view-yaml")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        String yaml = service.getYaml(clusterId, namespace, name);
        return ApiResponse.success(yaml);
    }

    @PutMapping("/{name}/yaml")
    public ApiResponse<String> updateYaml(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name, @RequestBody String yaml) {
        if (!hasPermission(clusterUid, namespace, name, "edit-yaml")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        service.updateYaml(clusterId, namespace, name, yaml);
        return ApiResponse.success("RoleBinding updated successfully");
    }

    @DeleteMapping("/{name}")
    public ApiResponse<String> delete(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name) {
        if (!hasPermission(clusterUid, namespace, name, "delete")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        service.deleteRoleBinding(clusterId, namespace, name);
        return ApiResponse.success("RoleBinding deleted successfully");
    }
}
