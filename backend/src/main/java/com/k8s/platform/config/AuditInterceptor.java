package com.k8s.platform.config;

import com.k8s.platform.service.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically logs every mutating API request (POST / PUT / PATCH / DELETE)
 * to the audit_logs table.  Read-only GET requests are not logged to avoid noise.
 *
 * Skipped paths:
 *   - /api/auth/**          (login / refresh — logged separately)
 *   - /api/securityrules/** (agent heartbeats)
 *   - /actuator/**
 *   - /ws/**
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "audit_start_ms";

    private static final Set<String> LOGGED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    // Paths whose prefix should be skipped entirely
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/api/auth/",
            "/api/securityrules/",
            "/actuator/",
            "/ws/"
    );

    // Paths that contain these substrings should be skipped (agent traffic — too noisy)
    private static final Set<String> SKIP_CONTAINS = Set.of(
            "/flows/batch"
    );

    // Extract clusterUid from URL patterns like /api/k8s/{clusterUid}/... or /api/clusters/{clusterUid}/...
    private static final Pattern CLUSTER_UID_PATTERN =
            Pattern.compile("/(?:k8s|clusters|network)/([0-9a-f\\-]{8,})");

    private final AuditLogService auditLogService;

    // ── pre-handle: stamp start time ────────────────────────────────────────

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START, System.currentTimeMillis());
        return true;
    }

    // ── after-completion: write audit row ────────────────────────────────────

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        if (!LOGGED_METHODS.contains(method)) return;

        String path = request.getRequestURI();
        if (shouldSkip(path)) return;

        try {
            long durationMs = System.currentTimeMillis()
                    - (long) request.getAttribute(ATTR_START);

            String username = resolveUsername();
            String clusterUid = extractClusterUid(path);
            String action = buildAction(method, path);
            String details = buildDetails(method, path, response.getStatus(), durationMs, ex);

            auditLogService.log(username, action, details, clusterUid, null);

        } catch (Exception e) {
            // Never let audit failures disrupt the response
            log.warn("[AUDIT] Failed to write audit log for {} {}: {}", method, path, e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean shouldSkip(String path) {
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        for (String part : SKIP_CONTAINS) {
            if (path.contains(part)) return true;
        }
        return false;
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String extractClusterUid(String path) {
        Matcher m = CLUSTER_UID_PATTERN.matcher(path);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Build a short, readable action string, e.g.
     *   POST /api/k8s/{uid}/pods             → "pods:create"
     *   DELETE /api/k8s/{uid}/deployments/x  → "deployments:delete"
     *   PUT /api/admin/roles/3               → "admin-roles:update"
     */
    private String buildAction(String method, String path) {
        // Strip cluster-uid segment for readability
        String normalized = path
                .replaceAll("/api/k8s/[^/]+", "")
                .replaceAll("/api/clusters/[^/]+", "")
                .replaceAll("/api/network/[^/]+", "")
                .replaceAll("/api/", "")
                .replaceAll("/[0-9a-f\\-]{8,}$", "") // strip trailing UUID/id
                .replaceAll("/[0-9]+$", "")           // strip trailing numeric id
                .replace('/', '-');

        String verb = switch (method) {
            case "POST"   -> "create";
            case "PUT"    -> "update";
            case "PATCH"  -> "patch";
            case "DELETE" -> "delete";
            default       -> method.toLowerCase();
        };

        return normalized + ":" + verb;
    }

    private String buildDetails(String method, String path, int status, long durationMs, Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(path);
        sb.append(" | status=").append(status);
        sb.append(" | ").append(durationMs).append("ms");
        if (ex != null) {
            sb.append(" | error=").append(ex.getMessage());
        }
        return sb.toString();
    }
}
