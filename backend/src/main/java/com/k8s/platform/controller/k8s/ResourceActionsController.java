package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.K8sEvent;
import com.k8s.platform.dto.response.ResourceDetailsDTO;
import com.k8s.platform.service.k8s.EventService;
import com.k8s.platform.service.k8s.ResourceDetailsService;
import com.k8s.platform.service.k8s.ResourceManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/resources")
@RequiredArgsConstructor
@Slf4j
public class ResourceActionsController {

        private final ResourceDetailsService resourceDetailsService;
        private final ResourceManagementService resourceManagementService;
        private final EventService eventService;
        private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

        // ==================== GLOBAL ACTIONS ====================

        /**
         * View Details - Get detailed information about any resource
         */
        @GetMapping("/{resourceKind}/namespaces/{namespace}/{name}/details")
        public ResponseEntity<ResourceDetailsDTO> getResourceDetails(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "view");
                authHelper.logAction(clusterUid, resourceKind, "view details: " + name + " in " + namespace);
                return ResponseEntity.ok(
                                resourceDetailsService.getResourceDetails(clusterUid, namespace, resourceKind, name));
        }

        /**
         * View Details - Cluster-scoped resources
         */
        @GetMapping("/{resourceKind}/{name}/details")
        public ResponseEntity<ResourceDetailsDTO> getClusterResourceDetails(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, null, resourceKind, name, "view");
                authHelper.logAction(clusterUid, resourceKind, "view details: " + name);
                return ResponseEntity.ok(
                                resourceDetailsService.getClusterResourceDetails(clusterUid, resourceKind, name));
        }

        /**
         * View YAML - Get YAML representation
         */
        @GetMapping("/{resourceKind}/namespaces/{namespace}/{name}/yaml")
        public ResponseEntity<Map<String, String>> getResourceYaml(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "view-yaml");
                ResourceDetailsDTO details = resourceDetailsService.getResourceDetails(
                                clusterUid, namespace, resourceKind, name);
                authHelper.logAction(clusterUid, resourceKind, "view yaml: " + name + " in " + namespace);
                return ResponseEntity.ok(Map.of("yaml", details.getYaml()));
        }

        /**
         * View YAML - Cluster-scoped resources
         */
        @GetMapping("/{resourceKind}/{name}/yaml")
        public ResponseEntity<Map<String, String>> getClusterResourceYaml(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, null, resourceKind, name, "view-yaml");
                ResourceDetailsDTO details = resourceDetailsService.getClusterResourceDetails(
                                clusterUid, resourceKind, name);
                authHelper.logAction(clusterUid, resourceKind, "view yaml: " + name);
                return ResponseEntity.ok(Map.of("yaml", details.getYaml()));
        }

        /**
         * Refresh - Re-sync resource from cluster
         */
        @PostMapping("/{resourceKind}/namespaces/{namespace}/{name}/refresh")
        public ResponseEntity<ResourceDetailsDTO> refreshResource(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "view");
                return ResponseEntity.ok(
                                resourceDetailsService.getResourceDetails(clusterUid, namespace, resourceKind, name));
        }

        /**
         * Edit YAML - Apply YAML changes (namespaced resources)
         */
        @PutMapping("/{resourceKind}/namespaces/{namespace}/{name}/yaml")
        public ResponseEntity<Map<String, Object>> editResourceYaml(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestBody Map<String, String> request) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "edit-yaml");

                String yaml = request.get("yaml");
                Boolean dryRun = Boolean.parseBoolean(request.getOrDefault("dryRun", "false"));

                authHelper.logAction(clusterUid, resourceKind, "edit yaml: " + name + " in " + namespace);
                return ResponseEntity.ok(
                                resourceManagementService.applyYaml(clusterUid, namespace, yaml, dryRun));
        }

        /**
         * Edit YAML - Apply YAML changes (cluster-scoped resources)
         */
        @PutMapping("/{resourceKind}/{name}/yaml")
        public ResponseEntity<Map<String, Object>> editClusterResourceYaml(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String name,
                        @RequestBody Map<String, String> request) {
                authHelper.checkPermissionOrThrow(clusterUid, null, resourceKind, name, "edit-yaml");

                String yaml = request.get("yaml");
                Boolean dryRun = Boolean.parseBoolean(request.getOrDefault("dryRun", "false"));

                authHelper.logAction(clusterUid, resourceKind, "edit yaml: " + name);
                // For cluster-scoped resources, namespace is null
                return ResponseEntity.ok(
                                resourceManagementService.applyYaml(clusterUid, null, yaml, dryRun));
        }

        /**
         * Delete - Delete resource
         */
        @DeleteMapping("/{resourceKind}/namespaces/{namespace}/{name}")
        public ResponseEntity<Map<String, String>> deleteResource(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestParam(required = false, defaultValue = "false") Boolean force) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "delete");

                authHelper.logAction(clusterUid, resourceKind,
                                "delete: " + name + " in " + namespace + (force ? " (force)" : ""));
                resourceManagementService.deleteResource(clusterUid, namespace, resourceKind, name, force);
                return ResponseEntity.ok(Map.of("message", resourceKind + " deleted successfully"));
        }

        /**
         * Delete - Cluster-scoped resources
         */
        @DeleteMapping("/{resourceKind}/{name}")
        public ResponseEntity<Map<String, String>> deleteClusterResource(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String name,
                        @RequestParam(required = false, defaultValue = "false") Boolean force) {
                authHelper.checkPermissionOrThrow(clusterUid, null, resourceKind, name, "delete");

                authHelper.logAction(clusterUid, resourceKind, "delete: " + name + (force ? " (force)" : ""));
                resourceManagementService.deleteClusterResource(clusterUid, resourceKind, name, force);
                return ResponseEntity.ok(Map.of("message", resourceKind + " deleted successfully"));
        }

        /**
         * Manage Labels - Update labels
         */
        @PatchMapping("/{resourceKind}/namespaces/{namespace}/{name}/labels")
        public ResponseEntity<Map<String, String>> updateLabels(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestBody Map<String, String> labels) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "edit-labels");

                authHelper.logAction(clusterUid, resourceKind, "update labels: " + name + " in " + namespace);
                resourceManagementService.updateLabels(clusterUid, namespace, resourceKind, name, labels);
                return ResponseEntity.ok(Map.of("message", "Labels updated successfully"));
        }

        /**
         * Manage Annotations - Update annotations
         */
        @PatchMapping("/{resourceKind}/namespaces/{namespace}/{name}/annotations")
        public ResponseEntity<Map<String, String>> updateAnnotations(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestBody Map<String, String> annotations) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "edit-annotations");

                authHelper.logAction(clusterUid, resourceKind, "update annotations: " + name + " in " + namespace);
                resourceManagementService.updateAnnotations(clusterUid, namespace, resourceKind, name, annotations);
                return ResponseEntity.ok(Map.of("message", "Annotations updated successfully"));
        }

        /**
         * View Events - Get events related to a namespaced resource
         */
        @GetMapping("/{resourceKind}/namespaces/{namespace}/{name}/events")
        public ResponseEntity<List<K8sEvent>> getResourceEvents(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, resourceKind, name, "view");
                return ResponseEntity.ok(
                                eventService.getEventsForResource(clusterUid, namespace, resourceKind, name));
        }

        /**
         * View Events - Get events related to a cluster-scoped resource (e.g. Node)
         */
        @GetMapping("/{resourceKind}/{name}/events")
        public ResponseEntity<List<K8sEvent>> getClusterResourceEvents(
                        @PathVariable String clusterUid,
                        @PathVariable String resourceKind,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, null, resourceKind, name, "view");
                return ResponseEntity.ok(
                                eventService.getEventsForResource(clusterUid, null, resourceKind, name));
        }
}
