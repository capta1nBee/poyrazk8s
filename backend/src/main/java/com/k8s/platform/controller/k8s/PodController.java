package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.Pod;
import com.k8s.platform.dto.response.PodResponseDTO;
import com.k8s.platform.service.k8s.PodActionsService;
import com.k8s.platform.service.k8s.PodService;
import com.k8s.platform.service.k8s.ResourceDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class PodController {

        private final PodService podService;
        private final PodActionsService podActionsService;
        private final ResourceDetailsService resourceDetailsService;
        private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

        /**
         * List all pods - filtered by authorized namespaces
         */
        @GetMapping("/pods")
        public ResponseEntity<List<PodResponseDTO>> listAllPods(
                        @PathVariable String clusterUid,
                        @RequestParam(defaultValue = "false") boolean includeDeleted) {
                authHelper.checkPermissionOrThrow(clusterUid, "*", "Pod", "*", "view");
                List<PodResponseDTO> allPods = podService.listPods(clusterUid, includeDeleted).stream()
                                .map(PodResponseDTO::fromEntity)
                                .collect(Collectors.toList());

                // Filter by authorized namespaces
                List<PodResponseDTO> filteredPods = authHelper.filterAccessibleResources(allPods, clusterUid, "Pod",
                                "view",
                                PodResponseDTO::getNamespace, PodResponseDTO::getName);

                return ResponseEntity.ok(filteredPods);
        }

        @GetMapping("/namespaces/{namespace}/pods")
        public ResponseEntity<List<PodResponseDTO>> listPods(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @RequestParam(defaultValue = "false") boolean includeDeleted) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", "*", "view");
                List<PodResponseDTO> all = podService.listPods(clusterUid, namespace, includeDeleted).stream()
                                .map(PodResponseDTO::fromEntity)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Pod", "view",
                                PodResponseDTO::getNamespace, PodResponseDTO::getName));
        }

        @GetMapping("/namespaces/{namespace}/pods/{name}")
        public ResponseEntity<Pod> getPod(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "view");
                return ResponseEntity.ok(podService.getPod(clusterUid, namespace, name));
        }

        @GetMapping("/namespaces/{namespace}/pods/{name}/yaml")
        public ResponseEntity<Map<String, String>> getPodYaml(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "view-yaml");
                // Get YAML directly from Kubernetes cluster using ResourceDetailsService
                var details = resourceDetailsService.getResourceDetails(clusterUid, namespace, "Pod", name);
                return ResponseEntity.ok(Map.of("yaml", details.getYaml()));
        }

        @DeleteMapping("/namespaces/{namespace}/pods/{name}")
        public ResponseEntity<Map<String, String>> deletePod(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "delete");
                authHelper.logAction(clusterUid, "Pod", "delete: " + name + " in " + namespace);
                podService.deletePod(clusterUid, namespace, name);
                return ResponseEntity.ok(Map.of("message", "Pod deleted successfully"));
        }

        @PostMapping("/namespaces/{namespace}/pods/{name}/delete-force")
        public ResponseEntity<Map<String, String>> forceDeletePod(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "force-delete");
                authHelper.logAction(clusterUid, "Pod", "force delete: " + name + " in " + namespace);
                podService.forceDeletePod(clusterUid, namespace, name);
                return ResponseEntity.ok(Map.of("message", "Pod force deleted successfully"));
        }

        @GetMapping("/namespaces/{namespace}/pods/{name}/logs")
        public ResponseEntity<Map<String, String>> getLogs(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestParam(required = false) String container) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "logs");
                authHelper.logAction(clusterUid, "Pod", "view logs: " + name + " in " + namespace
                                + (container != null ? " (container: " + container + ")" : ""));
                String logs = podService.getLogs(clusterUid, namespace, name, container);
                return ResponseEntity.ok(Map.of("logs", logs));
        }

        @GetMapping("/namespaces/{namespace}/pods/{name}/logs/previous")
        public ResponseEntity<Map<String, String>> getPreviousLogs(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestParam(required = false) String container) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "previous-logs");
                String logs = podService.getPreviousLogs(clusterUid, namespace, name, container);
                return ResponseEntity.ok(Map.of("logs", logs));
        }

        @GetMapping("/pods/phase/{phase}")
        public ResponseEntity<List<PodResponseDTO>> listPodsByPhase(
                        @PathVariable String clusterUid,
                        @PathVariable String phase) {
                List<PodResponseDTO> all = podService.listPodsByPhase(clusterUid, phase).stream()
                                .map(PodResponseDTO::fromEntity)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Pod", "view",
                                PodResponseDTO::getNamespace, PodResponseDTO::getName));
        }

        @GetMapping("/pods/node/{nodeName}")
        public ResponseEntity<List<PodResponseDTO>> listPodsByNode(
                        @PathVariable String clusterUid,
                        @PathVariable String nodeName) {
                List<PodResponseDTO> all = podService.listPodsByNode(clusterUid, nodeName).stream()
                                .map(PodResponseDTO::fromEntity)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Pod", "view",
                                PodResponseDTO::getNamespace, PodResponseDTO::getName));
        }

        @PostMapping("/namespaces/{namespace}/pods/{name}/exec")
        public ResponseEntity<Map<String, String>> execPod(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestParam(required = false) String container,
                        @RequestParam String command) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "exec");
                authHelper.logAction(clusterUid, "Pod", "exec: " + command + " on " + name + " in " + namespace
                                + (container != null ? " (container: " + container + ")" : ""));
                // This is a placeholder for exec functionality
                // WebSocket implementation would be needed for interactive shell
                return ResponseEntity
                                .ok(Map.of("message",
                                                "Exec command initiated. Full implementation requires WebSocket support."));
        }

        // ==================== POD-SPECIFIC ACTIONS ====================

        @PostMapping("/namespaces/{namespace}/pods/{name}/restart")
        public ResponseEntity<Map<String, String>> restartPod(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "restart");
                authHelper.logAction(clusterUid, "Pod", "restart: " + name + " in " + namespace);
                return ResponseEntity.ok(podActionsService.restartPod(clusterUid, namespace, name));
        }

        @GetMapping("/namespaces/{namespace}/pods/{name}/metrics")
        public ResponseEntity<Map<String, Object>> getPodMetrics(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Pod", name, "metrics");
                return ResponseEntity.ok(podActionsService.getPodMetrics(clusterUid, namespace, name));
        }

        @GetMapping("/namespaces/{namespace}/pods/{name}/containers")
        public ResponseEntity<List<Map<String, String>>> getContainers(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                return ResponseEntity.ok(podActionsService.getContainers(clusterUid, namespace, name));
        }

        @PostMapping("/namespaces/{namespace}/pods/{name}/port-forward")
        public ResponseEntity<Map<String, Object>> createPortForward(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestParam Integer localPort,
                        @RequestParam Integer podPort) {
                return ResponseEntity.ok(
                                podActionsService.createPortForward(clusterUid, namespace, name, localPort, podPort));
        }

        /**
         * Returns distinct values of the 'network.policy.policy-labels' key
         * from pods in the given cluster + namespace.
         * Used by the Network Topology pod label filter dropdown.
         */
        @GetMapping("/namespaces/{namespace}/pods/policy-labels")
        public ResponseEntity<List<String>> getPodPolicyLabels(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace) {
                List<String> labels = podService.getPodPolicyLabelValues(clusterUid, namespace);
                return ResponseEntity.ok(labels);
        }
}
