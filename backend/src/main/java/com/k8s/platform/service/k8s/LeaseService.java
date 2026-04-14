package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.Lease;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.LeaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final ClusterRepository clusterRepository;

    public List<Lease> listLeases(String clusterUid) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return leaseRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public List<Lease> listLeases(String clusterUid, String namespace) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return leaseRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    public Lease getLease(String clusterUid, String namespace, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return leaseRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(cluster.getId(), namespace, name)
                .orElseThrow(() -> new RuntimeException("Lease not found: " + namespace + "/" + name));
    }
}
