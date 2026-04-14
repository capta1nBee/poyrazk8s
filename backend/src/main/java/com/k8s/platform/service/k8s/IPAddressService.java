package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.IPAddress;
import com.k8s.platform.domain.repository.k8s.IPAddressRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IPAddressService {

    private final IPAddressRepository repository;
    private final K8sClientService k8sClientService;

    public List<IPAddress> listIPAddresses(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public IPAddress getIPAddress(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("IPAddress not found"));
    }

    @Transactional
    public void deleteIPAddress(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.network().v1alpha1().ipAddresses().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress resource = client.network().v1alpha1()
                .ipAddresses().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress resource = k8sClientService
                .deserializeFromYaml(yaml, io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress.class);
        client.network().v1alpha1().ipAddresses().resource(resource).update();
    }
}
