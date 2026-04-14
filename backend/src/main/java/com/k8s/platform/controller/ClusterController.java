package com.k8s.platform.controller;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.dto.request.ClusterRequest;
import com.k8s.platform.dto.response.ClusterValidationResponse;
import com.k8s.platform.service.cluster.ClusterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping
    public ResponseEntity<List<Cluster>> getAllClusters() {
        return ResponseEntity.ok(clusterService.getAllClusters());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Cluster>> getActiveClusters() {
        return ResponseEntity.ok(clusterService.getActiveClusters());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cluster> getCluster(@PathVariable Long id) {
        return ResponseEntity.ok(clusterService.getCluster(id));
    }

    @PostMapping
    public ResponseEntity<Cluster> createCluster(@Valid @RequestBody ClusterRequest request) {
        authHelper.checkPermissionOrThrow(null, null, "Cluster", "*", "create");
        Cluster cluster = clusterService.createCluster(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cluster);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cluster> updateCluster(
            @PathVariable Long id,
            @Valid @RequestBody ClusterRequest request) {
        authHelper.checkPermissionOrThrow(null, null, "Cluster", id.toString(), "edit");
        Cluster cluster = clusterService.updateCluster(id, request);
        return ResponseEntity.ok(cluster);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCluster(@PathVariable Long id) {
        authHelper.checkPermissionOrThrow(null, null, "Cluster", id.toString(), "delete");
        clusterService.deleteCluster(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = clusterService.testConnection(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Connection successful" : "Connection failed"));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnectionWithRequest(
            @Valid @RequestBody ClusterRequest request) {
        boolean success = clusterService.testConnection(request);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Connection successful" : "Connection failed"));
    }

    @PostMapping("/validate")
    public ResponseEntity<ClusterValidationResponse> validateKubeconfig(
            @Valid @RequestBody ClusterRequest request) {
        ClusterValidationResponse response = clusterService.validateKubeconfig(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getClusterStats(@PathVariable Long id) {
        Cluster cluster = clusterService.getCluster(id);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", cluster.getNodes() != null ? cluster.getNodes() : 0);
        stats.put("totalPods", 0); // TODO: Implement
        stats.put("totalDeployments", 0); // TODO: Implement
        stats.put("totalServices", 0); // TODO: Implement
        stats.put("totalNamespaces", 0); // TODO: Implement
        return ResponseEntity.ok(stats);
    }
}
