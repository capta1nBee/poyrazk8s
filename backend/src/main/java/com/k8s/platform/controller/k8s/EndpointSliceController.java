package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;

import com.k8s.platform.domain.entity.k8s.EndpointSlice;
import com.k8s.platform.service.k8s.EndpointSliceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class EndpointSliceController {

    private final EndpointSliceService endpointSliceService;
        private final ResourceAuthorizationHelper authHelper;
        
    @GetMapping("/endpointslices")
    public ResponseEntity<List<EndpointSlice>> listAllEndpointSlices(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "Resource", "*", "view");
        List<EndpointSlice> all = endpointSliceService.listEndpointSlices(clusterUid);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "EndpointSlice", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/endpointslices")
    public ResponseEntity<List<EndpointSlice>> listEndpointSlices(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "EndpointSlice", "*", "view");
        List<EndpointSlice> all = endpointSliceService.listEndpointSlices(clusterUid, namespace);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "EndpointSlice", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/endpointslices/{name}")
    public ResponseEntity<EndpointSlice> getEndpointSlice(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "EndpointSlice", name, "view");
        return ResponseEntity.ok(endpointSliceService.getEndpointSlice(clusterUid, namespace, name));
    }
}
