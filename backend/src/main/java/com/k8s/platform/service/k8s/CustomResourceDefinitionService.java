package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.CustomResourceDefinition;
import com.k8s.platform.domain.repository.k8s.CustomResourceDefinitionRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomResourceDefinitionService {

    private final CustomResourceDefinitionRepository repository;
    private final K8sClientService k8sClientService;

    public List<CustomResourceDefinition> listCustomResourceDefinitions(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public CustomResourceDefinition getCustomResourceDefinition(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("CustomResourceDefinition not found"));
    }

    @Transactional
    public void deleteCustomResourceDefinition(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.apiextensions().v1().customResourceDefinitions().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition resource = client.apiextensions().v1()
                .customResourceDefinitions().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition resource = k8sClientService
                .deserializeFromYaml(yaml,
                        io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition.class);
        client.apiextensions().v1().customResourceDefinitions().resource(resource).update();
    }
}
