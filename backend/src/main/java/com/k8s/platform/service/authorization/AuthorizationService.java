package com.k8s.platform.service.authorization;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.authorization.UIPermission;
import com.k8s.platform.domain.repository.PageRepository;
import com.k8s.platform.domain.repository.authorization.UIPermissionRepository;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Thin delegation wrapper — keeps existing controller signatures intact.
 * Permission logic delegates to Casbin; page metadata comes from PageRepository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final CasbinPermissionService casbinPermissionService;
    private final PageRepository pageRepository;
    private final UIPermissionRepository uiPermissionRepository;

    // ── Permission checks (Casbin) ────────────────────────────────────────────

    public boolean hasPermission(User user, String clusterUid, String namespace,
            String resourceKind, String resourceName, String action) {
        return casbinPermissionService.hasPermission(
                user.getUsername(), clusterUid, namespace, resourceKind, action);
    }

    public boolean hasPageAccess(User user, String pageName) {
        return casbinPermissionService.canAccessPage(user.getUsername(), "*", pageName);
    }

    public Set<String> getUserPages(User user) {
        return casbinPermissionService.getAccessiblePages(user.getUsername());
    }

    public List<String> getAllowedClusterUids(User user, String resourceKind, String action) {
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) return null;
        Set<String> uids = casbinPermissionService.getAccessibleClusterUids(user.getUsername());
        return uids.contains("*") ? null : new ArrayList<>(uids);
    }

    // ── Page metadata (PageRepository) ───────────────────────────────────────

    /** Returns all active page names (= sidebar pageKeys). */
    public List<String> getAllActivePageNames() {
        return pageRepository.findByIsActiveTrue().stream()
                .map(com.k8s.platform.domain.entity.Page::getName)
                .collect(Collectors.toList());
    }

    /** Returns distinct resource kinds of all active pages (nulls excluded). */
    public List<String> getAllResourceKinds() {
        return pageRepository.findByIsActiveTrue().stream()
                .map(com.k8s.platform.domain.entity.Page::getResourceKind)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Returns name + displayName + resourceKind for all active pages. */
    public List<Map<String, String>> getAllPagesWithDetails() {
        return pageRepository.findByIsActiveTrue().stream()
                .map(p -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name",         p.getName());
                    m.put("displayName",  p.getDisplayName());
                    m.put("resourceKind", p.getResourceKind() != null ? p.getResourceKind() : "");
                    m.put("icon",         p.getIcon() != null ? p.getIcon() : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── UIPermission CRUD (legacy table, kept for API compatibility) ──────────

    /** Get (or empty-default) UIPermission for a user. */
    public UIPermission getUserUIPermissions(Long userId) {
        return uiPermissionRepository.findByUserId(userId)
                .orElseGet(() -> UIPermission.builder()
                        .userId(userId)
                        .pages("[]")
                        .features("[]")
                        .build());
    }

    /** Save or update a UIPermission record. */
    public UIPermission saveUIPermission(UIPermission permission) {
        // upsert: if a record exists for this user, merge into it
        uiPermissionRepository.findByUserId(permission.getUserId()).ifPresent(existing -> {
            permission.setId(existing.getId());
        });
        return uiPermissionRepository.save(permission);
    }

    /** Delete all stored UIPermissions for a user. */
    @Transactional
    public void deleteUserPermissions(Long userId) {
        uiPermissionRepository.deleteByUserId(userId);
    }
}
