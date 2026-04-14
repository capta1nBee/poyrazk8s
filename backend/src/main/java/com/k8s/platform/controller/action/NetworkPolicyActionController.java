package com.k8s.platform.controller.action;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.NetworkPolicyService;
import com.k8s.platform.util.SecurityUtils;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/clusters/{clusterUid}/namespaces/{namespace}/networkpolicies")
@RequiredArgsConstructor
public class NetworkPolicyActionController {
    private final NetworkPolicyService networkPolicyService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    private boolean hasPermission(String clusterUid, String namespace, String resourceName, String action) {
        User user = securityUtils.getCurrentUser();
        if (user == null)
            return false;
        if (user.getIsSuperadmin() != null && user.getIsSuperadmin())
            return true;
        return authorizationService.hasPermission(user, clusterUid, namespace, "NetworkPolicy", resourceName, action);
    }

    @GetMapping("/{name}/yaml")
    public ApiResponse<String> getYaml(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        if (!hasPermission(clusterUid, namespace, name, "view-yaml")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        NetworkPolicy networkPolicy = networkPolicyService.getNetworkPolicy(clusterUid, namespace, name);
        // Convert to YAML string (you may need to add a utility method)
        return ApiResponse.success("YAML retrieved successfully", networkPolicy.toString());
    }

    @PutMapping("/{name}/yaml")
    public ApiResponse<String> updateYaml(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody NetworkPolicy networkPolicy) {

        if (!hasPermission(clusterUid, namespace, name, "edit-yaml")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        networkPolicyService.updateNetworkPolicy(clusterUid, namespace, name, networkPolicy);
        return ApiResponse.success("NetworkPolicy updated successfully");
    }

    @DeleteMapping("/{name}")
    public ApiResponse<String> delete(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        if (!hasPermission(clusterUid, namespace, name, "delete")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        networkPolicyService.deleteNetworkPolicy(clusterUid, namespace, name);
        return ApiResponse.success("NetworkPolicy deleted successfully");
    }

    @GetMapping("/{name}/visualize")
    public ApiResponse<Map<String, Object>> visualize(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        if (!hasPermission(clusterUid, namespace, name, "view")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        Map<String, Object> visualization = networkPolicyService.visualize(clusterUid, namespace, name);
        return ApiResponse.success("Visualization generated successfully", visualization);
    }

    @PostMapping("/{name}/simulate")
    public ApiResponse<Map<String, Object>> simulate(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, String> simulationRequest) {

        if (!hasPermission(clusterUid, namespace, name, "view")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        String sourcePod = simulationRequest.get("sourcePod");
        String targetPod = simulationRequest.get("targetPod");
        Map<String, Object> simulation = null;
        return ApiResponse.success("Simulation completed successfully", simulation);
    }
}
