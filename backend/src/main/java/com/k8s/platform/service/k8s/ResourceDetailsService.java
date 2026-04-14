package com.k8s.platform.service.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.dto.response.ResourceDetailsDTO;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceDetailsService {

    private final ClusterContextManager clusterContextManager;
    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get detailed information about any Kubernetes resource
     */
    public ResourceDetailsDTO getResourceDetails(String clusterUid, String namespace, 
                                                  String resourceKind, String name) {
        log.info("Getting details for {}/{} in namespace {} of cluster {}", 
                resourceKind, name, namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        HasMetadata resource = fetchResource(client, namespace, resourceKind, name);
        
        if (resource == null) {
            throw new RuntimeException(resourceKind + " not found: " + name);
        }

        return buildResourceDetails(resource, cluster);
    }

    /**
     * Get detailed information about cluster-scoped resource
     */
    public ResourceDetailsDTO getClusterResourceDetails(String clusterUid, 
                                                         String resourceKind, String name) {
        log.info("Getting details for cluster-scoped {}/{} in cluster {}", 
                resourceKind, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        HasMetadata resource = fetchClusterResource(client, resourceKind, name);
        
        if (resource == null) {
            throw new RuntimeException(resourceKind + " not found: " + name);
        }

        return buildResourceDetails(resource, cluster);
    }

    private HasMetadata fetchResource(KubernetesClient client, String namespace, 
                                     String kind, String name) {
        return switch (kind.toLowerCase()) {
            // Workloads
            case "pod" -> client.pods().inNamespace(namespace).withName(name).get();
            case "deployment" -> client.apps().deployments().inNamespace(namespace).withName(name).get();
            case "statefulset" -> client.apps().statefulSets().inNamespace(namespace).withName(name).get();
            case "daemonset" -> client.apps().daemonSets().inNamespace(namespace).withName(name).get();
            case "replicaset" -> client.apps().replicaSets().inNamespace(namespace).withName(name).get();
            case "replicationcontroller" -> client.replicationControllers().inNamespace(namespace).withName(name).get();
            case "job" -> client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
            case "cronjob" -> client.batch().v1().cronjobs().inNamespace(namespace).withName(name).get();
            // Network
            case "service" -> client.services().inNamespace(namespace).withName(name).get();
            case "ingress" -> client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
            case "networkpolicy" -> client.network().v1().networkPolicies().inNamespace(namespace).withName(name).get();
            // Config
            case "configmap" -> client.configMaps().inNamespace(namespace).withName(name).get();
            case "secret" -> client.secrets().inNamespace(namespace).withName(name).get();
            // Storage
            case "persistentvolumeclaim", "pvc" -> client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
            // RBAC (namespaced)
            case "role", "k8srole" -> client.rbac().roles().inNamespace(namespace).withName(name).get();
            case "rolebinding" -> client.rbac().roleBindings().inNamespace(namespace).withName(name).get();
            case "serviceaccount" -> client.serviceAccounts().inNamespace(namespace).withName(name).get();
            // Discovery
            case "endpointslice" -> client.discovery().v1().endpointSlices().inNamespace(namespace).withName(name).get();
            // Coordination
            case "lease" -> client.leases().inNamespace(namespace).withName(name).get();
            // Events
            case "event" -> client.v1().events().inNamespace(namespace).withName(name).get();
            // Autoscaling
            case "horizontalpodautoscaler" -> client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).withName(name).get();
            default -> throw new IllegalArgumentException("Unsupported namespaced resource kind: " + kind);
        };
    }

    private HasMetadata fetchClusterResource(KubernetesClient client, String kind, String name) {
        return switch (kind.toLowerCase()) {
            // Core
            case "node" -> client.nodes().withName(name).get();
            case "namespace" -> client.namespaces().withName(name).get();
            // Storage
            case "persistentvolume", "pv" -> client.persistentVolumes().withName(name).get();
            case "storageclass" -> client.storage().v1().storageClasses().withName(name).get();
            case "csidriver" -> client.storage().v1().csiDrivers().withName(name).get();
            case "csinode" -> client.storage().v1().csiNodes().withName(name).get();
            case "volumeattachment" -> client.storage().v1().volumeAttachments().withName(name).get();
            // RBAC (cluster-scoped)
            case "clusterrole" -> client.rbac().clusterRoles().withName(name).get();
            case "clusterrolebinding" -> client.rbac().clusterRoleBindings().withName(name).get();
            // Admission Control
            case "mutatingwebhookconfiguration" -> client.admissionRegistration().v1().mutatingWebhookConfigurations().withName(name).get();
            case "validatingwebhookconfiguration" -> client.admissionRegistration().v1().validatingWebhookConfigurations().withName(name).get();
            // API Extensions
            case "customresourcedefinition", "crd" -> client.apiextensions().v1().customResourceDefinitions().withName(name).get();
            // Network (cluster-scoped)
            case "ingressclass" -> client.network().v1().ingressClasses().withName(name).get();
            // Certificates
            case "certificatesigningrequest", "csr" -> client.certificates().v1().certificateSigningRequests().withName(name).get();
            // Scheduling
            case "priorityclass" -> client.scheduling().v1().priorityClasses().withName(name).get();
            default -> throw new IllegalArgumentException("Unsupported cluster-scoped resource kind: " + kind);
        };
    }

    @SuppressWarnings("unchecked")
    private ResourceDetailsDTO buildResourceDetails(HasMetadata resource, Cluster cluster) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        
        // Extract spec and status from the resource
        Map<String, Object> resourceMap = objectMapper.convertValue(resource, Map.class);
        Map<String, Object> spec = (Map<String, Object>) resourceMap.get("spec");
        Map<String, Object> status = (Map<String, Object>) resourceMap.get("status");

        // Store managed fields before clearing for YAML generation
        var managedFields = resource.getMetadata().getManagedFields();
        
        // Clear managed fields for cleaner YAML output (Hide Managed Fields)
        resource.getMetadata().setManagedFields(null);
        String yaml = Serialization.asYaml(resource);

        return ResourceDetailsDTO.builder()
                .kind(resource.getKind())
                .apiVersion(resource.getApiVersion())
                .name(resource.getMetadata().getName())
                .namespace(resource.getMetadata().getNamespace())
                .uid(resource.getMetadata().getUid())
                .resourceVersion(resource.getMetadata().getResourceVersion())
                .generation(resource.getMetadata().getGeneration())
                .createdAt(resource.getMetadata().getCreationTimestamp() != null 
                        ? resource.getMetadata().getCreationTimestamp() : null)
                .deletedAt(resource.getMetadata().getDeletionTimestamp() != null 
                        ? resource.getMetadata().getDeletionTimestamp() : null)
                .labels(resource.getMetadata().getLabels())
                .annotations(resource.getMetadata().getAnnotations())
                .ownerReferences(resource.getMetadata().getOwnerReferences())
                .finalizers(resource.getMetadata().getFinalizers())
                .managedFields(managedFields)
                .spec(spec != null ? spec : new HashMap<>())
                .status(status != null ? status : new HashMap<>())
                .yaml(yaml)
                .clusterId(cluster.getId())
                .clusterUid(cluster.getUid())
                .clusterName(cluster.getName())
                .build();
    }
}

