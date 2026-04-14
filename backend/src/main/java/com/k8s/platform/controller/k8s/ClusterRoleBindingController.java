package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.ClusterRoleBinding;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.ClusterRoleBindingService;
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
public class ClusterRoleBindingController {

    private final ClusterRoleBindingService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/clusterrolebindings")
    public List<ClusterRoleBinding> listClusterRoleBindings(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterRoleBinding", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listClusterRoleBindings(clusterId);
    }

    @GetMapping("/clusterrolebindings/{name}")
    public ClusterRoleBinding getClusterRoleBinding(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterRoleBinding", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getClusterRoleBinding(clusterId, name);
    }
}
