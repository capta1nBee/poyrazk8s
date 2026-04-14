package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.k8s.ServiceAccount;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.ServiceAccountService;
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
public class ServiceAccountController {

    private final ServiceAccountService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/namespaces/{namespace}/serviceaccounts")
    public List<ServiceAccount> listServiceAccounts(@PathVariable String clusterUid, @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ServiceAccount", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listServiceAccounts(clusterId, namespace);
    }

    @GetMapping("/namespaces/{namespace}/serviceaccounts/{name}")
    public ServiceAccount getServiceAccount(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ServiceAccount", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getServiceAccount(clusterId, namespace, name);
    }
}
