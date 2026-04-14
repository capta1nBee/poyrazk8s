package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.CSINode;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.CSINodeService;
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
public class CSINodeController {

    private final CSINodeService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/csinodes")
    public List<CSINode> listCSINodes(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CSINode", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listCSINodes(clusterId);
    }

    @GetMapping("/csinodes/{name}")
    public CSINode getCSINode(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CSINode", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getCSINode(clusterId, name);
    }
}
