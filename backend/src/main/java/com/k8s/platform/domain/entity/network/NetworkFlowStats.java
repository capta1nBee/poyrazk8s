package com.k8s.platform.domain.entity.network;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_flow_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkFlowStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(nullable = false)
    private String granularity; // minute, hour, day

    @Column(name = "flow_type")
    private String flowType;

    @Column(name = "source_namespace")
    private String sourceNamespace;

    @Column(name = "destination_namespace")
    private String destinationNamespace;

    @Column
    private String protocol;

    @Column(name = "flow_count")
    private Long flowCount;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
