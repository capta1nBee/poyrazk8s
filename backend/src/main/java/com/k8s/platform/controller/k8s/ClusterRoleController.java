package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.k8s.ClusterRole;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.ClusterRoleService;
import com.k8s.platform.service.k8s.K8sClientService;
import com.k8s.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class ClusterRoleController {

    private final ClusterRoleService clusterRoleService;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/clusterroles")
    public List<ClusterRole> listClusterRoles(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterRole", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        List<ClusterRole> all = clusterRoleService.listClusterRoles(clusterId);
        return authHelper.filterAccessibleResources(all, clusterUid, "ClusterRole", "view", null, item -> item.getName());
    }

    @GetMapping("/clusterroles/{name}")
    public ClusterRole getClusterRole(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterRole", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return clusterRoleService.getClusterRole(clusterId, name);
    }
}
