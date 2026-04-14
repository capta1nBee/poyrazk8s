package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.CSINode;
import com.k8s.platform.domain.repository.k8s.CSINodeRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CSINodeService {

    private final CSINodeRepository repository;
    private final K8sClientService k8sClientService;

    public List<CSINode> listCSINodes(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public CSINode getCSINode(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("CSINode not found"));
    }

    @Transactional
    public void deleteCSINode(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.storage().csiNodes().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.storage.CSINode resource = client.storage().csiNodes().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.storage.CSINode resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.storage.CSINode.class);
        client.storage().csiNodes().resource(resource).update();
    }
}
