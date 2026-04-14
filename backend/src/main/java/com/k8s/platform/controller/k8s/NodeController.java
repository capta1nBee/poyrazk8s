package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.K8sNode;
import com.k8s.platform.dto.response.NodeResponseDTO;
import com.k8s.platform.service.k8s.NodeService;
import io.fabric8.kubernetes.api.model.Node;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping("/nodes")
    public ResponseEntity<List<NodeResponseDTO>> listNodes(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "Node", "*", "view");
        List<NodeResponseDTO> allNodes = nodeService.listNodes(clusterUid, includeDeleted).stream().map(NodeResponseDTO::fromEntity).collect(Collectors.toList());
        // Node is cluster-scoped — no namespace. Pass item -> null so CasbinPermissionService treats ns as "*".
        return ResponseEntity.ok(authHelper.filterAccessibleResources(allNodes, clusterUid, "Node", "view", item -> null, NodeResponseDTO::getName));
    }

    @GetMapping("/nodes/{name}")
    public ResponseEntity<K8sNode> getNode(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "Node", name, "view");
        return ResponseEntity.ok(nodeService.getNode(clusterUid, name));
    }

    @PostMapping("/nodes/{name}/cordon")
    public ResponseEntity<Node> cordonNode(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "Node", name, "cordon");
        return ResponseEntity.ok(nodeService.cordonNode(clusterUid, name));
    }

    @PostMapping("/nodes/{name}/uncordon")
    public ResponseEntity<Node> uncordonNode(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "Node", name, "uncordon");
        return ResponseEntity.ok(nodeService.uncordonNode(clusterUid, name));
    }

    @PostMapping("/nodes/{name}/drain")
    public ResponseEntity<Map<String, String>> drainNode(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "Node", name, "drain");
        nodeService.drainNode(clusterUid, name);
        return ResponseEntity.ok(Map.of("message", "Node drained successfully"));
    }

    @GetMapping("/nodes/{name}/metrics")
    public ResponseEntity<Map<String, Object>> getNodeMetrics(
            @PathVariable String clusterUid,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, null, "Node", name, "metrics");
        return ResponseEntity.ok(nodeService.getNodeMetrics(clusterUid, name));
    }
}
