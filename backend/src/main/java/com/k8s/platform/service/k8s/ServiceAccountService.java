package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.ServiceAccount;
import com.k8s.platform.domain.repository.k8s.ServiceAccountRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceAccountService {

    private final ServiceAccountRepository serviceAccountRepository;
    private final K8sClientService k8sClientService;

    public List<ServiceAccount> listServiceAccounts(Long clusterId, String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            return serviceAccountRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, namespace);
        }
        return serviceAccountRepository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public ServiceAccount getServiceAccount(Long clusterId, String namespace, String name) {
        return serviceAccountRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .orElseThrow(() -> new RuntimeException("ServiceAccount not found"));
    }

    @Transactional
    public void deleteServiceAccount(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.serviceAccounts().inNamespace(namespace).withName(name).delete();

        serviceAccountRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .ifPresent(sa -> {
                    sa.setIsDeleted(true);
                    serviceAccountRepository.save(sa);
                });
    }

    public String getYaml(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.ServiceAccount resource = client.serviceAccounts().inNamespace(namespace)
                .withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.ServiceAccount resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.ServiceAccount.class);
        client.serviceAccounts().inNamespace(namespace).resource(resource).update();
    }
}
