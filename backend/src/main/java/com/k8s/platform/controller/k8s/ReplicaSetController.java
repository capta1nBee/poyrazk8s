package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.ReplicaSet;
import com.k8s.platform.service.k8s.ReplicaSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class ReplicaSetController {

    private final ReplicaSetService replicaSetService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping("/replicasets")
    public ResponseEntity<List<ReplicaSet>> listReplicaSets(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ReplicaSet", "*", "view");
        List<ReplicaSet> all = replicaSetService.listReplicaSets(clusterUid);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "ReplicaSet", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/replicasets")
    public ResponseEntity<List<ReplicaSet>> listReplicaSetsByNamespace(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ReplicaSet", "*", "view");
        List<ReplicaSet> all = replicaSetService.listReplicaSets(clusterUid, namespace);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "ReplicaSet", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/replicasets/{name}")
    public ResponseEntity<ReplicaSet> getReplicaSet(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ReplicaSet", name, "view");
        return ResponseEntity.ok(replicaSetService.getReplicaSet(clusterUid, namespace, name));
    }
}
