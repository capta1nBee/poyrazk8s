package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.IngressClass;
import com.k8s.platform.service.k8s.IngressClassService;
import com.k8s.platform.service.k8s.K8sClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class IngressClassController {

    private final IngressClassService service;
    private final K8sClientService k8sClientService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping("/ingressclasses")
    public List<IngressClass> listIngressClasses(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "IngressClass", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        List<IngressClass> allResources = service.listIngressClasses(clusterId);
        return authHelper.filterAccessibleResources(allResources, clusterUid, "IngressClass", "view", item -> item.getNamespace(), item -> item.getName());
    }

    @GetMapping("/ingressclasses/{name}")
    public IngressClass getIngressClass(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "IngressClass", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getIngressClass(clusterId, name);
    }
}
