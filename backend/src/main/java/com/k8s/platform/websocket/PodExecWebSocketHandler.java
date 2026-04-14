package com.k8s.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.config.JwtTokenProvider;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import com.k8s.platform.service.k8s.CommandAuditService;
import io.fabric8.kubernetes.client.KubernetesClient;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PodExecWebSocketHandler extends TextWebSocketHandler {

    private final ClusterContextManager clusterContextManager;
    private final CommandAuditService auditService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.k8s.platform.service.audit.AuditLogService persistentAuditService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, ExecSessionState> sessions = new ConcurrentHashMap<>();

    public PodExecWebSocketHandler(ClusterContextManager clusterContextManager,
            CommandAuditService auditService,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            com.k8s.platform.service.audit.AuditLogService persistentAuditService) {
        this.clusterContextManager = clusterContextManager;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.persistentAuditService = persistentAuditService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ExecSessionState state = sessions.get(session.getId());

        if (state == null) {
            Map<String, Object> req = mapper.readValue(message.getPayload(), Map.class);
            String token = (String) req.get("token");
            String username = null;

            if (token != null && jwtTokenProvider.validateToken(token)) {
                username = jwtTokenProvider.getUsernameFromToken(token);
            }

            if (username == null) {
                session.sendMessage(new TextMessage("\r\nUnauthorized: Please provide a valid token.\r\n"));
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                session.sendMessage(new TextMessage("\r\nUser not found.\r\n"));
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            startExec(session, user,
                    (String) req.get("clusterUid"),
                    (String) req.get("namespace"),
                    (String) req.get("podName"),
                    (String) req.get("container"));
        } else {
            state.processInput(message.getPayload());
        }
    }

    private void startExec(WebSocketSession session, User user, String clusterUid,
            String namespace, String podName, String container) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            String sessionUid = UUID.randomUUID().toString();

            auditService.createSession(user.getUsername(), sessionUid, clusterUid, namespace, podName);

            var pod = client.pods().inNamespace(namespace).withName(podName);

            // Format roles string — use Casbin RoleTemplate names (new system)
            java.util.Set<String> roleSet = auditService.getUserRoleNames(user.getUsername());
            String roles = roleSet.isEmpty() ? "NONE" : String.join(", ", roleSet);

            String welcomeMsg = String.format(
                    "\r\n" +
                            "\u001b[32m* Welcome Kubernetes Terminal\u001b[0m\r\n" +
                            "\u001b[36m* USER:      %s\u001b[0m\r\n" +
                            "\u001b[36m* ROLES:     %s\u001b[0m\r\n" +
                            "\u001b[36m* Session:   %s\u001b[0m\r\n" +
                            "\r\n",
                    user.getUsername(), roles, sessionUid);

            session.sendMessage(new TextMessage(welcomeMsg));

            // Send disable bracketed paste mode sequence to terminal
            session.sendMessage(new TextMessage("\u001b[?2004l"));

            ExecWatch exec = (container != null && !container.isBlank())
                    ? pod.inContainer(container)
                            .redirectingInput()
                            .writingOutput(new WsOut(session))
                            .writingError(new WsOut(session))
                            .withTTY()
                            // Fix character issues by setting TERM and disabling inputrc features that
                            // cause issues
                            .exec("sh", "-c", "export TERM=xterm; [ -x /bin/bash ] && exec bash || exec sh")
                    : pod.redirectingInput()
                            .writingOutput(new WsOut(session))
                            .writingError(new WsOut(session))
                            .withTTY()
                            // Fix character issues by setting TERM and disabling inputrc features that
                            // cause issues
                            .exec("sh", "-c", "export TERM=xterm; [ -x /bin/bash ] && exec bash || exec sh");

            ExecSessionState state = new ExecSessionState(exec, user, clusterUid, namespace, podName, container,
                    sessionUid, session, auditService, persistentAuditService);
            sessions.put(session.getId(), state);

        } catch (Exception e) {
            log.error("Exec error", e);
            try {
                session.sendMessage(new TextMessage("\r\nConnection failed: " + e.getMessage() + "\r\n"));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ExecSessionState s = sessions.remove(session.getId());
        if (s != null) {
            s.close();
            auditService.closeSession(s.sessionUid);
        }
    }

    static class ExecSessionState {
        private final ExecWatch exec;
        private final User user;
        private final String clusterId;
        private final String namespace;
        private final String podName;
        private final String containerName;
        private final String sessionUid;
        private final WebSocketSession wsSession;
        private final CommandAuditService auditService;
        private final com.k8s.platform.service.audit.AuditLogService persistentAuditService;
        private final StringBuilder commandBuffer = new StringBuilder();

        ExecSessionState(ExecWatch exec, User user, String clusterId, String namespace, String podName,
                String containerName, String sessionUid, WebSocketSession wsSession, CommandAuditService auditService,
                com.k8s.platform.service.audit.AuditLogService persistentAuditService) {
            this.exec = exec;
            this.user = user;
            this.clusterId = clusterId;
            this.namespace = namespace;
            this.podName = podName;
            this.containerName = containerName;
            this.sessionUid = sessionUid;
            this.wsSession = wsSession;
            this.auditService = auditService;
            this.persistentAuditService = persistentAuditService;
        }

        void processInput(String data) throws Exception {
            // Handle resize messages from the frontend
            if (data.startsWith("{\"type\":\"resize\"")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resizeMsg = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(data, Map.class);
                    int cols = ((Number) resizeMsg.get("cols")).intValue();
                    int rows = ((Number) resizeMsg.get("rows")).intValue();
                    if (cols > 0 && rows > 0) {
                        exec.resize(cols, rows);
                        log.debug("Terminal resized to {}x{} for session {}", cols, rows, sessionUid);
                    }
                } catch (Exception e) {
                    log.warn("Failed to process resize message: {}", e.getMessage());
                }
                return;
            }

            // Echo back handling is done by the shell (TTY), we only snoop here for audit.
            // If data contains \r, process command line.

            for (char c : data.toCharArray()) {
                if (c == '\r' || c == '\n') {
                    String cmd = commandBuffer.toString().trim();
                    if (!cmd.isEmpty()) {
                        log.debug("Checking command authorization for: '{}'", cmd);
                        boolean allowed = auditService.isCommandAllowed(user, cmd);
                        auditService.logCommand(user.getUsername(), clusterId, namespace, podName, containerName, cmd,
                                allowed);

                        String details = String.format("pod: %s, exec: %s, status: %s", podName, cmd,
                                allowed ? "allow" : "deny");
                        persistentAuditService.log(user.getUsername(), "pod exec", details, clusterId, null);

                        if (!allowed) {
                            log.warn("Command blocked: {}", cmd);

                            // 1. Send Ctrl-C (3) and Ctrl-U (21) to cancel and clear
                            // 2. Send newline to force prompt refresh
                            exec.getInput().write(new byte[] { 3, 21, 13 });
                            exec.getInput().flush();

                            commandBuffer.setLength(0);

                            // Clear line visually and show error
                            wsSession.sendMessage(
                                    new TextMessage(
                                            "\r\u001b[2K\u001b[31;1mUNAUTHORIZED COMMAND: " + cmd + "\u001b[0m\r\n"));
                            return;
                        }
                    }
                    commandBuffer.setLength(0);
                } else if (c == 127 || c == 8) { // Backspace
                    if (commandBuffer.length() > 0) {
                        commandBuffer.setLength(commandBuffer.length() - 1);
                    }
                } else if (c >= 32 && c <= 126) { // Printable
                    commandBuffer.append(c);
                }
            }

            // Write to shell
            exec.getInput().write(data.getBytes());
            exec.getInput().flush();
        }

        void close() {
            exec.close();
        }
    }

    static class WsOut extends OutputStream {
        private final WebSocketSession session;

        WsOut(WebSocketSession s) {
            session = s;
        }

        @Override
        public void write(byte[] b, int o, int l) throws IOException {
            if (session.isOpen())
                session.sendMessage(new TextMessage(new String(b, o, l)));
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b }, 0, 1);
        }
    }
}
