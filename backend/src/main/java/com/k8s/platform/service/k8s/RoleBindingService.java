package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.RoleBinding;
import com.k8s.platform.domain.repository.k8s.RoleBindingRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleBindingService {

    private final RoleBindingRepository roleBindingRepository;
    private final K8sClientService k8sClientService;

    public List<RoleBinding> listRoleBindings(Long clusterId, String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            return roleBindingRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, namespace);
        }
        return roleBindingRepository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public RoleBinding getRoleBinding(Long clusterId, String namespace, String name) {
        return roleBindingRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .orElseThrow(() -> new RuntimeException("RoleBinding not found"));
    }

    @Transactional
    public void deleteRoleBinding(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.rbac().roleBindings().inNamespace(namespace).withName(name).delete();

        roleBindingRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .ifPresent(rb -> {
                    rb.setIsDeleted(true);
                    roleBindingRepository.save(rb);
                });
    }

    public String getYaml(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.RoleBinding resource = client.rbac().roleBindings().inNamespace(namespace)
                .withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.RoleBinding resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.rbac.RoleBinding.class);
        client.rbac().roleBindings().inNamespace(namespace).resource(resource).update();
    }
}
