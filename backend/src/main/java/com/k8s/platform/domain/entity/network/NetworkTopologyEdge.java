package com.k8s.platform.domain.entity.network;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_topology_edges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkTopologyEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    // Source node
    @Column(name = "source_type", nullable = false)
    private String sourceType; // pod, service, external, node

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "source_namespace")
    private String sourceNamespace;

    // Target node
    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_name", nullable = false)
    private String targetName;

    @Column(name = "target_namespace")
    private String targetNamespace;

    // Edge properties
    @Column
    private String protocol;

    @Column
    private Integer port;

    @Column(name = "flow_count")
    private Long flowCount;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    // Labels and metadata for policy generation
    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_namespace")
    private String serviceNamespace;

    @Column(name = "backend_pod_name")
    private String backendPodName;

    @Column(name = "backend_pod_namespace")
    private String backendPodNamespace;

    @Column(name = "flow_type")
    private String flowType; // pod-to-pod, pod-to-service, pod-to-external, etc.

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
