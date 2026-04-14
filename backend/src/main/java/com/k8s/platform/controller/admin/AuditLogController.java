package com.k8s.platform.controller.admin;

import com.k8s.platform.domain.entity.AuditLog;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Audit log viewer — superadmin only.
 * No Casbin resource policy is needed: audit logs are a platform-level
 * administrative feature, not a per-cluster/per-namespace resource.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> getLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String details,
            @RequestParam(required = false) String clusterUid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        User user = authHelper.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!Boolean.TRUE.equals(user.getIsSuperadmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        Sort sort = direction.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        // Superadmin sees all clusters — pass null for allowedClusterUids (no filter)
        return ResponseEntity.ok(
                auditLogService.getLogs(username, action, details, clusterUid, null, pageRequest));
    }
}
