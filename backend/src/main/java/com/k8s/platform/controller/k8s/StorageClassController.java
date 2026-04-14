package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.k8s.K8sClientService;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class StorageClassController {

    private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;

    @GetMapping("/storageclasses")
    public List<Map<String, Object>> listStorageClasses(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "StorageClass", "*", "view");
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        return client.storage().v1().storageClasses().list().getItems()
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @GetMapping("/storageclasses/{name}")
    public Map<String, Object> getStorageClass(@PathVariable String clusterUid,
                                               @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "StorageClass", name, "view");
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        StorageClass sc = client.storage().v1().storageClasses().withName(name).get();
        if (sc == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "StorageClass not found: " + name);
        }
        return toMap(sc);
    }

    @GetMapping("/storageclasses/{name}/yaml")
    public String getStorageClassYaml(@PathVariable String clusterUid,
                                      @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "StorageClass", name, "view-yaml");
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        StorageClass sc = client.storage().v1().storageClasses().withName(name).get();
        if (sc == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "StorageClass not found: " + name);
        }
        return k8sClientService.serializeToYaml(sc);
    }

    @DeleteMapping("/storageclasses/{name}")
    public void deleteStorageClass(@PathVariable String clusterUid,
                                   @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "StorageClass", name, "delete");
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        client.storage().v1().storageClasses().withName(name).delete();
    }

    private Map<String, Object> toMap(StorageClass sc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", sc.getMetadata().getName());
        m.put("provisioner", sc.getProvisioner());
        m.put("reclaimPolicy", sc.getReclaimPolicy());
        m.put("volumeBindingMode", sc.getVolumeBindingMode());
        m.put("allowVolumeExpansion", Boolean.TRUE.equals(sc.getAllowVolumeExpansion()));
        m.put("parameters", sc.getParameters());
        m.put("labels", sc.getMetadata().getLabels());
        m.put("annotations", sc.getMetadata().getAnnotations());
        m.put("k8sCreatedAt", sc.getMetadata().getCreationTimestamp());
        return m;
    }
}
