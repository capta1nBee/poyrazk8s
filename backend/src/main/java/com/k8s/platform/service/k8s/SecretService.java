package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretService {

    private final ClusterContextManager clusterContextManager;

    public List<Secret> listSecrets(String clusterUid) {
        return listSecrets(clusterUid, false);
    }

    public List<Secret> listSecrets(String clusterUid, boolean includeDeleted) {
        log.info("Listing all secrets in cluster UID: {} (includeDeleted: {})", clusterUid, includeDeleted);
        // Note: Kubernetes client doesn't support includeDeleted, deleted resources are removed from cluster
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.secrets().inAnyNamespace().list().getItems();
    }

    public List<Secret> listSecrets(String clusterUid, String namespace) {
        return listSecrets(clusterUid, namespace, false);
    }

    public List<Secret> listSecrets(String clusterUid, String namespace, boolean includeDeleted) {
        log.info("Listing secrets in namespace: {} in cluster UID: {} (includeDeleted: {})", namespace, clusterUid, includeDeleted);
        // Note: Kubernetes client doesn't support includeDeleted, deleted resources are removed from cluster
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.secrets().inNamespace(namespace).list().getItems();
    }

    public Secret getSecret(String clusterUid, String namespace, String name) {
        log.info("Getting secret: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.secrets().inNamespace(namespace).withName(name).get();
    }

    public Secret createSecret(String clusterUid, String namespace, Secret secret) {
        log.info("Creating secret: {}/{} in cluster UID: {}", namespace, secret.getMetadata().getName(), clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.secrets().inNamespace(namespace).resource(secret).create();
    }

    public Secret updateSecret(String clusterUid, String namespace, String name, Secret secret) {
        log.info("Updating secret: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.secrets().inNamespace(namespace).withName(name).replace(secret);
    }

    public void deleteSecret(String clusterUid, String namespace, String name) {
        log.info("Deleting secret: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.secrets().inNamespace(namespace).withName(name).delete();

        log.info("Secret deleted successfully: {}/{}", namespace, name);
    }

    public Map<String, String> revealSecret(String clusterUid, String namespace, String name) {
        log.info("Revealing secret: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        Secret secret = getSecret(clusterUid, namespace, name);

        if (secret != null && secret.getData() != null) {
            Map<String, String> revealed = new HashMap<>();

            secret.getData().forEach((key, value) -> {
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                revealed.put(key, decoded);
            });

            return revealed;
        }

        throw new RuntimeException("Secret not found: " + name);
    }

    public Secret rotateSecret(String clusterUid, String namespace, String name, Map<String, String> newData) {
        log.info("Rotating secret: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        Secret secret = getSecret(clusterUid, namespace, name);

        if (secret != null) {
            Map<String, String> encodedData = new HashMap<>();

            newData.forEach((key, value) -> {
                String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
                encodedData.put(key, encoded);
            });

            secret.setData(encodedData);

            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            return client.secrets().inNamespace(namespace).resource(secret).update();
        }

        throw new RuntimeException("Secret not found: " + name);
    }
}
