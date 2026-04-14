package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.domain.entity.k8s.K8sRole;
import com.k8s.platform.service.k8s.K8sRoleService;
import com.k8s.platform.service.k8s.K8sClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class K8sRoleController {

    private final K8sRoleService service;
    private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;

    @GetMapping("/namespaces/{namespace}/roles")
    public List<K8sRole> listRoles(@PathVariable String clusterUid, @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Role", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        List<K8sRole> allResources = service.listRoles(clusterId, namespace);
        return authHelper.filterAccessibleResources(allResources, clusterUid, "Role", "view", item -> item.getNamespace(), item -> item.getName());
    }

    @GetMapping("/namespaces/{namespace}/roles/{name}")
    public K8sRole getRole(@PathVariable String clusterUid, @PathVariable String namespace, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Role", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getRole(clusterId, namespace, name);
    }
}
