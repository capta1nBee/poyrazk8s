package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.Application;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.ApplicationService;
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
public class ApplicationController {

    private final ApplicationService service;
    private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/namespaces/{namespace}/applications")
    public List<Application> listApplications(@PathVariable String clusterUid, @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Application", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        List<Application> allResources = service.listApplications(clusterId, namespace);
        return authHelper.filterAccessibleResources(allResources, clusterUid, "Application", "view", item -> item.getNamespace(), item -> item.getName());
    }

    @GetMapping("/namespaces/{namespace}/applications/{name}")
    public Application getApplication(@PathVariable String clusterUid, @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Application", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getApplication(clusterId, namespace, name);
    }
}
