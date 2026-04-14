package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.CertificateSigningRequest;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.CertificateSigningRequestService;
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
public class CertificateSigningRequestController {

    private final CertificateSigningRequestService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/certificatesigningrequests")
    public List<CertificateSigningRequest> listCertificateSigningRequests(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CertificateSigningRequest", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listCertificateSigningRequests(clusterId);
    }

    @GetMapping("/certificatesigningrequests/{name}")
    public CertificateSigningRequest getCertificateSigningRequest(@PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CertificateSigningRequest", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getCertificateSigningRequest(clusterId, name);
    }
}
