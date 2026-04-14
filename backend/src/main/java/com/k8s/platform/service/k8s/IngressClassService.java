package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.IngressClass;
import com.k8s.platform.domain.repository.k8s.IngressClassRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressClassService {

    private final IngressClassRepository repository;
    private final K8sClientService k8sClientService;

    public List<IngressClass> listIngressClasses(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public IngressClass getIngressClass(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("IngressClass not found"));
    }

    @Transactional
    public void deleteIngressClass(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.network().v1().ingressClasses().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.networking.v1.IngressClass resource = client.network().v1().ingressClasses()
                .withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.networking.v1.IngressClass resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.networking.v1.IngressClass.class);
        client.network().v1().ingressClasses().resource(resource).update();
    }
}
