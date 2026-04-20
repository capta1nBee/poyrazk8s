package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.K8sEvent;
import com.k8s.platform.domain.repository.k8s.K8sEventRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final K8sEventRepository eventRepository;
    private final ClusterContextManager clusterContextManager;
    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get events for a specific resource
     */
    public List<K8sEvent> getEventsForResource(String clusterUid, String namespace,
            String resourceKind, String resourceName) {
        log.info("Getting events for {}/{} in namespace {} of cluster {}",
                resourceKind, resourceName, namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        // For cluster-scoped resources (namespace == null), search across all namespaces
        List<Event> allEvents = (namespace == null || namespace.isBlank())
                ? client.v1().events().inAnyNamespace().list().getItems()
                : client.v1().events().inNamespace(namespace).list().getItems();

        List<Event> events = allEvents
                .stream()
                .filter(event -> {
                    if (event.getInvolvedObject() == null || event.getInvolvedObject().getKind() == null) {
                        return false;
                    }
                    String involvedKind = event.getInvolvedObject().getKind();
                    boolean kindMatches = resourceKind.equalsIgnoreCase(involvedKind) ||
                            resourceKind.equalsIgnoreCase(involvedKind + "s") ||
                            resourceKind.equalsIgnoreCase(involvedKind + "es");

                    return kindMatches && resourceName.equals(event.getInvolvedObject().getName());
                })
                .collect(Collectors.toList());

        Cluster cluster = clusterRepository.findByUid(clusterUid).orElse(null);
        Long clusterId = cluster != null ? cluster.getId() : null;

        // Convert to entity
        return events.stream()
                .map(e -> convertToEntity(e, clusterId))
                .collect(Collectors.toList());
    }

    /**
     * Get all events in a namespace
     */
    public List<K8sEvent> getEvents(String clusterUid, String namespace) {
        log.info("Getting all events in namespace {} of cluster {}", namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        List<Event> events = client.v1().events()
                .inNamespace(namespace)
                .list()
                .getItems();

        Cluster cluster = clusterRepository.findByUid(clusterUid).orElse(null);
        Long clusterId = cluster != null ? cluster.getId() : null;

        return events.stream()
                .map(e -> convertToEntity(e, clusterId))
                .collect(Collectors.toList());
    }

    /**
     * Get all events across all namespaces
     */
    public List<K8sEvent> getAllEvents(String clusterUid) {
        log.info("Getting all events in cluster {}", clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        List<Event> events = client.v1().events()
                .inAnyNamespace()
                .list()
                .getItems();

        Cluster cluster = clusterRepository.findByUid(clusterUid).orElse(null);
        Long clusterId = cluster != null ? cluster.getId() : null;

        return events.stream()
                .map(e -> convertToEntity(e, clusterId))
                .collect(Collectors.toList());
    }

    private K8sEvent convertToEntity(Event event, Long clusterId) {
        K8sEvent entity = new K8sEvent();
        entity.setClusterId(clusterId);

        entity.setKind("Event");
        entity.setApiVersion(event.getApiVersion());
        entity.setNamespace(event.getMetadata().getNamespace());
        entity.setName(event.getMetadata().getName());
        entity.setUid(event.getMetadata().getUid());
        entity.setResourceVersion(event.getMetadata().getResourceVersion());

        if (event.getInvolvedObject() != null) {
            entity.setInvolvedObjectKind(event.getInvolvedObject().getKind());
            entity.setInvolvedObjectName(event.getInvolvedObject().getName());
            try {
                entity.setInvolvedObject(objectMapper.writeValueAsString(event.getInvolvedObject()));
            } catch (Exception e) {
                log.warn("Failed to serialize involved object for event: {}", event.getMetadata().getUid());
            }
        }

        entity.setType(event.getType());
        entity.setReason(event.getReason());
        entity.setMessage(event.getMessage());
        entity.setCount(event.getCount());

        entity.setFirstTimestamp(parseTimestamp(event.getFirstTimestamp()));
        entity.setLastTimestamp(parseTimestamp(event.getLastTimestamp()));
        entity.setK8sCreatedAt(event.getMetadata().getCreationTimestamp());

        if (event.getLastTimestamp() != null) {
            entity.setLastSeen(event.getLastTimestamp());
        }

        entity.setSource(event.getSource() != null ? event.getSource().getComponent() : null);

        return entity;
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Kubernetes timestamp: {}", timestamp);
            return null;
        }
    }
}
