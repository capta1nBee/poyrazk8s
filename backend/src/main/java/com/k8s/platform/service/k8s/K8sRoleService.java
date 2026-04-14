package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.K8sRole;
import com.k8s.platform.domain.repository.k8s.K8sRoleRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class K8sRoleService {

    private final K8sRoleRepository k8sRoleRepository;
    private final K8sClientService k8sClientService;

    public List<K8sRole> listRoles(Long clusterId, String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            return k8sRoleRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, namespace);
        }
        return k8sRoleRepository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public K8sRole getRole(Long clusterId, String namespace, String name) {
        return k8sRoleRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .orElseThrow(() -> new RuntimeException("Role not found"));
    }

    @Transactional
    public void deleteRole(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.rbac().roles().inNamespace(namespace).withName(name).delete();

        k8sRoleRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    k8sRoleRepository.save(r);
                });
    }

    public String getYaml(Long clusterId, String namespace, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.Role resource = client.rbac().roles().inNamespace(namespace).withName(name)
                .get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.Role resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.rbac.Role.class);
        client.rbac().roles().inNamespace(namespace).resource(resource).update();
    }
}
