package com.k8s.platform.controller.action;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.action.PodActionService;
import com.k8s.platform.service.authorization.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clusters/{clusterId}/namespaces/{namespace}/pods")
@RequiredArgsConstructor
@Slf4j
public class PodActionController {
    private final PodActionService podActionService;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final ClusterRepository clusterRepository;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        
        if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        } else if (authentication.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) authentication.getPrincipal()).getUsername();
            return userRepository.findByUsername(username).orElse(null);
        } else {
            String username = authentication.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
    }

    private boolean hasPermission(Long clusterId, String namespace, String resourceName, String action) {
        User user = getCurrentUser();
        if (user == null) return false;
        if (user.getIsSuperadmin() != null && user.getIsSuperadmin()) return true;
        
        // Get cluster UID from cluster ID
        Cluster cluster = clusterRepository.findById(clusterId).orElse(null);
        String clusterUid = cluster != null ? cluster.getUid() : null;
        
        return authorizationService.hasPermission(user, clusterUid, namespace, "Pod", resourceName, action);
    }

    @PostMapping("/{podName}/restart")
    public ApiResponse<String> restartPod(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String podName) {
        if (!hasPermission(clusterId, namespace, podName, "restart")) {
            log.warn("User attempted to restart pod without permission: {}/{}", namespace, podName);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        
        podActionService.restartPod(clusterId, namespace, podName);
        return ApiResponse.success("Pod restarted successfully");
    }

    @DeleteMapping("/{podName}")
    public ApiResponse<String> deletePod(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String podName,
            @RequestParam(required = false, defaultValue = "false") Boolean force) {
        String action = force ? "force-delete" : "delete";
        if (!hasPermission(clusterId, namespace, podName, action)) {
            log.warn("User attempted to delete pod without permission: {}/{}", namespace, podName);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        
        podActionService.deletePod(clusterId, namespace, podName, force);
        return ApiResponse.success("Pod deleted successfully");
    }

    @GetMapping("/{podName}/metrics")
    public ApiResponse<?> getPodMetrics(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String podName) {
        if (!hasPermission(clusterId, namespace, podName, "metrics")) {
            log.warn("User attempted to view pod metrics without permission: {}/{}", namespace, podName);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        
        return ApiResponse.success("Pod metrics retrieved", podActionService.getPodMetrics(clusterId, namespace, podName));
    }
}
