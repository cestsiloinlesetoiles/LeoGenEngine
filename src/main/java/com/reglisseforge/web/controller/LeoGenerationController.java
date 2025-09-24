package com.reglisseforge.web.controller;

import com.reglisseforge.tools.WebLeoCodeEngine;
import com.reglisseforge.web.model.GenerationRequest;
import com.reglisseforge.web.model.GenerationResponse;
import com.reglisseforge.web.model.StatusResponse;
import com.reglisseforge.web.model.ConnectionsResponse;
import com.reglisseforge.web.model.HealthResponse;
import com.reglisseforge.web.service.StreamEventService;
import com.reglisseforge.web.handler.LeoGenerationWebSocketHandler;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for Leo code generation
 */
@RestController
@RequestMapping("/api/generation")
@CrossOrigin(origins = "*") // Configure appropriately for production
public class LeoGenerationController {
    
    private static final Logger logger = LoggerFactory.getLogger(LeoGenerationController.class);
    
    private final WebLeoCodeEngine webLeoCodeEngine;
    private final StreamEventService eventService;
    public LeoGenerationController(WebLeoCodeEngine webLeoCodeEngine, StreamEventService eventService, 
                                   LeoGenerationWebSocketHandler webSocketHandler) {
        this.webLeoCodeEngine = webLeoCodeEngine;
        this.eventService = eventService;
    }

    /**
     * Start Leo code generation process
     */
    @PostMapping("/start")
    public ResponseEntity<GenerationResponse> startGeneration(@Valid @RequestBody GenerationRequest request) {
        try {
            // Generate session ID if not provided
            String sessionId = request.getSessionId() != null ? 
                request.getSessionId() : UUID.randomUUID().toString();
            
            logger.info("Starting generation for project: {} with session: {}", 
                       request.getProjectName(), sessionId);
            
            // Sessions will be subscribed via the WebSocket API endpoint when the frontend connects
            logger.info("Generation session {} ready for WebSocket subscriptions", sessionId);
            
            // Start generation asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    // Initialize project and generate code
                    String projectPath = webLeoCodeEngine.initProject(
                        sessionId, 
                        request.getProjectName(), 
                        request.getProjectDescription(),
                        request.getWorkspacePath()
                    );
                    
                    // Build and fix with max 20 attempts
                    webLeoCodeEngine.buildAndFix(sessionId, projectPath, 20);
                    
                } catch (Exception e) {
                    logger.error("Error during generation for session: {}", sessionId, e);
                    eventService.sendError(sessionId, "Generation failed: " + e.getMessage());
                }
            });
            
            // Return immediate response
            GenerationResponse response = GenerationResponse.success(sessionId, "Generation started");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to start generation", e);
            GenerationResponse response = GenerationResponse.error("Failed to start generation: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get generation status
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<StatusResponse> getGenerationStatus(@PathVariable String sessionId) {
        int subscriberCount = eventService.getSubscriberCount(sessionId);
        StatusResponse response = StatusResponse.create(sessionId, subscriberCount);
        return ResponseEntity.ok(response);
    }

    /**
     * Get active connections count
     */
    @GetMapping("/connections")
    public ResponseEntity<ConnectionsResponse> getConnectionsInfo() {
        int activeConnections = eventService.getActiveConnectionCount();
        ConnectionsResponse response = ConnectionsResponse.create(activeConnections);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        int activeConnections = eventService.getActiveConnectionCount();
        HealthResponse response = HealthResponse.create(activeConnections);
        return ResponseEntity.ok(response);
    }
}
