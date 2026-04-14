package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.K8sEvent;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.K8sEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class K8sEventService {

    private final K8sEventRepository eventRepository;
    private final ClusterRepository clusterRepository;

    public List<K8sEvent> listEvents(String clusterUid) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return eventRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public List<K8sEvent> listEvents(String clusterUid, String namespace) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return eventRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    public List<K8sEvent> listEventsForResource(String clusterUid, String namespace, String kind, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return eventRepository.findByClusterIdAndNamespaceAndInvolvedObjectKindAndInvolvedObjectNameAndIsDeletedFalse(
                cluster.getId(), namespace, kind, name);
    }
}
