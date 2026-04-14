package com.k8s.platform.service.casbin;

import com.k8s.platform.domain.entity.casbin.CasbinRule;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.PageRepository;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.domain.repository.casbin.CasbinRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central permission service backed by Casbin.
 *
 * Policy dimensions:
 *   p: sub(role), dom(cluster/*), ns(namespace-pattern), obj(resource-kind/page:X), act(action/*)
 *   g: username, roleName, clusterUid (or *)
 *
 * SUPERADMIN: has role binding (username, SUPERADMIN, *) and policy (SUPERADMIN, *, *, *, *)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CasbinPermissionService {

    private final Enforcer enforcer;
    private final CasbinRuleRepository ruleRepository;
    private final PageRepository pageRepository;
    private final UserRepository userRepository;

    // ── Core check ────────────────────────────────────────────────────────────

    /**
     * Checks if username has permission. SuperAdmin always returns true.
     * Uses DB lookup for superadmin check (most reliable, avoids Casbin domain-wildcard quirks).
     */
    public boolean hasPermission(String username, String clusterUid, String namespace,
                                 String resourceKind, String action) {
        if (isSuperadminByUsername(username)) return true;
        String ns = (namespace == null || namespace.isBlank()) ? "*" : namespace;
        boolean result = enforcer.enforce(username, clusterUid, ns, resourceKind, action);
        log.trace("enforce({}, {}, {}, {}, {}) = {}", username, clusterUid, ns, resourceKind, action, result);
        return result;
    }

    public boolean hasPermission(User user, String clusterUid, String namespace,
                                 String resourceKind, String action) {
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) return true;
        return hasPermission(user.getUsername(), clusterUid, namespace, resourceKind, action);
    }

    /** Check page-level access: obj = "page:<pageKey>". SuperAdmin always allowed. */
    public boolean canAccessPage(String username, String clusterUid, String pageKey) {
        if (isSuperadminByUsername(username)) return true;
        return enforcer.enforce(username, clusterUid, "*", "page:" + pageKey, "access");
    }

    /**
     * Check if user has page:X access on ANY cluster.
     * Used for platform-level pages (Federation, etc.) that have no cluster context in the API.
     */
    public boolean hasAnyClusterPageAccess(String username, String pageKey) {
        if (isSuperadminByUsername(username)) return true;
        Set<String> userRoles = getUserRoles(username);
        if (userRoles.isEmpty()) return false;
        String targetObj = "page:" + pageKey;
        return ruleRepository.findByPtype("p").stream().anyMatch(rule ->
                userRoles.contains(rule.getV0()) &&
                targetObj.equals(rule.getV3()) &&
                "access".equals(rule.getV4())
        );
    }

    private boolean isSuperadminByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(u -> Boolean.TRUE.equals(u.getIsSuperadmin()))
                .orElse(false);
    }

    /** Returns set of page keys this user can access across any cluster */
    /**
     * Returns pages accessible across ALL clusters (for initial load / no cluster selected).
     * Uses DB scan: any role the user holds anywhere that has page:X → included.
     */
    public Set<String> getAccessiblePages(String username) {
        if (isSuperadminByUsername(username)) return new LinkedHashSet<>(getAllPageKeys());
        Set<String> pages = new LinkedHashSet<>();
        List<CasbinRule> policies = ruleRepository.findByPtype("p");
        Set<String> userRoles = getUserRoles(username);

        for (CasbinRule rule : policies) {
            if (rule.getV3() != null && rule.getV3().startsWith("page:")) {
                if (userRoles.contains(rule.getV0())) {
                    pages.add(rule.getV3().substring(5)); // strip "page:"
                }
            }
        }
        return pages;
    }

    /**
     * Returns pages accessible for a SPECIFIC cluster.
     * Uses the Casbin enforcer so domain matching is exact.
     * Use this for cluster-aware sidebar rendering.
     */
    public Set<String> getAccessiblePages(String username, String clusterUid) {
        if (isSuperadminByUsername(username)) return new LinkedHashSet<>(getAllPageKeys());
        // Use "*" cluster → fall back to the any-cluster scan
        if (clusterUid == null || clusterUid.isBlank() || "*".equals(clusterUid)) {
            return getAccessiblePages(username);
        }
        Set<String> pages = new LinkedHashSet<>();
        for (String pageKey : getAllPageKeys()) {
            if (canAccessPage(username, clusterUid, pageKey)) {
                pages.add(pageKey);
            }
        }
        return pages;
    }

    /** Returns accessible cluster UIDs for a user */
    public Set<String> getAccessibleClusterUids(String username) {
        List<CasbinRule> bindings = ruleRepository.findRoleBindingsByUsername(username);
        Set<String> uids = new HashSet<>();
        for (CasbinRule b : bindings) {
            if (b.getV2() != null) uids.add(b.getV2());
        }
        return uids;
    }

    // ── Role binding management ───────────────────────────────────────────────

    /** Assign a role to a user scoped to a specific cluster (or "*" for all) */
    @Transactional
    public void assignRole(String username, String roleName, String clusterUid) {
        String uid = (clusterUid == null || clusterUid.isBlank()) ? "*" : clusterUid;
        boolean exists = ruleRepository.existsByPtypeAndV0AndV1AndV2AndV3AndV4(
                "g", username, roleName, uid, null, null);
        if (!exists) {
            enforcer.addRoleForUserInDomain(username, roleName, uid);
            log.info("Casbin: assigned role '{}' to '{}' in cluster '{}'", roleName, username, uid);
        }
    }

    @Transactional
    public void removeRole(String username, String roleName, String clusterUid) {
        String uid = (clusterUid == null || clusterUid.isBlank()) ? "*" : clusterUid;
        enforcer.deleteRoleForUserInDomain(username, roleName, uid);
        log.info("Casbin: removed role '{}' from '{}' in cluster '{}'", roleName, username, uid);
    }

    @Transactional
    public void removeAllRolesForUser(String username) {
        List<CasbinRule> bindings = ruleRepository.findRoleBindingsByUsername(username);
        for (CasbinRule b : bindings) {
            enforcer.deleteRoleForUserInDomain(b.getV0(), b.getV1(), b.getV2());
        }
    }

    /** Sync SUPERADMIN status: add/remove SUPERADMIN role binding */
    @Transactional
    public void syncSuperadmin(String username, boolean isSuperadmin) {
        if (isSuperadmin) {
            assignRole(username, "SUPERADMIN", "*");
        } else {
            removeRole(username, "SUPERADMIN", "*");
        }
    }

    // ── Policy management ────────────────────────────────────────────────────

    @Transactional
    public void addPolicy(String roleName, String clusterUid, String nsPattern,
                          String resourceKind, String action) {
        String dom = (clusterUid == null || clusterUid.isBlank()) ? "*" : clusterUid;
        enforcer.addPolicy(roleName, dom, nsPattern, resourceKind, action);
    }

    @Transactional
    public void removePolicy(String roleName, String clusterUid, String nsPattern,
                             String resourceKind, String action) {
        String dom = (clusterUid == null || clusterUid.isBlank()) ? "*" : clusterUid;
        enforcer.removePolicy(roleName, dom, nsPattern, resourceKind, action);
    }

    @Transactional
    public void removeAllPoliciesForRole(String roleName) {
        ruleRepository.deleteAllPoliciesForRole(roleName);
        enforcer.loadPolicy(); // reload enforcer
    }

    public List<List<String>> getPoliciesForRole(String roleName) {
        return enforcer.getFilteredPolicy(0, roleName);
    }

    public List<CasbinRule> getRoleBindings(String roleName) {
        return ruleRepository.findRoleBindingsByRoleName(roleName);
    }

    public void reloadPolicy() {
        enforcer.loadPolicy();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Set<String> getUserRoles(String username) {
        return ruleRepository.findRoleBindingsByUsername(username)
                .stream()
                .map(CasbinRule::getV1)
                .collect(Collectors.toSet());
    }

    /** All page keys come from the pages DB table — never hardcoded */
    private List<String> getAllPageKeys() {
        return pageRepository.findByIsActiveTrue().stream()
                .map(p -> p.getName())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<String> listAllPageKeys() {
        return getAllPageKeys();
    }

    /** Returns all role bindings for a specific user (ptype='g', v0=username) */
    public List<CasbinRule> getUserRoleBindings(String username) {
        return ruleRepository.findRoleBindingsByUsername(username);
    }
}
