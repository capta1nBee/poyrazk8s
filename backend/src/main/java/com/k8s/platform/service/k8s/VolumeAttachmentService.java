package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.VolumeAttachment;
import com.k8s.platform.domain.repository.k8s.VolumeAttachmentRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VolumeAttachmentService {

    private final VolumeAttachmentRepository repository;
    private final K8sClientService k8sClientService;

    public List<VolumeAttachment> listVolumeAttachments(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public VolumeAttachment getVolumeAttachment(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("VolumeAttachment not found"));
    }

    @Transactional
    public void deleteVolumeAttachment(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.storage().v1().volumeAttachments().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.storage.VolumeAttachment resource = client.storage().v1().volumeAttachments()
                .withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.storage.VolumeAttachment resource = k8sClientService.deserializeFromYaml(yaml,
                io.fabric8.kubernetes.api.model.storage.VolumeAttachment.class);
        client.storage().v1().volumeAttachments().resource(resource).update();
    }
}
