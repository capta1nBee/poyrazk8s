package com.k8s.platform.controller.admin;

import com.k8s.platform.dto.request.ActionRequest;
import com.k8s.platform.dto.request.PageRequest;
import com.k8s.platform.dto.response.ActionResponse;
import com.k8s.platform.dto.response.PageResponse;
import com.k8s.platform.service.PageActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/pages-actions")
@RequiredArgsConstructor
public class PageActionController {

    private final PageActionService pageActionService;

    // ==================== PUBLIC ENDPOINTS (No auth required for UI) ====================
    
    /**
     * Get actions by resource kind - Public endpoint for UI permission checks
     * This endpoint is used by the frontend to determine which actions to show
     */
    @GetMapping("/actions/resource-kind/{resourceKind}")
    public ResponseEntity<List<ActionResponse>> getActionsByResourceKindPublic(@PathVariable String resourceKind) {
        return ResponseEntity.ok(pageActionService.getActionsByResourceKind(resourceKind));
    }

    // ==================== ADMIN ENDPOINTS (SUPERADMIN only) ====================

    // Page endpoints
    @GetMapping("/pages")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<PageResponse>> getAllPages() {
        return ResponseEntity.ok(pageActionService.getAllPages());
    }

    @GetMapping("/pages/active")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<PageResponse>> getActivePages() {
        return ResponseEntity.ok(pageActionService.getActivePages());
    }

    @GetMapping("/pages/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<PageResponse> getPageById(@PathVariable Long id) {
        return ResponseEntity.ok(pageActionService.getPageById(id));
    }

    @GetMapping("/pages/name/{name}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<PageResponse> getPageByName(@PathVariable String name) {
        return ResponseEntity.ok(pageActionService.getPageByName(name));
    }

    @PostMapping("/pages")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<PageResponse> createPage(@RequestBody PageRequest request) {
        return ResponseEntity.ok(pageActionService.createPage(request));
    }

    @PutMapping("/pages/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<PageResponse> updatePage(@PathVariable Long id, @RequestBody PageRequest request) {
        return ResponseEntity.ok(pageActionService.updatePage(id, request));
    }

    @DeleteMapping("/pages/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        pageActionService.deletePage(id);
        return ResponseEntity.noContent().build();
    }

    // Action endpoints
    @GetMapping("/actions/page/{pageId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<ActionResponse>> getActionsByPageId(@PathVariable Long pageId) {
        return ResponseEntity.ok(pageActionService.getActionsByPageId(pageId));
    }

    @GetMapping("/actions/resource/{resourceKind}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<ActionResponse>> getActionsByResourceKind(@PathVariable String resourceKind) {
        return ResponseEntity.ok(pageActionService.getActionsByResourceKind(resourceKind));
    }

    @GetMapping("/actions/code/{actionCode}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<ActionResponse>> getActionsByActionCode(@PathVariable String actionCode) {
        return ResponseEntity.ok(pageActionService.getActionsByActionCode(actionCode));
    }

    @GetMapping("/actions/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ActionResponse> getActionById(@PathVariable Long id) {
        return ResponseEntity.ok(pageActionService.getActionById(id));
    }

    @PostMapping("/actions")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ActionResponse> createAction(@RequestBody ActionRequest request) {
        return ResponseEntity.ok(pageActionService.createAction(request));
    }

    @PutMapping("/actions/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ActionResponse> updateAction(@PathVariable Long id, @RequestBody ActionRequest request) {
        return ResponseEntity.ok(pageActionService.updateAction(id, request));
    }

    @DeleteMapping("/actions/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteAction(@PathVariable Long id) {
        pageActionService.deleteAction(id);
        return ResponseEntity.noContent().build();
    }
}
