package com.k8s.platform.controller.k8s;

import com.k8s.platform.service.k8s.IngressService;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class IngressController {

    private final IngressService ingressService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    /**
     * List all ingresses - filtered by authorized namespaces
     */
    @GetMapping("/ingresses")
    public ResponseEntity<List<Map<String, Object>>> listAllIngresses(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "Ingress", "*", "view");
        List<Map<String, Object>> allIngresses = ingressService.listIngresses(clusterUid).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // Filter by authorized namespaces
        List<Map<String, Object>> filteredIngresses = authHelper.filterAccessibleResources(allIngresses, clusterUid,
                "Ingress", "view", dto -> (String) dto.get("namespace"), dto -> (String) dto.get("name"));

        return ResponseEntity.ok(filteredIngresses);
    }

    @GetMapping("/namespaces/{namespace}/ingresses")
    public ResponseEntity<List<Map<String, Object>>> listIngresses(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", "*", "view");
        List<Map<String, Object>> all = ingressService.listIngresses(clusterUid, namespace).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Ingress", "view",
                dto -> (String) dto.get("namespace"), dto -> (String) dto.get("name")));
    }

    private Map<String, Object> toDTO(Ingress ingress) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", ingress.getMetadata().getName());
        dto.put("namespace", ingress.getMetadata().getNamespace());
        dto.put("ingressClass", ingress.getSpec().getIngressClassName());
        dto.put("hosts", ingress.getSpec().getRules() != null ? ingress.getSpec().getRules().stream()
                .map(rule -> rule.getHost())
                .collect(Collectors.toList()) : List.of());
        dto.put("address",
                ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null
                        ? ingress.getStatus().getLoadBalancer().getIngress()
                        : null);
        dto.put("age", ingress.getMetadata().getCreationTimestamp());
        dto.put("uid", ingress.getMetadata().getUid());
        return dto;
    }

    @GetMapping("/namespaces/{namespace}/ingresses/{name}")
    public ResponseEntity<Ingress> getIngress(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", name, "view");
        return ResponseEntity.ok(ingressService.getIngress(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/ingresses")
    public ResponseEntity<Ingress> createIngress(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestBody Ingress ingress) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", "*", "create");
        return ResponseEntity.ok(ingressService.createIngress(clusterUid, namespace, ingress));
    }

    @PutMapping("/namespaces/{namespace}/ingresses/{name}")
    public ResponseEntity<Ingress> updateIngress(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Ingress ingress) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", name, "update");
        return ResponseEntity.ok(ingressService.updateIngress(clusterUid, namespace, name, ingress));
    }

    @DeleteMapping("/namespaces/{namespace}/ingresses/{name}")
    public ResponseEntity<Map<String, String>> deleteIngress(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", name, "delete");
        ingressService.deleteIngress(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Ingress deleted successfully"));
    }

    @PostMapping("/namespaces/{namespace}/ingresses/{name}/update-rules")
    public ResponseEntity<Ingress> updateRules(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody List<IngressRule> rules) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", name, "update");
        return ResponseEntity.ok(ingressService.updateRules(clusterUid, namespace, name, rules));
    }

    @PostMapping("/namespaces/{namespace}/ingresses/{name}/update-tls")
    public ResponseEntity<Ingress> updateTLS(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody List<IngressTLS> tls) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", name, "update");
        return ResponseEntity.ok(ingressService.updateTLS(clusterUid, namespace, name, tls));
    }

    @GetMapping("/namespaces/{namespace}/ingresses/{name}/test-route")
    public ResponseEntity<Map<String, Object>> testRoute(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam(required = false, defaultValue = "/") String path) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Ingress", name, "view");
        return ResponseEntity.ok(ingressService.testRoute(clusterUid, namespace, name, path));
    }
}
