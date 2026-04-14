package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.ClusterRole;
import com.k8s.platform.domain.repository.k8s.ClusterRoleRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterRoleService {

    private final ClusterRoleRepository clusterRoleRepository;
    private final K8sClientService k8sClientService;

    public List<ClusterRole> listClusterRoles(Long clusterId) {
        return clusterRoleRepository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public ClusterRole getClusterRole(Long clusterId, String name) {
        return clusterRoleRepository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("ClusterRole not found"));
    }

    @Transactional
    public void deleteClusterRole(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.rbac().clusterRoles().withName(name).delete();

        // Local DB update will be handled by sync service, but we can soft delete
        // immediately for UI responsiveness
        clusterRoleRepository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(cr -> {
                    cr.setIsDeleted(true);
                    clusterRoleRepository.save(cr);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.ClusterRole resource = client.rbac().clusterRoles().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.ClusterRole resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.rbac.ClusterRole.class);
        client.rbac().clusterRoles().resource(resource).update();
    }
}
