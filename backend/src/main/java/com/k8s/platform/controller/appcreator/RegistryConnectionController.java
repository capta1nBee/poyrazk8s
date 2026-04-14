package com.k8s.platform.controller.appcreator;

import com.k8s.platform.domain.dto.appcreator.RegistryConnectionCreateRequest;
import com.k8s.platform.domain.dto.appcreator.RegistryConnectionDto;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.appcreator.RegistryConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing container registry connections.
 * Base path: /api/k8s/{clusterUid}/appcreator/registry-connections
 */
@RestController
@RequestMapping("/api/k8s/{clusterUid}/appcreator/registry-connections")
@RequiredArgsConstructor
public class RegistryConnectionController {

    private final RegistryConnectionService registryService;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping
    public ResponseEntity<List<RegistryConnectionDto>> list(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow("appcreator"); // global — not cluster-specific
        return ResponseEntity.ok(registryService.listForCluster(clusterUid, requireUserId()));
    }

    @PostMapping
    public ResponseEntity<RegistryConnectionDto> create(
            @PathVariable String clusterUid,
            @RequestBody RegistryConnectionCreateRequest request) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        return ResponseEntity.ok(registryService.create(clusterUid, requireUserId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegistryConnectionDto> update(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestBody RegistryConnectionCreateRequest request) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        return ResponseEntity.ok(registryService.update(clusterUid, id, requireUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String clusterUid,
            @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        registryService.delete(clusterUid, id, requireUserId());
        return ResponseEntity.noContent().build();
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

