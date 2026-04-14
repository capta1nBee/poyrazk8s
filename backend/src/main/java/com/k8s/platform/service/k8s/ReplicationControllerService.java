package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.ReplicationController;
import com.k8s.platform.domain.repository.k8s.ReplicationControllerRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicationControllerService {

    private final ReplicationControllerRepository repository;
    private final K8sClientService k8sClientService;

    public List<ReplicationController> listReplicationControllers(Long clusterId, String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            return repository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, namespace);
        }
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public ReplicationController getReplicationController(Long clusterId, String namespace, String name) {
        return repository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .orElseThrow(() -> new RuntimeException("ReplicationController not found"));
    }

    @Transactional
    public void deleteReplicationController(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.replicationControllers().inNamespace(namespace).withName(name).delete();

        repository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.ReplicationController resource = client.replicationControllers()
                .inNamespace(namespace).withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.ReplicationController resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.ReplicationController.class);
        client.replicationControllers().inNamespace(namespace).resource(resource).update();
    }
}
