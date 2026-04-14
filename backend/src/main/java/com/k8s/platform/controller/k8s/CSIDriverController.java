package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.CSIDriver;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.CSIDriverService;
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
public class CSIDriverController {

    private final CSIDriverService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/csidrivers")
    public List<CSIDriver> listCSIDrivers(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CSIDriver", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listCSIDrivers(clusterId);
    }

    @GetMapping("/csidrivers/{name}")
    public CSIDriver getCSIDriver(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CSIDriver", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getCSIDriver(clusterId, name);
    }
}
