package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.k8s.Lease;
import com.k8s.platform.service.k8s.LeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class LeaseController {

    private final LeaseService service;
        private final ResourceAuthorizationHelper authHelper;
        
    @GetMapping("/leases")
    public List<Lease> listLeases(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "Resource", "*", "view");
        return service.listLeases(clusterUid);
    }

    @GetMapping("/namespaces/{namespace}/leases")
    public List<Lease> listLeasesByNamespace(@PathVariable String clusterUid, @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Lease", "*", "view");
        return service.listLeases(clusterUid, namespace);
    }

    @GetMapping("/namespaces/{namespace}/leases/{name}")
    public Lease getLease(@PathVariable String clusterUid, @PathVariable String namespace, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Lease", name, "view");
        return service.getLease(clusterUid, namespace, name);
    }
}
