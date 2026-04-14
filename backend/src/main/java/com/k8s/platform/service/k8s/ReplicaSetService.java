package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.ReplicaSet;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.ReplicaSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicaSetService {

    private final ReplicaSetRepository replicaSetRepository;

    private final ClusterRepository clusterRepository;

    @Transactional(readOnly = true)
    public List<ReplicaSet> listReplicaSets(String clusterUid) {
        return listReplicaSets(clusterUid, false);
    }

    @Transactional(readOnly = true)
    public List<ReplicaSet> listReplicaSets(String clusterUid, boolean includeDeleted) {
        // Find cluster by UID to get ID
        var cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return replicaSetRepository.findByClusterId(cluster.getId());
        }
        // Assuming findByClusterIdAndIsDeletedFalse exists in repository, consistent
        // with other services
        return replicaSetRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    @Transactional(readOnly = true)
    public List<ReplicaSet> listReplicaSets(String clusterUid, String namespace) {
        return listReplicaSets(clusterUid, namespace, false);
    }

    @Transactional(readOnly = true)
    public List<ReplicaSet> listReplicaSets(String clusterUid, String namespace, boolean includeDeleted) {
        var cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return replicaSetRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
        }
        return replicaSetRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    @Transactional(readOnly = true)
    public ReplicaSet getReplicaSet(String clusterUid, String namespace, String name) {
        var cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return replicaSetRepository
                .findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(cluster.getId(), namespace, name)
                .orElseThrow(() -> new RuntimeException("ReplicaSet not found: " + name));
    }
}
