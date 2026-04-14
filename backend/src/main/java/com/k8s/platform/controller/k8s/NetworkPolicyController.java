package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.service.k8s.NetworkPolicyService;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class NetworkPolicyController {

    private final NetworkPolicyService networkPolicyService;
    private final ResourceAuthorizationHelper authHelper;
        
    @GetMapping("/networkpolicies")
    public ResponseEntity<List<NetworkPolicy>> listAllNetworkPolicies(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "NetworkPolicy", "*", "view");
        List<NetworkPolicy> all = networkPolicyService.listNetworkPolicies(clusterUid);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "NetworkPolicy", "view", item -> item.getMetadata().getNamespace(), item -> item.getMetadata().getName()));
    }

    @GetMapping("/namespaces/{namespace}/networkpolicies")
    public ResponseEntity<List<NetworkPolicy>> listNetworkPolicies(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", "*", "view");

        List<NetworkPolicy> all = networkPolicyService.listNetworkPolicies(clusterUid, namespace);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "NetworkPolicy", "view", item -> item.getMetadata().getNamespace(), item -> item.getMetadata().getName()));
    }

    @GetMapping("/namespaces/{namespace}/networkpolicies/{name}")
    public ResponseEntity<NetworkPolicy> getNetworkPolicy(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", name, "view");

        return ResponseEntity.ok(networkPolicyService.getNetworkPolicy(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/networkpolicies")
    public ResponseEntity<NetworkPolicy> createNetworkPolicy(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestBody NetworkPolicy networkPolicy) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", "*", "create");

        return ResponseEntity.ok(networkPolicyService.createNetworkPolicy(clusterUid, namespace, networkPolicy));
    }

    @PutMapping("/namespaces/{namespace}/networkpolicies/{name}")
    public ResponseEntity<NetworkPolicy> updateNetworkPolicy(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody NetworkPolicy networkPolicy) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", name, "update");

        return ResponseEntity.ok(networkPolicyService.updateNetworkPolicy(clusterUid, namespace, name, networkPolicy));
    }

    @DeleteMapping("/namespaces/{namespace}/networkpolicies/{name}")
    public ResponseEntity<Map<String, String>> deleteNetworkPolicy(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", name, "delete");

        networkPolicyService.deleteNetworkPolicy(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Network policy deleted successfully"));
    }

    @GetMapping("/namespaces/{namespace}/networkpolicies/{name}/visualize")
    public ResponseEntity<Map<String, Object>> visualize(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", name, "view");

        return ResponseEntity.ok(networkPolicyService.visualize(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/networkpolicies/{name}/simulate")
    public ResponseEntity<Map<String, Object>> simulate(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, Object> request) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "NetworkPolicy", name, "view");

        @SuppressWarnings("unchecked")
        Map<String, String> sourcePod = (Map<String, String>) request.get("sourcePod");
        @SuppressWarnings("unchecked")
        Map<String, String> targetPod = (Map<String, String>) request.get("targetPod");

        return ResponseEntity.ok(networkPolicyService.simulate(clusterUid, namespace, name, sourcePod, targetPod));
    }
}
