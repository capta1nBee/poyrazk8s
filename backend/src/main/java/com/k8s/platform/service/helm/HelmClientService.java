package com.k8s.platform.service.helm;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.k8s.platform.service.k8s.K8sClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HelmClientService {

    private final K8sClientService k8sClientService;

    public List<Map<String, Object>> listDeployments(String namespace, String clusterUid) {
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        return client.apps().deployments().inNamespace(namespace).list().getItems()
                .stream().map(d -> Map.of(
                        "name", d.getMetadata().getName(),
                        "namespace", d.getMetadata().getNamespace(),
                        "replicas", d.getSpec().getReplicas() != null ? d.getSpec().getReplicas() : 0,
                        "readyReplicas", d.getStatus().getReadyReplicas() != null ? d.getStatus().getReadyReplicas() : 0,
                        "labels", d.getMetadata().getLabels() != null ? d.getMetadata().getLabels() : Collections.emptyMap()
                )).collect(Collectors.toList());
    }

    public List<String> getNamespaces(String clusterUid) {
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        return client.namespaces().list().getItems().stream()
                .map(ns -> ns.getMetadata().getName())
                .collect(Collectors.toList());
    }
}
