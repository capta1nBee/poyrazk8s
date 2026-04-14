package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.ClusterRoleBinding;
import com.k8s.platform.domain.repository.k8s.ClusterRoleBindingRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterRoleBindingService {

    private final ClusterRoleBindingRepository clusterRoleBindingRepository;
    private final K8sClientService k8sClientService;

    public List<ClusterRoleBinding> listClusterRoleBindings(Long clusterId) {
        return clusterRoleBindingRepository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public ClusterRoleBinding getClusterRoleBinding(Long clusterId, String name) {
        return clusterRoleBindingRepository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("ClusterRoleBinding not found"));
    }

    @Transactional
    public void deleteClusterRoleBinding(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.rbac().clusterRoleBindings().withName(name).delete();

        clusterRoleBindingRepository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(crb -> {
                    crb.setIsDeleted(true);
                    clusterRoleBindingRepository.save(crb);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding resource = client.rbac().clusterRoleBindings()
                .withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding.class);
        client.rbac().clusterRoleBindings().resource(resource).update();
    }
}
