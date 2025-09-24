package com.reglisseforge.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.reglisseforge.web.handler.LeoGenerationWebSocketHandler;

/**
 * WebSocket configuration for Leo code generation streaming
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LeoGenerationWebSocketHandler webSocketHandler;

    public WebSocketConfig(LeoGenerationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/generation")
                .setAllowedOriginPatterns("*") // Use allowedOriginPatterns for wildcard with credentials
                .withSockJS(); // Enable SockJS fallback
    }
}
