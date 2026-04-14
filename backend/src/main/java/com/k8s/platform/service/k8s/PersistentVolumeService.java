package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.PersistentVolume;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.PersistentVolumeRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistentVolumeService {

    private final PersistentVolumeRepository persistentVolumeRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public List<PersistentVolume> listPersistentVolumes(String clusterUid) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return persistentVolumeRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public PersistentVolume getPersistentVolume(String clusterUid, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return persistentVolumeRepository.findByClusterIdAndNameAndIsDeletedFalse(cluster.getId(), name)
                .orElseThrow(() -> new RuntimeException("PersistentVolume not found: " + name));
    }

    public List<PersistentVolume> listPersistentVolumesByPhase(String clusterUid, String phase) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return persistentVolumeRepository.findByClusterIdAndPhaseAndIsDeletedFalse(cluster.getId(), phase);
    }

    public List<PersistentVolume> listPersistentVolumesByStorageClass(String clusterUid, String storageClassName) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return persistentVolumeRepository.findByClusterIdAndStorageClassNameAndIsDeletedFalse(cluster.getId(), storageClassName);
    }

    public void deletePersistentVolume(String clusterUid, String name) {
        log.info("Deleting PersistentVolume: {} in cluster UID: {}", name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        client.persistentVolumes()
                .withName(name)
                .delete();

        log.info("PersistentVolume deleted successfully: {}", name);
    }

    public io.fabric8.kubernetes.api.model.PersistentVolume getPersistentVolumeFromK8s(String clusterUid, String name) {
        log.info("Getting PersistentVolume from K8s: {} in cluster UID: {}", name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.persistentVolumes().withName(name).get();
    }

    public List<io.fabric8.kubernetes.api.model.PersistentVolume> listPersistentVolumesFromK8s(String clusterUid) {
        log.info("Listing PersistentVolumes from K8s in cluster UID: {}", clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.persistentVolumes().list().getItems();
    }
}

