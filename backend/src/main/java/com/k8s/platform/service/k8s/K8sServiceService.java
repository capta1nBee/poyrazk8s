package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class K8sServiceService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public List<io.fabric8.kubernetes.api.model.Service> listServices(String clusterUid) {
        log.info("Listing all services in cluster UID: {}", clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.services().inAnyNamespace().list().getItems();
    }

    public List<io.fabric8.kubernetes.api.model.Service> listServices(String clusterUid, String namespace) {
        log.info("Listing services in namespace: {} in cluster UID: {}", namespace, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.services().inNamespace(namespace).list().getItems();
    }

    public io.fabric8.kubernetes.api.model.Service getService(String clusterUid, String namespace, String name) {
        log.info("Getting service: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.services().inNamespace(namespace).withName(name).get();
    }

    public io.fabric8.kubernetes.api.model.Service createService(String clusterUid, String namespace,
            io.fabric8.kubernetes.api.model.Service service) {
        log.info("Creating service: {}/{} in cluster UID: {}", namespace, service.getMetadata().getName(), clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.services().inNamespace(namespace).resource(service).create();
    }

    public io.fabric8.kubernetes.api.model.Service updateService(String clusterUid, String namespace, String name,
            io.fabric8.kubernetes.api.model.Service service) {
        log.info("Updating service: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        return client.services().inNamespace(namespace).withName(name).replace(service);
    }

    public void deleteService(String clusterUid, String namespace, String name) {
        log.info("Deleting service: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        client.services().inNamespace(namespace).withName(name).delete();

        log.info("Service deleted successfully: {}/{}", namespace, name);
    }

    public io.fabric8.kubernetes.api.model.Service changeServiceType(String clusterUid, String namespace, String name,
            String newType) {
        log.info("Changing service type: {}/{} to {} in cluster UID: {}", namespace, name, newType, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        io.fabric8.kubernetes.api.model.Service service = client.services().inNamespace(namespace).withName(name).get();

        if (service != null) {
            service.getSpec().setType(newType);
            return client.services().inNamespace(namespace).resource(service).update();
        }

        throw new RuntimeException("Service not found: " + name);
    }

    public io.fabric8.kubernetes.api.model.Service updatePorts(String clusterUid, String namespace, String name,
            List<ServicePort> ports) {
        log.info("Updating ports for service: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        io.fabric8.kubernetes.api.model.Service service = client.services().inNamespace(namespace).withName(name).get();

        if (service != null) {
            service.getSpec().setPorts(ports);
            return client.services().inNamespace(namespace).resource(service).update();
        }

        throw new RuntimeException("Service not found: " + name);
    }

    public Map<String, Object> getEndpoints(String clusterUid, String namespace, String name) {
        log.info("Getting endpoints for service: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        io.fabric8.kubernetes.api.model.Endpoints endpoints = client.endpoints().inNamespace(namespace).withName(name)
                .get();

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("namespace", namespace);

        List<Map<String, Object>> addresses = new java.util.ArrayList<>();
        if (endpoints != null && endpoints.getSubsets() != null) {
            for (var subset : endpoints.getSubsets()) {
                List<Integer> ports = subset.getPorts().stream()
                        .map(io.fabric8.kubernetes.api.model.EndpointPort::getPort)
                        .collect(Collectors.toList());

                if (subset.getAddresses() != null) {
                    for (var addr : subset.getAddresses()) {
                        for (Integer port : ports) {
                            Map<String, Object> endpointAddr = new HashMap<>();
                            endpointAddr.put("ip", addr.getIp());
                            endpointAddr.put("port", port);
                            endpointAddr.put("nodeName", addr.getNodeName());
                            endpointAddr.put("targetName",
                                    addr.getTargetRef() != null ? addr.getTargetRef().getName() : null);
                            endpointAddr.put("ready", true);
                            addresses.add(endpointAddr);
                        }
                    }
                }

                if (subset.getNotReadyAddresses() != null) {
                    for (var addr : subset.getNotReadyAddresses()) {
                        for (Integer port : ports) {
                            Map<String, Object> endpointAddr = new HashMap<>();
                            endpointAddr.put("ip", addr.getIp());
                            endpointAddr.put("port", port);
                            endpointAddr.put("nodeName", addr.getNodeName());
                            endpointAddr.put("targetName",
                                    addr.getTargetRef() != null ? addr.getTargetRef().getName() : null);
                            endpointAddr.put("ready", false);
                            addresses.add(endpointAddr);
                        }
                    }
                }
            }
        }

        result.put("endpoints", addresses);
        return result;
    }
}
