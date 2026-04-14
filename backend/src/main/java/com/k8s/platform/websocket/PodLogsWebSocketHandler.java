package com.k8s.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PodLogsWebSocketHandler extends TextWebSocketHandler {

    private final ClusterContextManager clusterContextManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, LogWatch> sessions = new ConcurrentHashMap<>();

    public PodLogsWebSocketHandler(ClusterContextManager clusterContextManager) {
        this.clusterContextManager = clusterContextManager;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (sessions.containsKey(session.getId())) return;

        Map<String, Object> req = mapper.readValue(message.getPayload(), Map.class);

        KubernetesClient client = clusterContextManager.getClient((String) req.get("clusterUid"));

        var pod = client.pods()
                .inNamespace((String) req.get("namespace"))
                .withName((String) req.get("podName"));

        LogWatch watch = (req.get("container") != null)
                ? pod.inContainer((String) req.get("container"))
                    .tailingLines((Integer) req.getOrDefault("tail", 100))
                    .watchLog()
                : pod.tailingLines((Integer) req.getOrDefault("tail", 100))
                    .watchLog();

        sessions.put(session.getId(), watch);

        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(watch.getOutput()))) {
                String line;
                while ((line = r.readLine()) != null && session.isOpen())
                    session.sendMessage(new TextMessage(line + "\n"));
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LogWatch w = sessions.remove(session.getId());
        if (w != null) w.close();
    }
}
