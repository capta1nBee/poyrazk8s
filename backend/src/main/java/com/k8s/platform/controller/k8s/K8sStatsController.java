package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class K8sStatsController {

    private final ClusterRepository clusterRepository;
    private final PodRepository podRepository;
    private final DeploymentRepository deploymentRepository;
    private final ServiceRepository serviceRepository;
    private final K8sNamespaceRepository namespaceRepository;
    private final K8sNodeRepository nodeRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getClusterStats(@PathVariable String clusterUid) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        Long clusterId = cluster.getId();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", nodeRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        stats.put("totalPods", podRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        stats.put("totalDeployments", deploymentRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        stats.put("totalServices", serviceRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        stats.put("totalNamespaces", namespaceRepository.countByClusterIdAndIsDeletedFalse(clusterId));

        return ResponseEntity.ok(stats);
    }
}

