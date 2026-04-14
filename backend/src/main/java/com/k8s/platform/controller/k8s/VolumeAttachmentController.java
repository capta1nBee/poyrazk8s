package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.VolumeAttachment;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.VolumeAttachmentService;
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
public class VolumeAttachmentController {

    private final VolumeAttachmentService service;
        private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/volumeattachments")
    public List<VolumeAttachment> listVolumeAttachments(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "VolumeAttachment", "*", "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.listVolumeAttachments(clusterId);
    }

    @GetMapping("/volumeattachments/{name}")
    public VolumeAttachment getVolumeAttachment(@PathVariable String clusterUid, @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "VolumeAttachment", name, "view");
        Long clusterId = k8sClientService.getClusterIdByUid(clusterUid);
        return service.getVolumeAttachment(clusterId, name);
    }
}
