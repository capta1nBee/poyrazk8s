package com.k8s.platform.controller.k8s;

import com.k8s.platform.dto.response.PVCResponseDTO;
import com.k8s.platform.service.k8s.PVCService;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class PVCController {

        private final PVCService pvcService;
        private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

        /**
         * List all PVCs - filtered by authorized namespaces
         */
        @GetMapping({ "/pvcs", "/persistentvolumeclaims" })
        public ResponseEntity<List<PVCResponseDTO>> listAllPVCs(
                        @PathVariable String clusterUid,
                        @RequestParam(defaultValue = "false") boolean includeDeleted) {
                authHelper.checkPermissionOrThrow(clusterUid, "*", "PersistentVolumeClaim", "*", "view");

                List<PVCResponseDTO> allPVCs = pvcService.listPVCsFromDB(clusterUid, includeDeleted).stream()
                                .map(PVCResponseDTO::fromEntity)
                                .collect(Collectors.toList());

                // Filter by authorized namespaces
                List<PVCResponseDTO> filteredPVCs = authHelper.filterAccessibleResources(allPVCs, clusterUid,
                                "PersistentVolumeClaim", "view", PVCResponseDTO::getNamespace, PVCResponseDTO::getName);

                return ResponseEntity.ok(filteredPVCs);
        }

        @GetMapping({ "/namespaces/{namespace}/pvcs", "/namespaces/{namespace}/persistentvolumeclaims" })
        public ResponseEntity<List<PVCResponseDTO>> listPVCs(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @RequestParam(defaultValue = "false") boolean includeDeleted) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "PersistentVolumeClaim", "*", "view");
                List<PVCResponseDTO> all = pvcService.listPVCsFromDB(clusterUid, namespace, includeDeleted).stream()
                                .map(PVCResponseDTO::fromEntity)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "PersistentVolumeClaim",
                                "view", PVCResponseDTO::getNamespace, PVCResponseDTO::getName));
        }

        @GetMapping({ "/namespaces/{namespace}/pvcs/{name}", "/namespaces/{namespace}/persistentvolumeclaims/{name}" })
        public ResponseEntity<PVCResponseDTO> getPVC(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "PersistentVolumeClaim", name, "view");
                return ResponseEntity.ok(
                                PVCResponseDTO.fromEntity(pvcService.getPVCFromDB(clusterUid, namespace, name)));
        }

        @PostMapping({ "/namespaces/{namespace}/pvcs", "/namespaces/{namespace}/persistentvolumeclaims" })
        public ResponseEntity<PersistentVolumeClaim> createPVC(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @RequestBody PersistentVolumeClaim pvc) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "PersistentVolumeClaim", "*", "create");
                return ResponseEntity.ok(pvcService.createPVC(clusterUid, namespace, pvc));
        }

        @DeleteMapping({ "/namespaces/{namespace}/pvcs/{name}",
                        "/namespaces/{namespace}/persistentvolumeclaims/{name}" })
        public ResponseEntity<Map<String, String>> deletePVC(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "PersistentVolumeClaim", name, "delete");
                pvcService.deletePVC(clusterUid, namespace, name);
                return ResponseEntity.ok(Map.of("message", "PVC deleted successfully"));
        }

        @PostMapping({ "/namespaces/{namespace}/pvcs/{name}/resize",
                        "/namespaces/{namespace}/persistentvolumeclaims/{name}/resize" })
        public ResponseEntity<PersistentVolumeClaim> resizePVC(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name,
                        @RequestBody Map<String, String> request) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "PersistentVolumeClaim", name, "update");
                String newSize = request.get("size");
                return ResponseEntity.ok(pvcService.resizePVC(clusterUid, namespace, name, newSize));
        }

        @GetMapping({ "/namespaces/{namespace}/pvcs/{name}/usage",
                        "/namespaces/{namespace}/persistentvolumeclaims/{name}/usage" })
        public ResponseEntity<Map<String, Object>> getUsage(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "PersistentVolumeClaim", name, "view");
                return ResponseEntity.ok(pvcService.getUsage(clusterUid, namespace, name));
        }
}
