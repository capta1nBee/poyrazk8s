package com.k8s.platform.config;

import com.k8s.platform.service.k8s.TerminalWebSocketHandler;
import com.k8s.platform.websocket.PodExecWebSocketHandler;
import com.k8s.platform.websocket.PodLogsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final PodLogsWebSocketHandler podLogsWebSocketHandler;
    private final PodExecWebSocketHandler podExecWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/ws/terminal")
                .setAllowedOrigins("*");

        registry.addHandler(podLogsWebSocketHandler, "/ws/pod-logs")
                .setAllowedOrigins("*");

        registry.addHandler(podExecWebSocketHandler, "/ws/pod-exec")
                .setAllowedOrigins("*");
    }
}
