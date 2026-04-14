package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkPolicyService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public List<NetworkPolicy> listNetworkPolicies(String clusterUid) {
        log.info("Listing all network policies in cluster UID: {}", clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().networkPolicies().inAnyNamespace().list().getItems();
    }

    public List<NetworkPolicy> listNetworkPolicies(String clusterUid, String namespace) {
        log.info("Listing network policies in namespace: {} in cluster UID: {}", namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().networkPolicies().inNamespace(namespace).list().getItems();
    }

    public NetworkPolicy getNetworkPolicy(String clusterUid, String namespace, String name) {
        log.info("Getting network policy: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().networkPolicies().inNamespace(namespace).withName(name).get();
    }

    public NetworkPolicy createNetworkPolicy(String clusterUid, String namespace, NetworkPolicy networkPolicy) {
        log.info("Creating network policy: {}/{} in cluster UID: {}", namespace, networkPolicy.getMetadata().getName(),
                clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().networkPolicies().inNamespace(namespace).resource(networkPolicy).create();
    }

    public NetworkPolicy updateNetworkPolicy(String clusterUid, String namespace, String name,
            NetworkPolicy networkPolicy) {
        log.info("Updating network policy: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().networkPolicies().inNamespace(namespace).withName(name).replace(networkPolicy);
    }

    public void deleteNetworkPolicy(String clusterUid, String namespace, String name) {
        log.info("Deleting network policy: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.network().v1().networkPolicies().inNamespace(namespace).withName(name).delete();

        log.info("Network policy deleted successfully: {}/{}", namespace, name);
    }

    public Map<String, Object> visualize(String clusterUid, String namespace, String name) {
        log.info("Visualizing network policy: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        NetworkPolicy policy = getNetworkPolicy(clusterUid, namespace, name);

        if (policy != null) {
            return Map.of(
                    "name", name,
                    "namespace", namespace,
                    "podSelector",
                    policy.getSpec().getPodSelector() != null ? policy.getSpec().getPodSelector() : Map.of(),
                    "policyTypes",
                    policy.getSpec().getPolicyTypes() != null ? policy.getSpec().getPolicyTypes() : List.of(),
                    "ingress", policy.getSpec().getIngress() != null ? policy.getSpec().getIngress() : List.of(),
                    "egress", policy.getSpec().getEgress() != null ? policy.getSpec().getEgress() : List.of());
        }

        throw new RuntimeException("Network policy not found: " + name);
    }

    public Map<String, Object> simulate(String clusterUid, String namespace, String name, Map<String, String> sourcePod,
            Map<String, String> targetPod) {
        log.info("Simulating network policy: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        NetworkPolicy policy = getNetworkPolicy(clusterUid, namespace, name);

        if (policy != null) {
            // Simple simulation - check if source pod matches policy selector
            boolean allowed = true;

            if (policy.getSpec().getPodSelector() != null
                    && policy.getSpec().getPodSelector().getMatchLabels() != null) {
                var matchLabels = policy.getSpec().getPodSelector().getMatchLabels();

                // Check if target pod matches selector
                for (Map.Entry<String, String> entry : matchLabels.entrySet()) {
                    if (!entry.getValue().equals(targetPod.get(entry.getKey()))) {
                        allowed = false;
                        break;
                    }
                }
            }

            return Map.of(
                    "name", name,
                    "namespace", namespace,
                    "sourcePod", sourcePod,
                    "targetPod", targetPod,
                    "allowed", allowed,
                    "reason", allowed ? "Policy allows connection" : "Policy blocks connection");
        }

        throw new RuntimeException("Network policy not found: " + name);
    }
}
