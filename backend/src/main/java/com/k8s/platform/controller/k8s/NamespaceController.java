package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.K8sNamespace;
import com.k8s.platform.dto.response.NamespaceResponseDTO;
import com.k8s.platform.service.k8s.NamespaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

        private final NamespaceService namespaceService;
        private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

        /**
         * List namespaces — returns only namespaces the user is authorized to access.
         *
         * No hard "Namespace/view" page permission required: the endpoint already
         * filters results via getAuthorizedNamespaceNames(), so a T1 user (Pod access
         * in prod*) or a T3 user (page:security) can call this and receive only the
         * namespaces their policies allow.  Superadmins receive all namespaces.
         */
        @GetMapping
        public ResponseEntity<List<NamespaceResponseDTO>> listNamespaces(
                        @PathVariable String clusterUid,
                        @RequestParam(defaultValue = "false") boolean includeDeleted,
                        Authentication authentication) {
                String username = authentication.getName();

                // Get authorized namespace names for the user
                List<String> authorizedNamespaceNames = namespaceService.getAuthorizedNamespaceNames(username,
                                clusterUid);

                // Get all namespaces and filter by authorized ones
                List<K8sNamespace> allNamespaces = namespaceService.listNamespaces(clusterUid, includeDeleted);

                List<NamespaceResponseDTO> filteredNamespaces = allNamespaces.stream()
                                .filter(ns -> authorizedNamespaceNames.contains(ns.getName()))
                                .map(NamespaceResponseDTO::fromEntity)
                                .collect(Collectors.toList());

                return ResponseEntity.ok(filteredNamespaces);
        }

        @GetMapping("/{name}")
        public ResponseEntity<NamespaceResponseDTO> getNamespace(
                        @PathVariable String clusterUid,
                        @PathVariable String name) {
                authHelper.checkPermissionOrThrow(clusterUid, null, "Namespace", name, "view");
                return ResponseEntity.ok(
                                NamespaceResponseDTO.fromEntity(namespaceService.getNamespace(clusterUid, name)));
        }

        @GetMapping("/list")
        public ResponseEntity<List<String>> listNamespaceNames(
                        @PathVariable String clusterUid) {
                authHelper.checkPermissionOrThrow(clusterUid, null, "Namespace", "*", "view");
                List<String> all = namespaceService.listNamespaces(clusterUid, false).stream()
                                .map(ns -> ns.getName())
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Namespace", "view",
                                (String item) -> null, item -> item));
        }

        /**
         * Get namespaces that the current user has permission to access.
         *
         * Returns namespaces derived from the user's Casbin namespace patterns —
         * does NOT require explicit "Namespace" page view permission, because a user
         * may only have Pod/Deployment access in certain namespaces without having
         * the Namespaces page assigned to their role.
         */
        @GetMapping("/authorized")
        public ResponseEntity<List<String>> getAuthorizedNamespaces(
                        @PathVariable String clusterUid,
                        Authentication authentication) {
                String username = authentication.getName();
                List<String> authorizedNames = namespaceService.getAuthorizedNamespaceNames(username, clusterUid);
                return ResponseEntity.ok(authorizedNames);
        }

        /**
         * Tier-3 page namespace endpoint.
         *
         * Returns ALL namespace names for the cluster without requiring a resource-level
         * "Namespace / view" permission.  Tier-3 pages (Helm, AppCreator, Federation,
         * Network Firewall …) are granted page-level access, which implies unrestricted
         * visibility across namespaces.  Authentication is still enforced by Spring
         * Security for every non-public route.
         */
        @GetMapping("/for-page")
        public ResponseEntity<List<String>> listNamespacesForPage(@PathVariable String clusterUid) {
                List<String> names = namespaceService.listNamespaces(clusterUid, false).stream()
                                .map(K8sNamespace::getName)
                                .sorted()
                                .collect(Collectors.toList());
                return ResponseEntity.ok(names);
        }
}
