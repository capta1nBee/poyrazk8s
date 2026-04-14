package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceManagementService {

    private final ClusterContextManager clusterContextManager;

    /**
     * Apply YAML to cluster
     */
    public Map<String, Object> applyYaml(String clusterUid, String namespace, String yaml, Boolean dryRun) {
        log.info("Applying YAML to namespace {} in cluster {} (dryRun: {})", namespace, clusterUid, dryRun);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        try {
            HasMetadata resource = Serialization.unmarshal(new ByteArrayInputStream(yaml.getBytes()));
            
            if (dryRun != null && dryRun) {
                // Dry run - validate only
                return Map.of(
                        "success", true,
                        "message", "YAML is valid",
                        "dryRun", true,
                        "resource", Map.of(
                                "kind", resource.getKind(),
                                "name", resource.getMetadata().getName(),
                                "namespace", resource.getMetadata().getNamespace()
                        )
                );
            }

            // Apply the resource
            HasMetadata applied;
            if (namespace != null) {
                applied = client.resource(resource).inNamespace(namespace).createOrReplace();
            } else {
                // For cluster-scoped resources (Node, PersistentVolume, Namespace, etc.)
                applied = client.resource(resource).createOrReplace();
            }

            return Map.of(
                    "success", true,
                    "message", "Resource applied successfully",
                    "resource", Map.of(
                            "kind", applied.getKind(),
                            "name", applied.getMetadata().getName(),
                            "namespace", applied.getMetadata().getNamespace(),
                            "uid", applied.getMetadata().getUid()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to apply YAML", e);
            return Map.of(
                    "success", false,
                    "message", "Failed to apply YAML: " + e.getMessage()
            );
        }
    }

    /**
     * Delete resource
     */
    public void deleteResource(String clusterUid, String namespace, String kind, String name, Boolean force) {
        log.info("Deleting {}/{} in namespace {} of cluster {} (force: {})", 
                kind, name, namespace, clusterUid, force);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        
        Resource<? extends HasMetadata> resource = getResourceHandle(client, namespace, kind, name);
        
        if (force != null && force) {
            resource.withGracePeriod(0).delete();
        } else {
            resource.delete();
        }
    }

    /**
     * Delete cluster-scoped resource
     */
    public void deleteClusterResource(String clusterUid, String kind, String name, Boolean force) {
        log.info("Deleting cluster-scoped {}/{} in cluster {} (force: {})", 
                kind, name, clusterUid, force);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        
        Resource<? extends HasMetadata> resource = getClusterResourceHandle(client, kind, name);
        
        if (force != null && force) {
            resource.withGracePeriod(0).delete();
        } else {
            resource.delete();
        }
    }

    /**
     * Update labels
     */
    public void updateLabels(String clusterUid, String namespace, String kind, String name, 
                            Map<String, String> labels) {
        log.info("Updating labels for {}/{} in namespace {} of cluster {}", 
                kind, name, namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        Resource<? extends HasMetadata> resource = getResourceHandle(client, namespace, kind, name);
        
        resource.edit(r -> {
            if (r.getMetadata().getLabels() == null) {
                r.getMetadata().setLabels(new HashMap<>());
            }
            r.getMetadata().getLabels().putAll(labels);
            return r;
        });
    }

    /**
     * Update annotations
     */
    public void updateAnnotations(String clusterUid, String namespace, String kind, String name, 
                                  Map<String, String> annotations) {
        log.info("Updating annotations for {}/{} in namespace {} of cluster {}", 
                kind, name, namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        Resource<? extends HasMetadata> resource = getResourceHandle(client, namespace, kind, name);
        
        resource.edit(r -> {
            if (r.getMetadata().getAnnotations() == null) {
                r.getMetadata().setAnnotations(new HashMap<>());
            }
            r.getMetadata().getAnnotations().putAll(annotations);
            return r;
        });
    }

    private Resource<? extends HasMetadata> getResourceHandle(KubernetesClient client, 
                                                               String namespace, String kind, String name) {
        return switch (kind.toLowerCase()) {
            case "pod" -> client.pods().inNamespace(namespace).withName(name);
            case "deployment" -> client.apps().deployments().inNamespace(namespace).withName(name);
            case "statefulset" -> client.apps().statefulSets().inNamespace(namespace).withName(name);
            case "daemonset" -> client.apps().daemonSets().inNamespace(namespace).withName(name);
            case "replicaset" -> client.apps().replicaSets().inNamespace(namespace).withName(name);
            case "job" -> client.batch().v1().jobs().inNamespace(namespace).withName(name);
            case "cronjob" -> client.batch().v1().cronjobs().inNamespace(namespace).withName(name);
            case "service" -> client.services().inNamespace(namespace).withName(name);
            case "ingress" -> client.network().v1().ingresses().inNamespace(namespace).withName(name);
            case "configmap" -> client.configMaps().inNamespace(namespace).withName(name);
            case "secret" -> client.secrets().inNamespace(namespace).withName(name);
            case "persistentvolumeclaim", "pvc" -> client.persistentVolumeClaims().inNamespace(namespace).withName(name);
            default -> throw new IllegalArgumentException("Unsupported resource kind: " + kind);
        };
    }

    private Resource<? extends HasMetadata> getClusterResourceHandle(KubernetesClient client, 
                                                                      String kind, String name) {
        return switch (kind.toLowerCase()) {
            case "node" -> client.nodes().withName(name);
            case "namespace" -> client.namespaces().withName(name);
            case "persistentvolume", "pv" -> client.persistentVolumes().withName(name);
            case "storageclass" -> client.storage().v1().storageClasses().withName(name);
            default -> throw new IllegalArgumentException("Unsupported cluster resource kind: " + kind);
        };
    }
}

