package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.PriorityClass;
import com.k8s.platform.domain.repository.k8s.PriorityClassRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriorityClassService {

    private final PriorityClassRepository repository;
    private final K8sClientService k8sClientService;

    public List<PriorityClass> listPriorityClasses(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public PriorityClass getPriorityClass(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("PriorityClass not found"));
    }

    @Transactional
    public void deletePriorityClass(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.scheduling().v1().priorityClasses().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass resource = client.scheduling().v1()
                .priorityClasses().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass resource = k8sClientService
                .deserializeFromYaml(yaml, io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass.class);
        client.scheduling().v1().priorityClasses().resource(resource).update();
    }
}
