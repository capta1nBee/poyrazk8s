package com.k8s.platform.util;

import com.k8s.platform.exception.PermissionDeniedException;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility class for permission checking in services.
 * Backed by Casbin (replaces the old JSON-policy PolicyService).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionValidator {

    private final CasbinPermissionService casbinPermissionService;

    /**
     * Validate permission and throw {@link PermissionDeniedException} if denied.
     * (resourceName is no longer part of the Casbin model — cluster/ns/kind/action suffice.)
     */
    public void validatePermission(String clusterUid, String namespace, String resourceKind,
            String resourceName, String action) {
        String username = getCurrentUsername();
        boolean hasPermission = casbinPermissionService.hasPermission(
                username, clusterUid, namespace, resourceKind, action);
        if (!hasPermission) {
            throw new PermissionDeniedException(
                    username, clusterUid, namespace, resourceKind, resourceName, action);
        }
    }

    /**
     * Check permission without throwing exception.
     */
    public boolean checkPermission(String clusterUid, String namespace, String resourceKind,
            String resourceName, String action) {
        String username = getCurrentUsername();
        return casbinPermissionService.hasPermission(
                username, clusterUid, namespace, resourceKind, action);
    }

    /**
     * Get current authenticated username.
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
