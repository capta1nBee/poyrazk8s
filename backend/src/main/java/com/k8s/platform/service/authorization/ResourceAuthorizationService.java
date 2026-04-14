package com.k8s.platform.service.authorization;

import com.k8s.platform.service.casbin.CasbinPermissionService;
import com.k8s.platform.service.k8s.NamespaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for filtering resources based on user authorization.
 * Backed by Casbin (replaces the old JSON-policy PolicyService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceAuthorizationService {

    private final NamespaceService namespaceService;
    private final CasbinPermissionService casbinPermissionService;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null) ? null : auth.getName();
    }

    // ── Namespace-level access ─────────────────────────────────────────────────

    /**
     * Get authorized namespace names for the current user in a cluster.
     */
    public List<String> getAuthorizedNamespaceNames(String clusterUid) {
        String username = currentUsername();
        if (username == null) return List.of();
        return namespaceService.getAuthorizedNamespaceNames(username, clusterUid);
    }

    /**
     * Filter resources to those in namespaces the current user can access.
     */
    public <T> List<T> filterByAuthorizedNamespaces(List<T> resources, String clusterUid,
            Function<T, String> namespaceExtractor) {
        Set<String> allowed = new java.util.HashSet<>(getAuthorizedNamespaceNames(clusterUid));
        return resources.stream()
                .filter(r -> allowed.contains(namespaceExtractor.apply(r)))
                .collect(Collectors.toList());
    }

    // ── Resource-level access (Casbin) ─────────────────────────────────────────

    /**
     * Check if the current user has permission to perform {@code action} on a
     * resource of type {@code resourceKind} in the given namespace.
     * (resourceName is no longer part of the Casbin model – cluster + ns + kind + action suffice.)
     */
    public boolean hasResourcePermission(String clusterUid, String namespace,
            String resourceKind, String resourceName, String action) {
        String username = currentUsername();
        if (username == null) return false;
        return casbinPermissionService.hasPermission(username, clusterUid, namespace, resourceKind, action);
    }

    /**
     * Returns true if the current user has at least one Casbin role bound to
     * this cluster (meaning they can access something inside it).
     */
    public boolean hasAnyPermissionInNamespace(String clusterUid, String namespace) {
        String username = currentUsername();
        if (username == null) return false;
        Set<String> accessibleClusters = casbinPermissionService.getAccessibleClusterUids(username);
        return accessibleClusters.contains("*") || accessibleClusters.contains(clusterUid);
    }

    /**
     * Filter a list of resources to those the current user is allowed to access
     * (resourceKind + action check via Casbin for each item's namespace).
     */
    public <T> List<T> filterAccessibleResources(List<T> resources, String clusterUid,
            String resourceKind, String action,
            Function<T, String> namespaceExtractor,
            Function<T, String> nameExtractor) {
        String username = currentUsername();
        if (username == null) return List.of();
        return resources.stream()
                .filter(r -> {
                    String ns = namespaceExtractor.apply(r);
                    return casbinPermissionService.hasPermission(
                            username, clusterUid, ns, resourceKind, action);
                })
                .collect(Collectors.toList());
    }
}
