package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.IPAddress;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.IPAddressService;
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
public class IPAddressController {

    private final IPAddressService service;
    private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/ipaddresses")
    public List<IPAddress> listIPAddresses(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "IPAddress", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        List<IPAddress> allResources = service.listIPAddresses(clusterId);
        return authHelper.filterAccessibleResources(allResources, clusterUid, "IPAddress", "view", item -> item.getNamespace(), item -> item.getName());
    }

    @GetMapping("/ipaddresses/{name}")
    public IPAddress getIPAddress(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "IPAddress", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getIPAddress(clusterId, name);
    }
}
