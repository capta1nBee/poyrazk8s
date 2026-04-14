package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.Application;
import com.k8s.platform.domain.repository.k8s.ApplicationRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository repository;
    private final K8sClientService k8sClientService;

    public List<Application> listApplications(Long clusterId, String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            return repository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, namespace);
        }
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public Application getApplication(Long clusterId, String namespace, String name) {
        return repository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .orElseThrow(() -> new RuntimeException("Application not found"));
    }

    @Transactional
    public void deleteApplication(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        // Note: Application is a CRD (Application CRD from SIG-apps), generic handling
        // might be needed if strictly typed client isn't available
        // For now assuming generic resource handling or user will add CRD support if
        // needed.
        // Using generic customResource approach
        client.genericKubernetesResources("app.k8s.io/v1beta1", "Application")
                .inNamespace(namespace)
                .withName(name)
                .delete();

        repository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.GenericKubernetesResource resource = client
                .genericKubernetesResources("app.k8s.io/v1beta1", "Application")
                .inNamespace(namespace)
                .withName(name)
                .get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.GenericKubernetesResource resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.GenericKubernetesResource.class);
        client.genericKubernetesResources("app.k8s.io/v1beta1", "Application")
                .inNamespace(namespace)
                .resource(resource)
                .update();
    }
}
