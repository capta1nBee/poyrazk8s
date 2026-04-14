package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.CSIDriver;
import com.k8s.platform.domain.repository.k8s.CSIDriverRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CSIDriverService {

    private final CSIDriverRepository repository;
    private final K8sClientService k8sClientService;

    public List<CSIDriver> listCSIDrivers(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public CSIDriver getCSIDriver(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("CSIDriver not found"));
    }

    @Transactional
    public void deleteCSIDriver(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.storage().csiDrivers().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.storage.CSIDriver resource = client.storage().csiDrivers().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.storage.CSIDriver resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.storage.CSIDriver.class);
        client.storage().csiDrivers().resource(resource).update();
    }
}
