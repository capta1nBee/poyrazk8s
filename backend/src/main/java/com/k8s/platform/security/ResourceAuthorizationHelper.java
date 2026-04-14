package com.k8s.platform.security;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Delegates all permission checks to CasbinPermissionService.
 * Kept for backward compatibility with existing controllers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceAuthorizationHelper {

    private final CasbinPermissionService casbinPermissionService;
    private final UserRepository userRepository;
    private final ClusterRepository clusterRepository;
    private final com.k8s.platform.service.audit.AuditLogService auditLogService;

    /**
     * Get the current authenticated user
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null)
            return null;

        if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        } else if (authentication.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) authentication.getPrincipal()).getUsername();
            return userRepository.findByUsername(username).orElse(null);
        } else {
            String username = authentication.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
    }

    /**
     * Log an action with cluster context
     */
    public void logAction(String clusterUid, String action, String details) {
        String clusterName = null;
        if (clusterUid != null) {
            clusterName = clusterRepository.findByUid(clusterUid)
                    .map(com.k8s.platform.domain.entity.Cluster::getName)
                    .orElse(null);
        }

        User user = getCurrentUser();
        String username = user != null ? user.getUsername() : "system";

        auditLogService.log(username, action, details, clusterUid, clusterName);
    }

    /**
     * Check if current user has permission for an action (using cluster UID)
     */
    public boolean hasPermission(String clusterUid, String namespace, String resourceKind, String resourceName,
            String action) {
        User user = getCurrentUser();

        log.info("[AUTH-HELPER] hasPermission called — user={}, isSuperadmin={}, cluster={}, ns={}, kind={}, name={}, action={}",
                user != null ? user.getUsername() : "<NULL USER>",
                user != null ? user.getIsSuperadmin() : "<null user so N/A>",
                clusterUid, namespace, resourceKind, resourceName, action);

        if (user == null) {
            log.warn("[AUTH-HELPER] DENIED — no authenticated user found in SecurityContext");
            return false;
        }
        if (user.getIsSuperadmin() != null && user.getIsSuperadmin()) {
            log.info("[AUTH-HELPER] GRANTED — user={} is SUPERADMIN", user.getUsername());
            return true;
        }

        // ── T3 Federation bypass ──────────────────────────────────────────────────
        // Federation page users need cross-cluster K8s resource visibility to manage
        // federation members and synced resources. If they hold page:federations in
        // any cluster, grant read access (view / get / list) to K8s resources.
        if ("view".equals(action)
                && casbinPermissionService.hasAnyClusterPageAccess(user.getUsername(), "federations")) {
            log.debug("[AUTH-HELPER] T3-federation bypass GRANTED view for user={} cluster={} kind={}",
                    user.getUsername(), clusterUid, resourceKind);
            return true;
        }

        boolean result = casbinPermissionService.hasPermission(user.getUsername(), clusterUid, namespace, resourceKind, action);
        log.debug("[AUTH] casbin result={} for user={}", result, user.getUsername());
        return result;
    }

    /**
     * Check if current user has permission for an action (using cluster ID)
     */
    public boolean hasPermissionByClusterId(Long clusterId, String namespace, String resourceKind, String resourceName,
            String action) {
        User user = getCurrentUser();
        if (user == null)
            return false;
        if (user.getIsSuperadmin() != null && user.getIsSuperadmin())
            return true;

        // Get cluster UID from cluster ID
        Cluster cluster = clusterRepository.findById(clusterId).orElse(null);
        String clusterUid = cluster != null ? cluster.getUid() : null;

        return casbinPermissionService.hasPermission(user.getUsername(), clusterUid, namespace, resourceKind, action);
    }

    /**
     * Check permission and throw exception if denied
     */
    public void checkPermissionOrThrow(String clusterUid, String namespace, String resourceKind, String resourceName,
            String action) {
        if (!hasPermission(clusterUid, namespace, resourceKind, resourceName, action)) {
            log.warn("Permission denied for action '{}' on {}/{}/{}/{}", action, clusterUid, namespace, resourceKind,
                    resourceName);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied for action: " + action);
        }
    }

    /**
     * Check permission by cluster ID and throw exception if denied
     */
    public void checkPermissionByClusterIdOrThrow(Long clusterId, String namespace, String resourceKind,
            String resourceName, String action) {
        if (!hasPermissionByClusterId(clusterId, namespace, resourceKind, resourceName, action)) {
            log.warn("Permission denied for action '{}' on cluster {}/{}/{}/{}", action, clusterId, namespace,
                    resourceKind, resourceName);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied for action: " + action);
        }
    }

    /**
     * Check page-level access in any cluster and throw 403 if denied.
     * Used for Tier 3 controllers (Backup, Federation, Network) where there is no cluster context.
     */
    public void checkPagePermissionOrThrow(String pageKey) {
        User user = getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authenticated");
        }
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) return;
        if (!casbinPermissionService.hasAnyClusterPageAccess(user.getUsername(), pageKey)) {
            log.warn("[AUTH-HELPER] DENIED page={} for user={}", pageKey, user.getUsername());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied for page: " + pageKey);
        }
    }

    /**
     * Check page-level access for a specific cluster and throw 403 if denied.
     */
    public void checkPagePermissionOrThrow(String clusterUid, String pageKey) {
        User user = getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authenticated");
        }
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) return;
        if (!casbinPermissionService.canAccessPage(user.getUsername(), clusterUid, pageKey)) {
            log.warn("[AUTH-HELPER] DENIED page={} cluster={} for user={}", pageKey, clusterUid, user.getUsername());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied for page: " + pageKey);
        }
    }

    /**
     * Check if user is superadmin
     */
    public boolean isSuperadmin() {
        User user = getCurrentUser();
        return user != null && user.getIsSuperadmin() != null && user.getIsSuperadmin();
    }

    /**
     * Filter a list of resources based on the user's policies (namespace +
     * namePattern)
     */
    public <T> java.util.List<T> filterAccessibleResources(java.util.List<T> resources, String clusterUid,
            String resourceKind, String action,
            java.util.function.Function<T, String> namespaceExtractor,
            java.util.function.Function<T, String> nameExtractor) {
        User user = getCurrentUser();
        if (user == null) {
            return new java.util.ArrayList<>();
        }
        // Superadmin sees everything
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) {
            return resources;
        }
        // T3 Federation bypass: federation users can see all resources (cross-cluster visibility)
        if ("view".equals(action)
                && casbinPermissionService.hasAnyClusterPageAccess(user.getUsername(), "federations")) {
            log.debug("[AUTH-HELPER] T3-federation filter bypass for user={} kind={}", user.getUsername(), resourceKind);
            return resources;
        }
        // Filter using Casbin: check each resource individually
        return resources.stream()
                .filter(r -> {
                    // Cluster-scoped resources (PV, ClusterRole, etc.) have no namespace → use "*"
                    String ns = (namespaceExtractor != null) ? namespaceExtractor.apply(r) : "*";
                    return casbinPermissionService.hasPermission(user.getUsername(), clusterUid, ns, resourceKind, action);
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
