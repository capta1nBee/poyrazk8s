package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.PersistentVolume;
import com.k8s.platform.service.k8s.PersistentVolumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class PersistentVolumeController {

    private final PersistentVolumeService persistentVolumeService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping("/persistentvolumes")
    public ResponseEntity<List<PersistentVolume>> listPersistentVolumes(@PathVariable String clusterUid) {
        // PV is cluster-scoped (no namespace) → use "*" for namespace
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PersistentVolume", "*", "view");
        List<PersistentVolume> all = persistentVolumeService.listPersistentVolumes(clusterUid);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "PersistentVolume", "view", null, item -> item.getName()));
    }

    @GetMapping("/persistentvolumes/{name}")
    public ResponseEntity<PersistentVolume> getPersistentVolume(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PersistentVolume", name, "view");
        return ResponseEntity.ok(persistentVolumeService.getPersistentVolume(clusterUid, name));
    }

    @GetMapping("/persistentvolumes/phase/{phase}")
    public ResponseEntity<List<PersistentVolume>> listPersistentVolumesByPhase(
            @PathVariable String clusterUid,
            @PathVariable String phase) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PersistentVolume", "*", "view");
        List<PersistentVolume> all = persistentVolumeService.listPersistentVolumesByPhase(clusterUid, phase);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "PersistentVolume", "view", null, item -> item.getName()));
    }

    @GetMapping("/persistentvolumes/storageclass/{storageClassName}")
    public ResponseEntity<List<PersistentVolume>> listPersistentVolumesByStorageClass(
            @PathVariable String clusterUid,
            @PathVariable String storageClassName) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PersistentVolume", "*", "view");
        List<PersistentVolume> all = persistentVolumeService.listPersistentVolumesByStorageClass(clusterUid, storageClassName);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "PersistentVolume", "view", null, item -> item.getName()));
    }

    @DeleteMapping("/persistentvolumes/{name}")
    public ResponseEntity<Map<String, String>> deletePersistentVolume(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "PersistentVolume", name, "delete");
        persistentVolumeService.deletePersistentVolume(clusterUid, name);
        return ResponseEntity.ok(Map.of("message", "PersistentVolume deleted successfully"));
    }
}
