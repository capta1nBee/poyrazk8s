package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.PriorityClass;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.PriorityClassService;
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
public class PriorityClassController {

    private final PriorityClassService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/priorityclasses")
    public List<PriorityClass> listPriorityClasses(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PriorityClass", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listPriorityClasses(clusterId);
    }

    @GetMapping("/priorityclasses/{name}")
    public PriorityClass getPriorityClass(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PriorityClass", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getPriorityClass(clusterId, name);
    }
}
