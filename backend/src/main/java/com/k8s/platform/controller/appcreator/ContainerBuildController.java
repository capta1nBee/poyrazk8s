package com.k8s.platform.controller.appcreator;

import com.k8s.platform.domain.dto.appcreator.ContainerBuildJobDto;
import com.k8s.platform.domain.dto.appcreator.ContainerBuildRequest;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.appcreator.ContainerBuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Container image build trigger & status polling.
 * Base path: /api/k8s/{clusterUid}/appcreator/build
 */
@RestController
@RequestMapping("/api/k8s/{clusterUid}/appcreator/build")
@RequiredArgsConstructor
public class ContainerBuildController {

    private final ContainerBuildService buildService;
    private final ResourceAuthorizationHelper authHelper;

    /**
     * Start an async build job.
     * Returns immediately with a jobId for polling.
     */
    @PostMapping
    public ResponseEntity<ContainerBuildJobDto> startBuild(
            @PathVariable String clusterUid,
            @RequestBody ContainerBuildRequest request) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "AppCreatorRegistry", "*", "build");
        Long userId = requireUserId();
        ContainerBuildJobDto job = buildService.startBuild(clusterUid, userId, request);
        return ResponseEntity.accepted().body(job);
    }

    /**
     * Poll build status & logs.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<ContainerBuildJobDto> getStatus(
            @PathVariable String clusterUid,
            @PathVariable String jobId) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "AppCreatorRegistry", "*", "view");
        return ResponseEntity.ok(buildService.getJob(jobId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Long requireUserId() {
        var user = authHelper.getCurrentUser();
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return user.getId();
    }
}

