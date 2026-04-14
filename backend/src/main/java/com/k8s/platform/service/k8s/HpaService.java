package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HpaService {

    private final ClusterContextManager clusterContextManager;

    public List<HorizontalPodAutoscaler> listHpas(String clusterUid) {
        log.info("Listing all HPAs in cluster UID: {}", clusterUid);
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.autoscaling().v2().horizontalPodAutoscalers()
                .inAnyNamespace().list().getItems();
    }

    public List<HorizontalPodAutoscaler> listHpas(String clusterUid, String namespace) {
        log.info("Listing HPAs in namespace: {} in cluster UID: {}", namespace, clusterUid);
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).list().getItems();
    }

    public HorizontalPodAutoscaler getHpa(String clusterUid, String namespace, String name) {
        log.info("Getting HPA: {}/{} in cluster UID: {}", namespace, name, clusterUid);
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).withName(name).get();
    }

    public HorizontalPodAutoscaler createHpa(String clusterUid, String namespace,
            HorizontalPodAutoscaler hpa) {
        log.info("Creating HPA: {}/{} in cluster UID: {}", namespace,
                hpa.getMetadata().getName(), clusterUid);
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).resource(hpa).create();
    }

    public HorizontalPodAutoscaler updateHpa(String clusterUid, String namespace, String name,
            HorizontalPodAutoscaler hpa) {
        log.info("Updating HPA: {}/{} in cluster UID: {}", namespace, name, clusterUid);
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).withName(name).replace(hpa);
    }

    public void deleteHpa(String clusterUid, String namespace, String name) {
        log.info("Deleting HPA: {}/{} in cluster UID: {}", namespace, name, clusterUid);
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).withName(name).delete();
        log.info("HPA deleted successfully: {}/{}", namespace, name);
    }
}
