package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedNetworkPolicyDTO {

    private Long id;
    private String clusterUid;
    private String namespace;
    private String name;
    private String policyType; // 'ingress' or 'egress'

    // Pod selector for the policy target
    private Map<String, String> podSelector;

    // Selected rules that make up this policy
    private List<NetworkPolicyRuleDTO> rules;

    // Generated YAML content
    private String yamlContent;

    // Status: draft, applied, deleted
    private String status;

    // Metadata
    private String description;
    private String createdBy;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Migration info
    private Integer currentVersion;
    private Integer totalVersions;

    // Kubernetes NetworkPolicy spec components (for detailed view)
    private PolicySpec spec;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicySpec {
        private Map<String, String> podSelector;
        private List<String> policyTypes;
        private List<IngressRule> ingress;
        private List<EgressRule> egress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngressRule {
        private List<NetworkPolicyPeer> from;
        private List<NetworkPolicyPort> ports;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EgressRule {
        private List<NetworkPolicyPeer> to;
        private List<NetworkPolicyPort> ports;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkPolicyPeer {
        private Map<String, String> podSelector;
        private Map<String, String> namespaceSelector;
        private IpBlock ipBlock;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpBlock {
        private String cidr;
        private List<String> except;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkPolicyPort {
        private String protocol;
        private Object port; // Can be Integer or String (named port)
    }
}
