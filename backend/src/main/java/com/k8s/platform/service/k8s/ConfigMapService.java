package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigMapService {

    private final ClusterContextManager clusterContextManager;

    public List<ConfigMap> listConfigMaps(String clusterUid) {
        return listConfigMaps(clusterUid, false);
    }

    public List<ConfigMap> listConfigMaps(String clusterUid, boolean includeDeleted) {
        log.info("Listing all configmaps in cluster UID: {} (includeDeleted: {})", clusterUid, includeDeleted);
        // Note: Kubernetes client doesn't support includeDeleted, deleted resources are removed from cluster
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.configMaps().inAnyNamespace().list().getItems();
    }

    public List<ConfigMap> listConfigMaps(String clusterUid, String namespace) {
        return listConfigMaps(clusterUid, namespace, false);
    }

    public List<ConfigMap> listConfigMaps(String clusterUid, String namespace, boolean includeDeleted) {
        log.info("Listing configmaps in namespace: {} in cluster UID: {} (includeDeleted: {})", namespace, clusterUid, includeDeleted);
        // Note: Kubernetes client doesn't support includeDeleted, deleted resources are removed from cluster
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.configMaps().inNamespace(namespace).list().getItems();
    }

    public ConfigMap getConfigMap(String clusterUid, String namespace, String name) {
        log.info("Getting configmap: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.configMaps().inNamespace(namespace).withName(name).get();
    }

    public ConfigMap createConfigMap(String clusterUid, String namespace, ConfigMap configMap) {
        log.info("Creating configmap: {}/{} in cluster UID: {}", namespace, configMap.getMetadata().getName(),
                clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.configMaps().inNamespace(namespace).resource(configMap).create();
    }

    public ConfigMap updateConfigMap(String clusterUid, String namespace, String name, ConfigMap configMap) {
        log.info("Updating configmap: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.configMaps().inNamespace(namespace).withName(name).replace(configMap);
    }

    public void deleteConfigMap(String clusterUid, String namespace, String name) {
        log.info("Deleting configmap: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.configMaps().inNamespace(namespace).withName(name).delete();

        log.info("ConfigMap deleted successfully: {}/{}", namespace, name);
    }

    public ConfigMap updateData(String clusterUid, String namespace, String name, Map<String, String> newData) {
        log.info("Updating data for configmap: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        ConfigMap configMap = getConfigMap(clusterUid, namespace, name);

        if (configMap != null) {
            configMap.setData(newData);

            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            return client.configMaps().inNamespace(namespace).resource(configMap).update();
        }

        throw new RuntimeException("ConfigMap not found: " + name);
    }

    public Map<String, Object> getMountPreview(String clusterUid, String namespace, String name) {
        log.info("Getting mount preview for configmap: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        ConfigMap configMap = getConfigMap(clusterUid, namespace, name);

        if (configMap != null) {
            return Map.of(
                    "name", name,
                    "namespace", namespace,
                    "data", configMap.getData() != null ? configMap.getData() : Map.of(),
                    "binaryData", configMap.getBinaryData() != null ? configMap.getBinaryData() : Map.of());
        }

        throw new RuntimeException("ConfigMap not found: " + name);
    }
}
