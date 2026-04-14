package com.k8s.platform.controller.k8s;

import com.k8s.platform.service.k8s.K8sServiceService;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
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
public class ServiceController {

    private final K8sServiceService serviceService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    /**
     * List all services - filtered by authorized namespaces
     */
    @GetMapping("/services")
    public ResponseEntity<List<Map<String, Object>>> listAllServices(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "Service", "*", "view");
        List<Map<String, Object>> allServices = serviceService.listServices(clusterUid).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // Filter by authorized namespaces
        List<Map<String, Object>> filteredServices = authHelper.filterAccessibleResources(allServices, clusterUid,
                "Service", "view", dto -> (String) dto.get("namespace"), dto -> (String) dto.get("name"));

        return ResponseEntity.ok(filteredServices);
    }

    @GetMapping("/namespaces/{namespace}/services")
    public ResponseEntity<List<Map<String, Object>>> listServices(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", "*", "view");
        List<Map<String, Object>> all = serviceService.listServices(clusterUid, namespace).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Service", "view",
                dto -> (String) dto.get("namespace"), dto -> (String) dto.get("name")));
    }

    private Map<String, Object> toDTO(Service service) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", service.getMetadata().getName());
        dto.put("namespace", service.getMetadata().getNamespace());
        dto.put("type", service.getSpec().getType());
        dto.put("clusterIP", service.getSpec().getClusterIP());
        dto.put("externalIPs", service.getSpec().getExternalIPs());
        dto.put("ports", service.getSpec().getPorts());
        dto.put("selector", service.getSpec().getSelector());
        dto.put("age", service.getMetadata().getCreationTimestamp());
        dto.put("uid", service.getMetadata().getUid());
        return dto;
    }

    @GetMapping("/namespaces/{namespace}/services/{name}")
    public ResponseEntity<Service> getService(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", name, "view");
        return ResponseEntity.ok(serviceService.getService(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/services")
    public ResponseEntity<Service> createService(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestBody Service service) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", "*", "create");
        return ResponseEntity.ok(serviceService.createService(clusterUid, namespace, service));
    }

    @PutMapping("/namespaces/{namespace}/services/{name}")
    public ResponseEntity<Service> updateService(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Service service) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", name, "update");
        return ResponseEntity.ok(serviceService.updateService(clusterUid, namespace, name, service));
    }

    @DeleteMapping("/namespaces/{namespace}/services/{name}")
    public ResponseEntity<Map<String, String>> deleteService(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", name, "delete");
        serviceService.deleteService(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Service deleted successfully"));
    }

    @PostMapping("/namespaces/{namespace}/services/{name}/change-type")
    public ResponseEntity<Service> changeServiceType(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, String> request) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", name, "change-type");
        String newType = request.get("type");
        return ResponseEntity.ok(serviceService.changeServiceType(clusterUid, namespace, name, newType));
    }

    @PostMapping("/namespaces/{namespace}/services/{name}/update-ports")
    public ResponseEntity<Service> updatePorts(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody List<ServicePort> ports) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", name, "update");
        return ResponseEntity.ok(serviceService.updatePorts(clusterUid, namespace, name, ports));
    }

    @GetMapping("/namespaces/{namespace}/services/{name}/endpoints")
    public ResponseEntity<Map<String, Object>> getEndpoints(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Service", name, "view-endpoints");
        return ResponseEntity.ok(serviceService.getEndpoints(clusterUid, namespace, name));
    }
}
