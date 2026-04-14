package com.k8s.platform.controller.helm;

import com.k8s.platform.domain.dto.helm.HelmRepositoryCreateRequest;
import com.k8s.platform.domain.dto.helm.HelmRepositoryDto;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.helm.HelmRepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/helm-repos")
@RequiredArgsConstructor
public class HelmRepositoryController {

    private final HelmRepoService helmRepoService;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping
    public ResponseEntity<List<HelmRepositoryDto>> getAllRepositories(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        return ResponseEntity.ok(helmRepoService.getAllForCluster(clusterUid));
    }

    @PostMapping
    public ResponseEntity<HelmRepositoryDto> createRepository(
            @PathVariable String clusterUid,
            @RequestBody HelmRepositoryCreateRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        return ResponseEntity.ok(helmRepoService.createRepository(clusterUid, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HelmRepositoryDto> updateRepository(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestBody HelmRepositoryCreateRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        return ResponseEntity.ok(helmRepoService.updateRepository(id, clusterUid, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(
            @PathVariable String clusterUid,
            @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        helmRepoService.deleteRepository(id, clusterUid);
        return ResponseEntity.ok().build();
    }
}
