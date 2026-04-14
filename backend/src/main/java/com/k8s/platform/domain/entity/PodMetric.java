package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Time-series pod CPU/memory metric.
 * One row per (cluster, namespace, pod, timestamp).
 */
@Entity
@Table(name = "pod_metrics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PodMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(name = "namespace", nullable = false)
    private String namespace;

    @Column(name = "pod_name", nullable = false)
    private String podName;

    /** CPU usage in millicores (e.g., 250 = 0.25 cores) */
    @Column(name = "cpu_millicores", nullable = false)
    private int cpuMillicores;

    /** Memory usage in bytes */
    @Column(name = "memory_bytes", nullable = false)
    private long memoryBytes;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
