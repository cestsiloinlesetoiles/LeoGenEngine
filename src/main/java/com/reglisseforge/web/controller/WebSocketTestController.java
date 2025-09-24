package com.reglisseforge.web.controller;

import com.reglisseforge.web.handler.LeoGenerationWebSocketHandler;
import com.reglisseforge.web.model.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 * Controller for testing WebSocket functionality
 */
@RestController
@RequestMapping("/api/websocket/test")
@CrossOrigin(origins = "*")
public class WebSocketTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketTestController.class);
    
    private final LeoGenerationWebSocketHandler webSocketHandler;

    public WebSocketTestController(LeoGenerationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Test WebSocket connection and subscription
     */
    @PostMapping("/connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("activeConnections", webSocketHandler.getActiveConnectionCount());
        
        logger.info("WebSocket connection test - Active connections: {}", 
                   webSocketHandler.getActiveConnectionCount());
        
        // Send test broadcast
        StreamEvent testEvent = StreamEvent.info(null, "Connection test broadcast at " + System.currentTimeMillis());
        webSocketHandler.broadcastEvent(testEvent);
        
        result.put("testBroadcastSent", true);
        result.put("message", "Test broadcast sent to all connected clients");
        
        return ResponseEntity.ok(result);
    }

    /**
     * Test subscription with automatic session creation
     */
    @PostMapping("/subscription")
    public ResponseEntity<Map<String, Object>> testSubscription(
            @RequestParam(required = false) String webSocketSessionId) {
        
        String testSessionId = "test-session-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> result = new HashMap<>();
        result.put("testSessionId", testSessionId);
        result.put("timestamp", System.currentTimeMillis());
        result.put("activeConnections", webSocketHandler.getActiveConnectionCount());
        
        logger.info("Testing subscription for session: {}", testSessionId);
        
        boolean subscribed = false;
        if (webSocketSessionId != null && !webSocketSessionId.isEmpty()) {
            // Try to subscribe specific session
            subscribed = webSocketHandler.subscribeSessionById(webSocketSessionId, testSessionId);
            result.put("specificSubscription", subscribed);
            result.put("webSocketSessionId", webSocketSessionId);
        }
        
        if (!subscribed) {
            // Fallback: subscribe all active sessions
            int subscribedCount = webSocketHandler.subscribeAllActiveSessionsTo(testSessionId);
            result.put("autoSubscribedSessions", subscribedCount);
            subscribed = subscribedCount > 0;
        }
        
        result.put("subscriptionSuccessful", subscribed);
        
        if (subscribed) {
            // Send test events to the subscribed session
            sendTestEventsAsync(testSessionId);
            result.put("testEventsScheduled", true);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Send chunked test events to simulate real generation process
     */
    @PostMapping("/chunked-events/{sessionId}")
    public ResponseEntity<Map<String, Object>> sendChunkedTestEvents(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("timestamp", System.currentTimeMillis());
        
        int subscriberCount = webSocketHandler.getSubscriberCount(sessionId);
        result.put("subscriberCount", subscriberCount);
        
        if (subscriberCount == 0) {
            // Auto-subscribe all active sessions
            int subscribed = webSocketHandler.subscribeAllActiveSessionsTo(sessionId);
            result.put("autoSubscribedSessions", subscribed);
        }
        
        // Send chunked events asynchronously
        sendTestEventsAsync(sessionId);
        result.put("chunkedEventsStarted", true);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed WebSocket statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDetailedStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("timestamp", System.currentTimeMillis());
        stats.put("activeConnections", webSocketHandler.getActiveConnectionCount());
        stats.put("totalSessions", webSocketHandler.getActiveConnectionCount());
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Send a series of test events asynchronously
     */
    private void sendTestEventsAsync(String sessionId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Event 1: Start
                StreamEvent startEvent = StreamEvent.info(sessionId, "🚀 Starting test generation process");
                webSocketHandler.sendEventToSession(sessionId, startEvent);
                Thread.sleep(500);

                // Event 2: Thinking
                StreamEvent thinkingEvent = StreamEvent.thinking(sessionId, "🤔 Analyzing project requirements...");
                webSocketHandler.sendEventToSession(sessionId, thinkingEvent);
                Thread.sleep(1000);

                // Event 3-7: Code chunks
                for (int i = 1; i <= 5; i++) {
                    String codeChunk = "// Test code chunk " + i + "\nfunction testFunction" + i + "() {\n    console.log('Test chunk " + i + "');\n}";
                    StreamEvent codeEvent = StreamEvent.codeChunk(sessionId, codeChunk);
                    webSocketHandler.sendEventToSession(sessionId, codeEvent);
                    Thread.sleep(800);
                }

                // Event 8: Build started
                StreamEvent buildEvent = StreamEvent.buildStarted(sessionId);
                webSocketHandler.sendEventToSession(sessionId, buildEvent);
                Thread.sleep(2000);

                // Event 9: Build success
                StreamEvent successEvent = StreamEvent.buildSuccess(sessionId);
                webSocketHandler.sendEventToSession(sessionId, successEvent);
                Thread.sleep(500);

                // Event 10: Project complete
                StreamEvent completeEvent = StreamEvent.projectComplete(sessionId, "/test/path");
                webSocketHandler.sendEventToSession(sessionId, completeEvent);

                logger.info("Completed sending test events for session: {}", sessionId);
                
            } catch (InterruptedException e) {
                logger.error("Test event sending interrupted for session: {}", sessionId, e);
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Test error handling
     */
    @PostMapping("/error/{sessionId}")
    public ResponseEntity<Map<String, Object>> testError(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        
        // Ensure session is subscribed
        webSocketHandler.ensureSessionsSubscribed(sessionId);
        
        // Send error event
        StreamEvent errorEvent = StreamEvent.error(sessionId, "🚨 Test error event - This is a simulated error for testing");
        webSocketHandler.sendEventToSession(sessionId, errorEvent);
        
        result.put("errorEventSent", true);
        return ResponseEntity.ok(result);
    }

    /**
     * Force subscribe all active sessions to a test session
     */
    @PostMapping("/force-subscribe/{sessionId}")
    public ResponseEntity<Map<String, Object>> forceSubscribe(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("activeConnections", webSocketHandler.getActiveConnectionCount());
        
        int subscribed = webSocketHandler.subscribeAllActiveSessionsTo(sessionId);
        result.put("subscribedSessions", subscribed);
        result.put("success", subscribed > 0);
        
        if (subscribed > 0) {
            StreamEvent confirmEvent = StreamEvent.info(sessionId, 
                "✅ Force subscription completed - " + subscribed + " sessions subscribed to " + sessionId);
            webSocketHandler.sendEventToSession(sessionId, confirmEvent);
        }
        
        return ResponseEntity.ok(result);
    }
}
