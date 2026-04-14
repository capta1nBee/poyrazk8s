package com.k8s.platform.controller.appcreator;

import com.k8s.platform.domain.dto.appcreator.*;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.appcreator.GitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing Git provider connections (Phase 2 — GitOps).
 * Base path: /api/k8s/{clusterUid}/appcreator/git-connections
 *
 * All operations are scoped to the currently authenticated user
 * (connections are personal / per-user, not per-cluster).
 */
@RestController
@RequestMapping("/api/k8s/{clusterUid}/appcreator/git-connections")
@RequiredArgsConstructor
public class GitConnectionController {

    private final GitService gitService;
    private final ResourceAuthorizationHelper authHelper;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<GitConnectionDto>> list(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow("appcreator"); // global — not cluster-specific
        Long userId = requireUserId();
        return ResponseEntity.ok(gitService.listForCluster(clusterUid, userId));
    }

    @PostMapping
    public ResponseEntity<GitConnectionDto> create(
            @PathVariable String clusterUid,
            @RequestBody GitConnectionCreateRequest request) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        Long userId = requireUserId();
        return ResponseEntity.ok(gitService.create(clusterUid, userId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GitConnectionDto> update(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestBody GitConnectionCreateRequest request) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        Long userId = requireUserId();
        return ResponseEntity.ok(gitService.update(clusterUid, id, userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String clusterUid,
            @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        Long userId = requireUserId();
        gitService.delete(clusterUid, id, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Repository listing ────────────────────────────────────────────────────

    @GetMapping("/{id}/repos")
    public ResponseEntity<List<GitRepoDto>> listRepos(
            @PathVariable String clusterUid,
            @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        Long userId = requireUserId();
        return ResponseEntity.ok(gitService.listRepositories(clusterUid, id, userId));
    }

    // ── Branch listing ────────────────────────────────────────────────────────

    /**
     * List branches for a given repository.
     * The repo is identified by its full name in the form "owner/repo"
     * passed as a query parameter: ?repo=owner%2Frepo
     */
    @GetMapping("/{id}/branches")
    public ResponseEntity<List<GitBranchDto>> listBranches(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestParam String repo) {
        authHelper.checkPagePermissionOrThrow("appcreator");
        Long userId = requireUserId();
        // Split "owner/repo" → owner + repo
        String[] parts = repo.split("/", 2);
        if (parts.length != 2) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(gitService.listBranches(clusterUid, id, userId, parts[0], parts[1]));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long requireUserId() {
        var user = authHelper.getCurrentUser();
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return user.getId();
    }
}

