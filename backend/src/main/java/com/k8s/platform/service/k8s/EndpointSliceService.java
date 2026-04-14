package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.EndpointSlice;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.EndpointSliceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EndpointSliceService {

    private final EndpointSliceRepository endpointSliceRepository;
    private final ClusterRepository clusterRepository;

    public List<EndpointSlice> listEndpointSlices(String clusterUid) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return endpointSliceRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public List<EndpointSlice> listEndpointSlices(String clusterUid, String namespace) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return endpointSliceRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    public EndpointSlice getEndpointSlice(String clusterUid, String namespace, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return endpointSliceRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                cluster.getId(), namespace, name)
                .orElseThrow(() -> new RuntimeException("EndpointSlice not found: " + name));
    }
}
