package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.PersistentVolumeClaimRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PVCService {

    private final ClusterContextManager clusterContextManager;
    private final PersistentVolumeClaimRepository pvcRepository;
    private final ClusterRepository clusterRepository;

    public List<PersistentVolumeClaim> listPVCs(String clusterUid) {
        log.info("Listing all PVCs in cluster UID: {}", clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.persistentVolumeClaims().inAnyNamespace().list().getItems();
    }

    public List<PersistentVolumeClaim> listPVCs(String clusterUid, String namespace) {
        log.info("Listing PVCs in namespace: {} in cluster UID: {}", namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.persistentVolumeClaims().inNamespace(namespace).list().getItems();
    }

    public PersistentVolumeClaim getPVC(String clusterUid, String namespace, String name) {
        log.info("Getting PVC: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
    }

    public PersistentVolumeClaim createPVC(String clusterUid, String namespace, PersistentVolumeClaim pvc) {
        log.info("Creating PVC: {}/{} in cluster UID: {}", namespace, pvc.getMetadata().getName(), clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
    }

    public void deletePVC(String clusterUid, String namespace, String name) {
        log.info("Deleting PVC: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.persistentVolumeClaims().inNamespace(namespace).withName(name).delete();

        log.info("PVC deleted successfully: {}/{}", namespace, name);
    }

    public PersistentVolumeClaim resizePVC(String clusterUid, String namespace, String name, String newSize) {
        log.info("Resizing PVC: {}/{} to {} in cluster UID: {}", namespace, name, newSize, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();

        if (pvc != null && pvc.getSpec() != null && pvc.getSpec().getResources() != null) {
            pvc.getSpec().getResources().getRequests().put("storage", new Quantity(newSize));
            return client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).update();
        }

        throw new RuntimeException("PVC not found or cannot be resized: " + name);
    }

    public Map<String, Object> getUsage(String clusterUid, String namespace, String name) {
        log.info("Getting usage for PVC: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        PersistentVolumeClaim pvc = getPVC(clusterUid, namespace, name);

        if (pvc != null && pvc.getStatus() != null) {
            return Map.of(
                    "name", name,
                    "namespace", namespace,
                    "capacity", pvc.getStatus().getCapacity() != null ? pvc.getStatus().getCapacity() : Map.of(),
                    "phase", pvc.getStatus().getPhase() != null ? pvc.getStatus().getPhase() : "Unknown",
                    "volumeName", pvc.getSpec().getVolumeName() != null ? pvc.getSpec().getVolumeName() : "");
        }

        throw new RuntimeException("PVC not found: " + name);
    }

    // DB-based methods for DTO responses
    public List<com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim> listPVCsFromDB(String clusterUid) {
        return listPVCsFromDB(clusterUid, false);
    }

    public List<com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim> listPVCsFromDB(String clusterUid, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
        if (includeDeleted) {
            return pvcRepository.findByClusterId(cluster.getId());
        }
        return pvcRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public List<com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim> listPVCsFromDB(String clusterUid, String namespace) {
        return listPVCsFromDB(clusterUid, namespace, false);
    }

    public List<com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim> listPVCsFromDB(String clusterUid, String namespace, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
        if (includeDeleted) {
            return pvcRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
        }
        return pvcRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    public com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim getPVCFromDB(String clusterUid, String namespace, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
        return pvcRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(cluster.getId(), namespace, name)
                .orElseThrow(() -> new RuntimeException("PVC not found: " + namespace + "/" + name));
    }
}
