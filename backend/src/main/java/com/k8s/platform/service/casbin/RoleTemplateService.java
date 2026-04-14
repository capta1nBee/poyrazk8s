package com.k8s.platform.service.casbin;

import com.k8s.platform.domain.entity.casbin.CasbinRule;
import com.k8s.platform.domain.entity.casbin.RoleNameFilter;
import com.k8s.platform.domain.entity.casbin.RoleTemplate;
import com.k8s.platform.domain.repository.casbin.CasbinRuleRepository;
import com.k8s.platform.domain.repository.casbin.RoleNameFilterRepository;
import com.k8s.platform.domain.repository.casbin.RoleTemplateRepository;
import com.k8s.platform.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleTemplateService {

    private final RoleTemplateRepository templateRepo;
    private final CasbinRuleRepository ruleRepo;
    private final CasbinPermissionService casbinService;
    private final UserRepository userRepository;
    private final RoleNameFilterRepository nameFilterRepo;

    // ── Role Template CRUD ────────────────────────────────────────────────────

    public List<RoleTemplate> listAll() {
        return templateRepo.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    public RoleTemplate getById(Long id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    @Transactional
    public RoleTemplate create(String name, String displayName, String description,
                               String color, Long createdBy) {
        if (templateRepo.existsByName(name))
            throw new IllegalArgumentException("Role name already exists: " + name);
        RoleTemplate t = RoleTemplate.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .color(color != null ? color : "#6366f1")
                .isActive(true)
                .createdBy(createdBy)
                .build();
        return templateRepo.save(t);
    }

    @Transactional
    public RoleTemplate update(Long id, String displayName, String description, String color) {
        RoleTemplate t = getById(id);
        if (displayName != null) t.setDisplayName(displayName);
        if (description != null) t.setDescription(description);
        if (color != null) t.setColor(color);
        return templateRepo.save(t);
    }

    @Transactional
    public void delete(Long id) {
        RoleTemplate t = getById(id);
        if ("SUPERADMIN".equals(t.getName()))
            throw new IllegalArgumentException("Cannot delete SUPERADMIN role");
        casbinService.removeAllPoliciesForRole(t.getName());
        t.setIsActive(false);
        templateRepo.save(t);
    }

    // ── Permission management ─────────────────────────────────────────────────

    /**
     * Add a permission rule to a role.
     * pageKey  → stored as obj="page:<pageKey>" with act="access" (sidebar visibility)
     *          → also stored as the resource-level rule
     * Example: addPermission("role_template_1", "*", "*prod*", "Deployment", ["view","create"])
     *          → p, role_template_1, *, *prod*, Deployment, view
     *          → p, role_template_1, *, *prod*, Deployment, create
     *          → p, role_template_1, *, *, page:deployments, access
     */
    @Transactional
    public void addPermission(Long roleId, String pageKey, String clusterUid,
                              String nsPattern, String resourceKind, List<String> actions) {
        RoleTemplate role = getById(roleId);
        String ns  = (nsPattern == null || nsPattern.isBlank())     ? "*" : nsPattern;
        String kind = (resourceKind == null || resourceKind.isBlank()) ? "*" : resourceKind;
        String dom = (clusterUid == null || clusterUid.isBlank())   ? "*" : clusterUid;

        // Page access rule — stored with specific cluster so sidebar is cluster-aware
        if (pageKey != null && !pageKey.isBlank()) {
            casbinService.addPolicy(role.getName(), dom, "*", "page:" + pageKey, "access");
        }

        // Resource rules per action.
        // Always ensure 'view' is present as a baseline so the resource list page is visible.
        List<String> effectiveActions = new java.util.ArrayList<>(actions);
        boolean isResourceKind = kind != null && !kind.isBlank() && !kind.equals("*")
                && !kind.startsWith("page:");
        if (isResourceKind && !effectiveActions.contains("view")) {
            effectiveActions.add(0, "view");
        }
        for (String action : effectiveActions) {
            casbinService.addPolicy(role.getName(), dom, ns, kind, action);
        }
    }

    @Transactional
    public void removeAllPermissions(Long roleId) {
        RoleTemplate role = getById(roleId);
        casbinService.removeAllPoliciesForRole(role.getName());
        nameFilterRepo.deleteByRoleName(role.getName());
    }

    // ── Name filter management ─────────────────────────────────────────────────

    public List<RoleNameFilter> getNameFilters(Long roleId) {
        RoleTemplate role = getById(roleId);
        return nameFilterRepo.findByRoleName(role.getName());
    }

    @Transactional
    public RoleNameFilter addNameFilter(Long roleId, String clusterUid, String nsPattern,
                                        String resourceKind, String namePattern) {
        RoleTemplate role = getById(roleId);
        String dom = (clusterUid == null || clusterUid.isBlank()) ? "*" : clusterUid;
        String ns  = (nsPattern  == null || nsPattern.isBlank())  ? "*" : nsPattern;
        String pat = (namePattern == null || namePattern.isBlank()) ? "*" : namePattern;

        RoleNameFilter f = RoleNameFilter.builder()
                .roleName(role.getName())
                .clusterUid(dom)
                .nsPattern(ns)
                .resourceKind(resourceKind)
                .namePattern(pat)
                .build();
        return nameFilterRepo.save(f);
    }

    @Transactional
    public void removeNameFilter(Long roleId, Long filterId) {
        RoleTemplate role = getById(roleId);
        RoleNameFilter filter = nameFilterRepo.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found: " + filterId));
        if (!filter.getRoleName().equals(role.getName())) {
            throw new IllegalArgumentException("Filter does not belong to this role");
        }
        nameFilterRepo.deleteById(filterId);
    }

    /**
     * Returns effective name patterns for a user accessing a resource in a cluster.
     * Logic:
     * - Get all roles the user holds (for this cluster or wildcard *)
     * - For each role, check if there's a name filter for the resource kind
     * - If ANY role has no filter → return ["*"] (no restriction)
     * - Otherwise return union of all patterns
     */
    public List<String> getEffectiveNamePatterns(String username, String clusterUid, String resourceKind) {
        return getEffectiveFilters(username, clusterUid, resourceKind).stream()
                .map(RoleNameFilter::getNamePattern)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Returns full filter entries (nsPattern + namePattern) for a user accessing a resource.
     * Used by the frontend to filter by BOTH namespace and name pattern client-side.
     *
     * Returns null (meaning "wildcard / no restriction") if any role grants unrestricted access.
     */
    public List<RoleNameFilter> getEffectiveFilters(String username, String clusterUid, String resourceKind) {
        List<CasbinRule> bindings = casbinService.getUserRoleBindings(username);

        // Collect role names applicable to this cluster
        List<String> applicableRoles = bindings.stream()
                .filter(b -> "*".equals(b.getV2()) || clusterUid.equals(b.getV2()))
                .map(CasbinRule::getV1)
                .distinct()
                .collect(Collectors.toList());

        if (applicableRoles.isEmpty()) return List.of();

        // SUPERADMIN always sees all
        if (applicableRoles.contains("SUPERADMIN")) return List.of();

        List<RoleNameFilter> filters = nameFilterRepo.findEffectiveFilters(
                applicableRoles, clusterUid, resourceKind);

        // Map: roleName → filters for that role
        Map<String, List<RoleNameFilter>> byRole = filters.stream()
                .collect(Collectors.groupingBy(RoleNameFilter::getRoleName));

        // If any applicable role has NO filter configured → wildcard (see all)
        for (String roleName : applicableRoles) {
            if (!byRole.containsKey(roleName)) return List.of();
        }

        return filters;
    }

    // ── User role assignments ─────────────────────────────────────────────────

    @Transactional
    public void assignRoleToUser(Long roleId, String username, List<String> clusterUids) {
        RoleTemplate role = getById(roleId);
        List<String> uids = (clusterUids == null || clusterUids.isEmpty())
                ? List.of("*") : clusterUids;
        for (String uid : uids) {
            casbinService.assignRole(username, role.getName(), uid);
        }
    }

    @Transactional
    public void removeRoleFromUser(Long roleId, String username, String clusterUid) {
        RoleTemplate role = getById(roleId);
        String uid = (clusterUid == null || clusterUid.isBlank()) ? "*" : clusterUid;
        casbinService.removeRole(username, role.getName(), uid);
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    public List<Map<String, Object>> getPermissions(Long roleId) {
        RoleTemplate role = getById(roleId);
        return casbinService.getPoliciesForRole(role.getName()).stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role",     get(p, 0));
                    m.put("cluster",  get(p, 1));
                    m.put("namespace",get(p, 2));
                    m.put("resource", get(p, 3));
                    m.put("action",   get(p, 4));
                    return m;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUserBindings(Long roleId) {
        RoleTemplate role = getById(roleId);
        return casbinService.getRoleBindings(role.getName()).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username",   r.getV0());
                    m.put("role",       r.getV1());
                    m.put("clusterUid", r.getV2());
                    // Enrich with user displayName if available
                    userRepository.findByUsername(r.getV0()).ifPresent(u -> {
                        m.put("displayName", u.getUsername());
                        m.put("email", u.getEmail());
                    });
                    return m;
                }).collect(Collectors.toList());
    }

    private String get(List<String> list, int i) {
        return i < list.size() ? list.get(i) : "";
    }
}
