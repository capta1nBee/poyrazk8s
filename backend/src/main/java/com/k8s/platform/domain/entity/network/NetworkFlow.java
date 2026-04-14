package com.k8s.platform.domain.entity.network;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_flows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private String flowId;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(name = "flow_type", nullable = false)
    private String flowType;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Source information
    @Column(name = "source_pod_name")
    private String sourcePodName;

    @Column(name = "source_namespace")
    private String sourceNamespace;

    @Column(name = "source_kind")
    private String sourceKind;

    @Column(name = "source_ip", nullable = false)
    private String sourceIp;

    @Column(name = "source_port", nullable = false)
    private Integer sourcePort;

    @Column(name = "source_node_name")
    private String sourceNodeName;

    @Column(name = "source_ingress")
    private Boolean sourceIngress;

    @Column(name = "source_egress")
    private Boolean sourceEgress;

    @Column(name = "source_drop")
    private Boolean sourceDrop;

    @Column(name = "source_pass")
    private Boolean sourcePass;

    // Destination information
    @Column(name = "destination_pod_name")
    private String destinationPodName;

    @Column(name = "destination_namespace")
    private String destinationNamespace;

    @Column(name = "destination_kind")
    private String destinationKind;

    @Column(name = "destination_ip", nullable = false)
    private String destinationIp;

    @Column(name = "destination_port", nullable = false)
    private Integer destinationPort;

    @Column(name = "destination_node_name")
    private String destinationNodeName;

    @Column(name = "destination_ingress")
    private Boolean destinationIngress;

    @Column(name = "destination_egress")
    private Boolean destinationEgress;

    @Column(name = "destination_drop")
    private Boolean destinationDrop;

    @Column(name = "destination_pass")
    private Boolean destinationPass;

    // Service information
    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_namespace")
    private String serviceNamespace;

    @Column(name = "backend_pod_name")
    private String backendPodName;

    @Column(name = "backend_pod_namespace")
    private String backendPodNamespace;

    // Network layer
    @Column(nullable = false)
    private String protocol;

    @Column(name = "tcp_flags")
    private Integer tcpFlags;

    @Column
    private Long bytes;

    @Column(name = "interface_name")
    private String interfaceName;

    @Column
    private String direction;

    // L7 layer
    @Column(name = "l7_protocol")
    private String l7Protocol;

    @Column(name = "l7_method")
    private String l7Method;

    @Column(name = "l7_host")
    private String l7Host;

    @Column(name = "l7_url")
    private String l7Url;

    @Column(name = "l7_path")
    private String l7Path;

    @Column(name = "l7_status_code")
    private Integer l7StatusCode;

    @Column(name = "l7_content_type")
    private String l7ContentType;

    // Metadata
    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
