package com.reglisseforge.web.controller;

import com.reglisseforge.web.handler.LeoGenerationWebSocketHandler;
import com.reglisseforge.web.model.SubscriptionResponse;
import com.reglisseforge.web.model.WebSocketStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for WebSocket management
 */
@RestController
@RequestMapping("/api/websocket")
@CrossOrigin(origins = "*")
public class WebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    private final LeoGenerationWebSocketHandler webSocketHandler;

    public WebSocketController(LeoGenerationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Subscribe a WebSocket session to a specific generation session
     */
    @PostMapping("/subscribe/{sessionId}")
    public ResponseEntity<SubscriptionResponse> subscribeToSession(
            @PathVariable String sessionId,
            @RequestParam String webSocketSessionId) {
        
        logger.info("Request to subscribe WebSocket session {} to generation session {}", 
                   webSocketSessionId, sessionId);
        
        // Find the WebSocket session by ID and subscribe it
        boolean subscribed = webSocketHandler.subscribeSessionById(webSocketSessionId, sessionId);
        
        if (subscribed) {
            logger.info("Successfully subscribed WebSocket session {} to generation session {}", 
                       webSocketSessionId, sessionId);
            SubscriptionResponse response = SubscriptionResponse.create(sessionId, webSocketSessionId);
            return ResponseEntity.ok(response);
        } else {
            logger.warn("Failed to subscribe WebSocket session {} to generation session {} - session not found", 
                       webSocketSessionId, sessionId);
            return ResponseEntity.badRequest()
                    .body(SubscriptionResponse.createError(sessionId, webSocketSessionId, "WebSocket session not found"));
        }
    }

    /**
     * Get WebSocket connection statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<WebSocketStatsResponse> getWebSocketStats() {
        int activeConnections = webSocketHandler.getActiveConnectionCount();
        WebSocketStatsResponse response = WebSocketStatsResponse.create(activeConnections);
        return ResponseEntity.ok(response);
    }
}
