package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.PriorityLevelConfiguration;
import com.k8s.platform.domain.repository.k8s.PriorityLevelConfigurationRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriorityLevelConfigurationService {

    private final PriorityLevelConfigurationRepository repository;
    private final K8sClientService k8sClientService;

    public List<PriorityLevelConfiguration> listPriorityLevelConfigurations(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public PriorityLevelConfiguration getPriorityLevelConfiguration(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("PriorityLevelConfiguration not found"));
    }

    @Transactional
    public void deletePriorityLevelConfiguration(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.flowControl().v1beta3().priorityLevelConfigurations().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration resource = client.flowControl()
                .v1beta3().priorityLevelConfigurations().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration resource = k8sClientService
                .deserializeFromYaml(yaml,
                        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration.class);
        client.flowControl().v1beta3().priorityLevelConfigurations().resource(resource).update();
    }
}
