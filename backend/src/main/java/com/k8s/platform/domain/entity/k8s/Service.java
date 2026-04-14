package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "services")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "Service";

    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "cluster_id", nullable = false)
    private Long clusterId;

    @Column(nullable = false)
    private String namespace;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String uid;

    @Column(name = "resource_version")
    private String resourceVersion;

    private Integer generation;

    @Column(name = "service_type")
    private String serviceType; // ClusterIP, NodePort, LoadBalancer

    @Column(name = "cluster_ip")
    private String clusterIP;

    @Column(name = "external_ip")
    private String externalIP;

    @Column(name = "external_ips", columnDefinition = "TEXT")
    private String externalIPs; // JSON

    @Column(name = "load_balancer_ip")
    private String loadBalancerIP;

    @Column(name = "external_name")
    private String externalName;

    @Column(name = "session_affinity")
    private String sessionAffinity;

    @Column(name = "load_balancer_source_ranges", columnDefinition = "TEXT")
    private String loadBalancerSourceRanges;

    @Column(name = "ip_families", columnDefinition = "TEXT")
    private String ipFamilies;

    @Column(name = "ip_family_policy")
    private String ipFamilyPolicy;

    @Column(columnDefinition = "TEXT")
    private String ports; // JSON

    @Column(columnDefinition = "TEXT")
    private String selector; // JSON

    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs; // JSON

    @Column(columnDefinition = "TEXT")
    private String labels; // JSON

    @Column(columnDefinition = "TEXT")
    private String annotations; // JSON

    @Column(name = "managed_fields", columnDefinition = "TEXT")
    private String managedFields; // JSON

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "k8s_created_at")
    private String k8sCreatedAt; // Kubernetes metadata.creationTimestamp

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
