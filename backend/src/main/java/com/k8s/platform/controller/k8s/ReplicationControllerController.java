package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.ReplicationController;
import com.k8s.platform.service.k8s.ReplicationControllerService;
import com.k8s.platform.service.k8s.K8sClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class ReplicationControllerController {

    private final ReplicationControllerService service;
    private final K8sClientService k8sClientService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping("/namespaces/{namespace}/replicationcontrollers")
    public List<ReplicationController> listReplicationControllers(@PathVariable String clusterUid,
            @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ReplicationController", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        List<ReplicationController> allResources = service.listReplicationControllers(clusterId, namespace);
        return authHelper.filterAccessibleResources(allResources, clusterUid, "ReplicationController", "view", item -> item.getNamespace(), item -> item.getName());
    }

    @GetMapping("/namespaces/{namespace}/replicationcontrollers/{name}")
    public ReplicationController getReplicationController(@PathVariable String clusterUid,
            @PathVariable String namespace, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ReplicationController", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getReplicationController(clusterId, namespace, name);
    }
}
