package com.k8s.platform.controller.action;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.k8s.EndpointSlice;
import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.service.authorization.AuthorizationService;
import com.k8s.platform.service.k8s.EndpointSliceService;
import com.k8s.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clusters/{clusterUid}/namespaces/{namespace}/endpointslices")
@RequiredArgsConstructor
public class EndpointSliceActionController {
    private final EndpointSliceService endpointSliceService;
    private final AuthorizationService authorizationService;
    private final SecurityUtils securityUtils;

    private boolean hasPermission(String clusterUid, String namespace, String resourceName, String action) {
        User user = securityUtils.getCurrentUser();
        if (user == null)
            return false;
        if (user.getIsSuperadmin() != null && user.getIsSuperadmin())
            return true;
        return authorizationService.hasPermission(user, clusterUid, namespace, "EndpointSlice", resourceName, action);
    }

    @GetMapping("/{name}/yaml")
    public ApiResponse<String> getYaml(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        if (!hasPermission(clusterUid, namespace, name, "view-yaml")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        EndpointSlice endpointSlice = endpointSliceService.getEndpointSlice(clusterUid, namespace, name);
        // Convert to YAML string (you may need to add a utility method)
        return ApiResponse.success("YAML retrieved successfully", endpointSlice.toString());
    }

    @GetMapping("/{name}/details")
    public ApiResponse<EndpointSlice> getDetails(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        if (!hasPermission(clusterUid, namespace, name, "view")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        EndpointSlice endpointSlice = endpointSliceService.getEndpointSlice(clusterUid, namespace, name);
        return ApiResponse.success("EndpointSlice details retrieved successfully", endpointSlice);
    }
}
