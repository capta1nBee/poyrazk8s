package com.k8s.platform.controller.admin;

import com.k8s.platform.domain.entity.casbin.RoleTemplate;
import com.k8s.platform.domain.repository.ActionRepository;
import com.k8s.platform.domain.repository.PageRepository;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import com.k8s.platform.service.casbin.RoleTemplateService;
import com.k8s.platform.util.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleTemplateController {

    private final RoleTemplateService roleTemplateService;
    private final CasbinPermissionService casbinService;
    private final SecurityUtils securityUtils;
    private final PageRepository pageRepository;
    private final ActionRepository actionRepository;

    // ── Role Template CRUD ────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<RoleTemplate>> listRoles() {
        return ResponseEntity.ok(roleTemplateService.listAll());
    }

    @PostMapping
    public ResponseEntity<RoleTemplate> createRole(@RequestBody RoleRequest req) {
        Long userId = securityUtils.getCurrentUser() != null
                ? securityUtils.getCurrentUser().getId() : null;
        return ResponseEntity.ok(roleTemplateService.create(
                req.getName(), req.getDisplayName(), req.getDescription(), req.getColor(), userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleTemplate> updateRole(@PathVariable Long id, @RequestBody RoleRequest req) {
        return ResponseEntity.ok(roleTemplateService.update(id,
                req.getDisplayName(), req.getDescription(), req.getColor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Permission management ─────────────────────────────────────────────────

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<Map<String, Object>>> getPermissions(@PathVariable Long id) {
        return ResponseEntity.ok(roleTemplateService.getPermissions(id));
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<Void> addPermission(@PathVariable Long id,
                                              @RequestBody PermissionRequest req) {
        roleTemplateService.addPermission(id, req.getPageKey(), req.getClusterUid(),
                req.getNamespacePattern(), req.getResourceKind(), req.getActions());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/permissions")
    public ResponseEntity<Void> removeAllPermissions(@PathVariable Long id) {
        roleTemplateService.removeAllPermissions(id);
        return ResponseEntity.ok().build();
    }

    /** Remove a single Casbin policy line from a role. */
    @DeleteMapping("/{id}/permissions/single")
    public ResponseEntity<Void> removeSinglePermission(@PathVariable Long id,
                                                       @RequestBody SinglePermissionRequest req) {
        RoleTemplate role = roleTemplateService.getById(id);
        casbinService.removePolicy(role.getName(),
                req.getCluster(), req.getNamespace(), req.getResource(), req.getAction());
        return ResponseEntity.ok().build();
    }

    // ── User assignments ──────────────────────────────────────────────────────

    @GetMapping("/{id}/users")
    public ResponseEntity<List<Map<String, Object>>> getUserBindings(@PathVariable Long id) {
        return ResponseEntity.ok(roleTemplateService.getUserBindings(id));
    }

    @PostMapping("/{id}/users")
    public ResponseEntity<Void> assignUser(@PathVariable Long id,
                                           @RequestBody UserAssignRequest req) {
        roleTemplateService.assignRoleToUser(id, req.getUsername(), req.getClusterUids());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/users/{username}")
    public ResponseEntity<Void> removeUser(@PathVariable Long id,
                                           @PathVariable String username,
                                           @RequestParam(defaultValue = "*") String clusterUid) {
        roleTemplateService.removeRoleFromUser(id, username, clusterUid);
        return ResponseEntity.ok().build();
    }

    // ── User-centric assignment endpoints ────────────────────────────────────

    /** Get all role template assignments for a specific user */
    @GetMapping("/users/{username}")
    public ResponseEntity<List<Map<String, Object>>> getUserAssignments(@PathVariable String username) {
        var bindings = casbinService.getUserRoleBindings(username);
        var result = bindings.stream().map(b -> {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("username",   b.getV0());
            m.put("roleName",   b.getV1());
            m.put("clusterUid", b.getV2());
            // enrich with role template metadata
            roleTemplateService.listAll().stream()
                    .filter(r -> r.getName().equals(b.getV1()))
                    .findFirst()
                    .ifPresent(r -> {
                        m.put("roleId",          r.getId());
                        m.put("roleDisplayName", r.getDisplayName() != null ? r.getDisplayName() : r.getName());
                        m.put("roleColor",       r.getColor());
                    });
            return (Map<String, Object>) m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** Remove ALL role assignments for a user (reset) */
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Void> removeAllUserAssignments(@PathVariable String username) {
        casbinService.removeAllRolesForUser(username);
        return ResponseEntity.noContent().build();
    }

    // ── My permissions (used by frontend after login) ─────────────────────────

    /**
     * Returns pages visible in the sidebar.
     * If clusterUid is provided (non-wildcard), returns only pages accessible in that cluster.
     * If clusterUid is omitted or "*", returns pages accessible in any cluster (for initial load).
     */
    @GetMapping("/my-pages")
    public ResponseEntity<Map<String, Object>> getMyPages(
            @RequestParam(required = false, defaultValue = "*") String clusterUid) {
        var user = securityUtils.getCurrentUser();
        if (user == null) return ResponseEntity.ok(Map.of("pages", List.of(), "isSuperadmin", false));
        boolean isSuperadmin = Boolean.TRUE.equals(user.getIsSuperadmin());
        Set<String> pages = isSuperadmin
                ? new java.util.LinkedHashSet<>(casbinService.listAllPageKeys())
                : casbinService.getAccessiblePages(user.getUsername(), clusterUid);
        return ResponseEntity.ok(Map.of(
                "pages", pages,
                "isSuperadmin", isSuperadmin));
    }

    /**
     * Dynamic config — pages with their actions come from DB, never hardcoded.
     * Used by the Role Management UI.
     * Each page entry includes its own action list so the UI can filter
     * actions when a specific page is selected.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        var pages = pageRepository.findByIsActiveTrue().stream()
                .map(p -> {
                    var acts = actionRepository.findByPageIdAndIsActiveTrue(p.getId()).stream()
                            .map(a -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("code", a.getActionCode());
                                m.put("displayName", a.getDisplayName() != null ? a.getDisplayName() : a.getActionCode());
                                m.put("requiresWrite", Boolean.TRUE.equals(a.getRequiresWrite()));
                                m.put("isDangerous", Boolean.TRUE.equals(a.getIsDangerous()));
                                return m;
                            })
                            .collect(Collectors.toList());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", p.getName());
                    m.put("displayName", p.getDisplayName() != null ? p.getDisplayName() : p.getName());
                    m.put("resourceKind", p.getResourceKind() != null ? p.getResourceKind() : "");
                    m.put("icon", p.getIcon() != null ? p.getIcon() : "");
                    m.put("isNamespaceScoped", Boolean.TRUE.equals(p.getIsNamespaceScoped()));
                    // page_tier: 1=namespace+name+action, 2=name+action, 3=page-only
                    int tier = 3;
                    if (p.getPageTier() != null) {
                        tier = p.getPageTier();
                    } else if (p.getResourceKind() != null && !p.getResourceKind().isBlank()) {
                        tier = Boolean.TRUE.equals(p.getIsNamespaceScoped()) ? 1 : 2;
                    }
                    m.put("pageTier", tier);
                    m.put("actions", acts);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("pages", pages));
    }

    /**
     * Resource-kind → action-codes map from DB.
     * Used by ResourceActionMenu.vue to dynamically determine
     * which actions to permission-check for each K8s resource kind.
     */
    @GetMapping("/action-map")
    public ResponseEntity<Map<String, List<String>>> getActionMap() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        actionRepository.findAll().stream()
                .filter(a -> a.getResourceKind() != null
                        && (Boolean.TRUE.equals(a.getIsActive()) || a.getIsActive() == null))
                .forEach(a -> result
                        .computeIfAbsent(a.getResourceKind(), k -> new ArrayList<>())
                        .add(a.getActionCode()));
        // deduplicate per kind
        result.replaceAll((k, v) -> v.stream().distinct().collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    /**
     * Runtime permission check — called by frontend action menus.
     * Body: { clusterUid, namespace, resourceKind, action }
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkPermission(@RequestBody PermissionCheckRequest req) {
        var user = securityUtils.getCurrentUser();
        if (user == null) return ResponseEntity.ok(Map.of("allowed", false));
        // SuperAdmin always allowed — bypasses Casbin entirely
        if (Boolean.TRUE.equals(user.getIsSuperadmin()))
            return ResponseEntity.ok(Map.of("allowed", true));
        boolean allowed = casbinService.hasPermission(
                user.getUsername(),
                req.getClusterUid() != null ? req.getClusterUid() : "*",
                req.getNamespace()   != null ? req.getNamespace()   : "*",
                req.getResourceKind() != null ? req.getResourceKind() : "*",
                req.getAction()      != null ? req.getAction()      : "view");
        return ResponseEntity.ok(Map.of("allowed", allowed));
    }

    // ── Name Filters ──────────────────────────────────────────────────────────

    /** List name filters for a role */
    @GetMapping("/{id}/name-filters")
    public ResponseEntity<List<Map<String, Object>>> getNameFilters(@PathVariable Long id) {
        return ResponseEntity.ok(roleTemplateService.getNameFilters(id).stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("roleName", f.getRoleName());
                    m.put("clusterUid", f.getClusterUid());
                    m.put("nsPattern", f.getNsPattern());
                    m.put("resourceKind", f.getResourceKind());
                    m.put("namePattern", f.getNamePattern());
                    return m;
                }).collect(Collectors.toList()));
    }

    /** Add a name filter to a role */
    @PostMapping("/{id}/name-filters")
    public ResponseEntity<Map<String, Object>> addNameFilter(@PathVariable Long id,
                                                             @RequestBody NameFilterRequest req) {
        var f = roleTemplateService.addNameFilter(id, req.getClusterUid(),
                req.getNsPattern(), req.getResourceKind(), req.getNamePattern());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("roleName", f.getRoleName());
        m.put("clusterUid", f.getClusterUid());
        m.put("nsPattern", f.getNsPattern());
        m.put("resourceKind", f.getResourceKind());
        m.put("namePattern", f.getNamePattern());
        return ResponseEntity.ok(m);
    }

    /** Delete a single name filter */
    @DeleteMapping("/{id}/name-filters/{filterId}")
    public ResponseEntity<Void> removeNameFilter(@PathVariable Long id,
                                                 @PathVariable Long filterId) {
        roleTemplateService.removeNameFilter(id, filterId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get effective name+namespace filters for the current user.
     * Used by frontend to filter resource lists client-side by BOTH name and namespace.
     *
     * Returns:
     *   { patterns: ["*kayit*"], nsPatterns: ["*prod*"],
     *     filters: [{ nsPattern: "*prod*", namePattern: "*kayit*" }] }
     *
     * Empty patterns list means "wildcard" (no restriction / superadmin).
     */
    @GetMapping("/my-name-filters")
    public ResponseEntity<Map<String, Object>> getMyNameFilters(
            @RequestParam String resourceKind,
            @RequestParam(defaultValue = "*") String clusterUid) {
        var user = securityUtils.getCurrentUser();
        if (user == null) return ResponseEntity.ok(Map.of(
                "patterns", List.of(), "nsPatterns", List.of(), "filters", List.of()));
        if (Boolean.TRUE.equals(user.getIsSuperadmin()))
            return ResponseEntity.ok(Map.of(
                    "patterns", List.of("*"), "nsPatterns", List.of("*"), "filters", List.of()));

        var filterEntries = roleTemplateService.getEffectiveFilters(
                user.getUsername(), clusterUid, resourceKind);

        // Empty filterEntries means "wildcard" (any of the user's roles grants full access)
        if (filterEntries.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "patterns", List.of("*"), "nsPatterns", List.of("*"), "filters", List.of()));
        }

        List<String> namePatterns = filterEntries.stream()
                .map(f -> f.getNamePattern())
                .distinct()
                .collect(Collectors.toList());

        List<String> nsPatterns = filterEntries.stream()
                .map(f -> f.getNsPattern())
                .distinct()
                .collect(Collectors.toList());

        List<Map<String, String>> filters = filterEntries.stream()
                .map(f -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("nsPattern", f.getNsPattern());
                    m.put("namePattern", f.getNamePattern());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "patterns", namePatterns,
                "nsPatterns", nsPatterns,
                "filters", filters));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Data
    public static class RoleRequest {
        private String name;
        private String displayName;
        private String description;
        private String color;
    }

    @Data
    public static class PermissionRequest {
        private String pageKey;
        private String clusterUid;
        private String namespacePattern;
        private String resourceKind;
        private List<String> actions;
    }

    @Data
    public static class UserAssignRequest {
        private String username;
        private List<String> clusterUids;
    }

    @Data
    public static class PermissionCheckRequest {
        private String clusterUid;
        private String namespace;
        private String resourceKind;
        private String action;
    }

    @Data
    public static class SinglePermissionRequest {
        private String cluster;
        private String namespace;
        private String resource;
        private String action;
    }

    @Data
    public static class NameFilterRequest {
        private String clusterUid;
        private String nsPattern;
        private String resourceKind;
        private String namePattern;
    }
}
