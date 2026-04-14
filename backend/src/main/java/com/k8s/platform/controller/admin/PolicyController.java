package com.k8s.platform.controller.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @deprecated The old JSON-based policy system has been replaced by the Casbin RBAC system.
 * All policy management is now handled by {@code RoleTemplateController}.
 * This class exists only to avoid a compilation error during the migration period.
 */
@RestController
@RequestMapping("/api/admin/policies-legacy")
@Deprecated
public class PolicyController {

    @RequestMapping("/**")
    public ResponseEntity<String> notAvailable() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("This endpoint is no longer available. Use /api/admin/roles instead.");
    }
}
