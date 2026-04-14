package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public List<Ingress> listIngresses(String clusterUid) {
        log.info("Listing all ingresses in cluster UID: {}", clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().ingresses().inAnyNamespace().list().getItems();
    }

    public List<Ingress> listIngresses(String clusterUid, String namespace) {
        log.info("Listing ingresses in namespace: {} in cluster UID: {}", namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().ingresses().inNamespace(namespace).list().getItems();
    }

    public Ingress getIngress(String clusterUid, String namespace, String name) {
        log.info("Getting ingress: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
    }

    public Ingress createIngress(String clusterUid, String namespace, Ingress ingress) {
        log.info("Creating ingress: {}/{} in cluster UID: {}", namespace, ingress.getMetadata().getName(), clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().ingresses().inNamespace(namespace).resource(ingress).create();
    }

    public Ingress updateIngress(String clusterUid, String namespace, String name, Ingress ingress) {
        log.info("Updating ingress: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.network().v1().ingresses().inNamespace(namespace).withName(name).replace(ingress);
    }

    public void deleteIngress(String clusterUid, String namespace, String name) {
        log.info("Deleting ingress: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.network().v1().ingresses().inNamespace(namespace).withName(name).delete();

        log.info("Ingress deleted successfully: {}/{}", namespace, name);
    }

    public Ingress updateRules(String clusterUid, String namespace, String name, List<IngressRule> rules) {
        log.info("Updating rules for ingress: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        Ingress ingress = client.network().v1().ingresses().inNamespace(namespace).withName(name).get();

        if (ingress != null) {
            ingress.getSpec().setRules(rules);
            return client.network().v1().ingresses().inNamespace(namespace).resource(ingress).update();
        }

        throw new RuntimeException("Ingress not found: " + name);
    }

    public Ingress updateTLS(String clusterUid, String namespace, String name, List<IngressTLS> tls) {
        log.info("Updating TLS for ingress: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        Ingress ingress = client.network().v1().ingresses().inNamespace(namespace).withName(name).get();

        if (ingress != null) {
            ingress.getSpec().setTls(tls);
            return client.network().v1().ingresses().inNamespace(namespace).resource(ingress).update();
        }

        throw new RuntimeException("Ingress not found: " + name);
    }

    public Map<String, Object> testRoute(String clusterUid, String namespace, String name, String path) {
        log.info("Testing route for ingress: {}/{} path: {} in cluster UID: {}", namespace, name, path, clusterUid);

        Ingress ingress = getIngress(clusterUid, namespace, name);

        if (ingress != null && ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null) {
            var loadBalancer = ingress.getStatus().getLoadBalancer();

            return Map.of(
                    "name", name,
                    "namespace", namespace,
                    "path", path,
                    "loadBalancer", loadBalancer,
                    "status", "available");
        }

        return Map.of(
                "name", name,
                "namespace", namespace,
                "path", path,
                "status", "pending");
    }
}
