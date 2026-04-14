package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final ClusterContextManager clusterContextManager;
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(ClusterContextManager clusterContextManager) {
        this.clusterContextManager = clusterContextManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        TerminalSession terminalSession = sessions.get(session.getId());

        if (terminalSession == null) {
            if (payload.startsWith("CONNECT:")) {
                String clusterUid = payload.substring(8);
                startTerminalSession(session, clusterUid);
            }
        } else {
            terminalSession.write(payload);
        }
    }

    private void startTerminalSession(WebSocketSession session, String clusterUid) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterUid);

            // Try finding a running pod first (prefer running status)
            var podList = client.pods().inNamespace("default").list().getItems();
            
            var runningPod = podList.stream()
                    .filter(p -> "Running".equals(p.getStatus().getPhase()))
                    .findFirst();

            if (runningPod.isEmpty()) {
                // If no running pod in default, search all namespaces
                podList = client.pods().inAnyNamespace().list().getItems();
                runningPod = podList.stream()
                        .filter(p -> "Running".equals(p.getStatus().getPhase()))
                        .findFirst();
            }

            if (runningPod.isEmpty()) {
                // If still no running pods, use first available pod
                if (podList.isEmpty()) {
                    session.sendMessage(new TextMessage("Error: No pods found in the cluster to connect to.\r\n"));
                    session.close(CloseStatus.SERVICE_RESTARTED);
                    return;
                }
            }

            var targetPod = runningPod.orElse(podList.get(0));
            String podName = targetPod.getMetadata().getName();
            String namespace = targetPod.getMetadata().getNamespace();
            
            log.info("Connecting to pod: {} in namespace: {} in cluster: {}", podName, namespace, clusterUid);
            session.sendMessage(new TextMessage("Connecting to pod: " + podName + " in namespace: " + namespace + "\r\n"));

            // Create exec session with better error handling
            ExecWatch execWatch = client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .redirectingInput()
                    .writingOutput(new WebSocketOutputStream(session))
                    .writingError(new WebSocketOutputStream(session))
                    .withTTY()
                    .exec("sh", "-c", "export TERM=xterm-256color; [ -x /bin/bash ] && /bin/bash || /bin/sh");

            TerminalSession ts = new TerminalSession(execWatch);
            sessions.put(session.getId(), ts);
            
            try {
                session.sendMessage(new TextMessage("\r\n$ Connected to " + podName + "\r\n"));
            } catch (IOException e) {
                log.error("Failed to send connection message", e);
            }

        } catch (Exception e) {
            log.error("Failed to start terminal session", e);
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("Error: Failed to start terminal session\r\n"));
                    session.sendMessage(new TextMessage("Details: " + e.getMessage() + "\r\n"));
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException ex) {
                log.error("Failed to send error message", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TerminalSession ts = sessions.remove(session.getId());
        if (ts != null) {
            ts.close();
        }
    }

    private static class TerminalSession {
        private final ExecWatch execWatch;

        public TerminalSession(ExecWatch execWatch) {
            this.execWatch = execWatch;
        }

        public void write(String data) throws IOException {
            OutputStream input = execWatch.getInput();
            if (input != null) {
                input.write(data.getBytes());
                input.flush();
            }
        }

        public void close() {
            execWatch.close();
        }
    }

    private static class WebSocketOutputStream extends OutputStream {
        private final WebSocketSession session;

        public WebSocketOutputStream(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b });
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(new String(b, off, len)));
            }
        }
    }
}
