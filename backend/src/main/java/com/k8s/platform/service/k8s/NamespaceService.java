package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.casbin.CasbinRule;
import com.k8s.platform.domain.entity.k8s.K8sNamespace;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.domain.repository.k8s.K8sNamespaceRepository;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamespaceService {

    private final K8sNamespaceRepository namespaceRepository;
    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;
    private final CasbinPermissionService casbinPermissionService;

    public List<K8sNamespace> listNamespaces(String clusterUid) {
        return listNamespaces(clusterUid, false);
    }

    public List<K8sNamespace> listNamespaces(String clusterUid, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
        if (includeDeleted) {
            return namespaceRepository.findByClusterId(cluster.getId());
        }
        return namespaceRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public K8sNamespace getNamespace(String clusterUid, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
        return namespaceRepository.findByClusterIdAndName(cluster.getId(), name)
                .orElseThrow(() -> new RuntimeException("Namespace not found"));
    }

    /**
     * Get namespaces that the user has permission to access for a specific cluster.
     *
     * Uses Casbin policy rules (NOT the legacy UserPolicy table) to determine
     * which namespace patterns the user's roles grant, then matches those patterns
     * against the real namespaces in the cluster.
     *
     * Logic:
     *  1. Superadmin → all namespaces
     *  2. Collect roles assigned to the user for this cluster (or wildcard *)
     *  3. From those roles' Casbin 'p' rules, collect ns patterns (v2) where
     *     dom (v1) matches the cluster — skip page:X rules
     *  4. If any pattern is '*' → all namespaces
     *  5. Otherwise return real namespaces whose names match any pattern
     */
    public List<String> getAuthorizedNamespaceNames(String username, String clusterUid) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return Collections.emptyList();

        // Superadmin → all namespaces
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) {
            return listNamespaces(clusterUid, false).stream()
                    .map(K8sNamespace::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Get user's role bindings applicable to this cluster
        List<CasbinRule> bindings = casbinPermissionService.getUserRoleBindings(username);
        List<String> applicableRoles = bindings.stream()
                .filter(b -> "*".equals(b.getV2()) || clusterUid.equals(b.getV2()))
                .map(CasbinRule::getV1)
                .distinct()
                .collect(Collectors.toList());

        if (applicableRoles.isEmpty()) {
            log.debug("[NS-AUTH] {} has no roles in cluster {}", username, clusterUid);
            return Collections.emptyList();
        }

        // SUPERADMIN role → all namespaces
        if (applicableRoles.contains("SUPERADMIN")) {
            return listNamespaces(clusterUid, false).stream()
                    .map(K8sNamespace::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Collect namespace patterns from all applicable roles' Casbin policies.
        // We track explicit (non-wildcard) patterns separately from wildcard (*).
        //
        // Rationale: a T2 resource rule is stored as (role, cluster, *, Resource, action).
        // If we treat that "*" as "show all namespaces" in the selector, a T1 user who also
        // has any T2 resource access would see ALL namespaces in the dropdown — wrong.
        // Rule: if there are ANY explicit (T1) ns patterns, use only those for the selector.
        //       Only fall back to "all namespaces" when the user has ZERO T1 restrictions.
        Set<String> explicitPatterns  = new HashSet<>(); // non-wildcard (T1) — e.g. "prod*"
        boolean     hasWildcardAccess = false;           // true when a rule has ns="*"

        for (String roleName : applicableRoles) {
            List<List<String>> policies = casbinPermissionService.getPoliciesForRole(roleName);
            for (List<String> policy : policies) {
                // policy layout: [roleName, dom, ns, obj, act]
                if (policy.size() < 3) continue;
                String dom = policy.get(1);
                String ns  = policy.get(2);
                String obj = policy.size() > 3 ? policy.get(3) : null;

                // Skip page-level access rules — they carry no namespace info
                if (obj != null && obj.startsWith("page:")) continue;

                // Domain must match this cluster or be wildcard
                if (!"*".equals(dom) && !clusterUid.equals(dom)) continue;

                if (ns == null || ns.isBlank()) continue;

                if ("*".equals(ns)) {
                    hasWildcardAccess = true;
                } else {
                    explicitPatterns.add(ns);
                }
            }
        }

        log.debug("[NS-AUTH] user={} cluster={} explicit={} wildcard={}",
                username, clusterUid, explicitPatterns, hasWildcardAccess);

        // T1 user: explicit ns patterns exist → show only matching namespaces
        if (!explicitPatterns.isEmpty()) {
            List<K8sNamespace> allNamespaces = listNamespaces(clusterUid, false);
            Set<String> authorized = new HashSet<>();
            for (K8sNamespace ns : allNamespaces) {
                for (String pattern : explicitPatterns) {
                    if (matchesPattern(ns.getName(), pattern)) {
                        authorized.add(ns.getName());
                        break;
                    }
                }
            }
            log.debug("[NS-AUTH] T1 authorized namespaces for user={}: {}", username, authorized);
            return authorized.stream().sorted().collect(Collectors.toList());
        }

        // T2-only or T3-only user: wildcard access → show all namespaces
        if (hasWildcardAccess) {
            return listNamespaces(clusterUid, false).stream()
                    .map(K8sNamespace::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }

        // No applicable policy → empty
        return Collections.emptyList();
    }

    /**
     * Helper method to match a value against a pattern with wildcard support
     */
    private boolean matchesPattern(String value, String pattern) {
        if (pattern == null || value == null) {
            return false;
        }

        // Exact match for '*'
        if (pattern.equals("*")) {
            return true;
        }

        // Convert wildcard pattern to regex
        String regexPattern = pattern
                .replace(".", "\\.") // Escape dots
                .replace("*", ".*") // * becomes .*
                .replace("?", "."); // ? becomes .

        // Add anchors for exact matching
        regexPattern = "^" + regexPattern + "$";

        try {
            return java.util.regex.Pattern.matches(regexPattern, value);
        } catch (Exception e) {
            log.error("Invalid pattern: {}", pattern, e);
            return false;
        }
    }
}
