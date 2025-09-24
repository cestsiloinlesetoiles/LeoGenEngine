package com.reglisseforge.web.service;

import com.reglisseforge.web.handler.LeoGenerationWebSocketHandler;
import com.reglisseforge.web.model.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Service for managing streaming events during Leo code generation
 */
@Service
public class StreamEventService {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamEventService.class);
    
    private final LeoGenerationWebSocketHandler webSocketHandler;

    public StreamEventService(LeoGenerationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Send a thinking event
     */
    public void sendThinking(String sessionId, String message) {
        StreamEvent event = StreamEvent.thinking(sessionId, message);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Thinking - {}", sessionId, message);
    }

    /**
     * Send event to subscribed sessions only (no automatic subscription)
     */
    private void sendToSubscribedSessions(String sessionId, StreamEvent event) {
        webSocketHandler.sendEventToSession(sessionId, event);
    }

    /**
     * Send a generating event
     */
    public void sendGenerating(String sessionId, String message) {
        StreamEvent event = StreamEvent.generating(sessionId, message);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Generating - {}", sessionId, message);
    }

    /**
     * Send a code chunk event
     */
    public void sendCodeChunk(String sessionId, String chunk) {
        StreamEvent event = StreamEvent.codeChunk(sessionId, chunk);
        sendToSubscribedSessions(sessionId, event);
        // Don't log code chunks to avoid spam - just debug level
        logger.debug("Session {}: Code chunk sent ({} chars)", sessionId, chunk.length());
    }

    /**
     * Send build started event
     */
    public void sendBuildStarted(String sessionId) {
        StreamEvent event = StreamEvent.buildStarted(sessionId);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Build started", sessionId);
    }

    /**
     * Send build success event
     */
    public void sendBuildSuccess(String sessionId) {
        StreamEvent event = StreamEvent.buildSuccess(sessionId);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Build succeeded", sessionId);
    }

    /**
     * Send build failed event
     */
    public void sendBuildFailed(String sessionId, String errorOutput) {
        StreamEvent event = StreamEvent.buildFailed(sessionId, errorOutput);
        sendToSubscribedSessions(sessionId, event);
        logger.warn("Session {}: Build failed", sessionId);
    }

    /**
     * Send fixing started event
     */
    public void sendFixingStarted(String sessionId, int attempt, int maxAttempts) {
        StreamEvent event = StreamEvent.fixingStarted(sessionId, attempt, maxAttempts);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Fixing started (attempt {}/{})", sessionId, attempt, maxAttempts);
    }

    /**
     * Send fixing progress event
     */
    public void sendFixingProgress(String sessionId, String message, int attempt) {
        StreamEvent event = StreamEvent.fixingProgress(sessionId, message, attempt);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Fixing progress (attempt {}) - {}", sessionId, attempt, message);
    }

    /**
     * Send fixing success event
     */
    public void sendFixingSuccess(String sessionId, int attempt) {
        StreamEvent event = StreamEvent.fixingSuccess(sessionId, attempt);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Fixing succeeded on attempt {}", sessionId, attempt);
    }

    /**
     * Send fixing failed event
     */
    public void sendFixingFailed(String sessionId, int maxAttempts) {
        StreamEvent event = StreamEvent.fixingFailed(sessionId, maxAttempts);
        sendToSubscribedSessions(sessionId, event);
        logger.warn("Session {}: Fixing failed after {} attempts", sessionId, maxAttempts);
    }

    /**
     * Send project complete event
     */
    public void sendProjectComplete(String sessionId, String projectPath) {
        StreamEvent event = StreamEvent.projectComplete(sessionId, projectPath);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Project completed at {}", sessionId, projectPath);
    }

    /**
     * Send error event
     */
    public void sendError(String sessionId, String error) {
        StreamEvent event = StreamEvent.error(sessionId, error);
        sendToSubscribedSessions(sessionId, event);
        logger.error("Session {}: Error - {}", sessionId, error);
    }

    /**
     * Send info event
     */
    public void sendInfo(String sessionId, String message) {
        StreamEvent event = StreamEvent.info(sessionId, message);
        sendToSubscribedSessions(sessionId, event);
        logger.info("Session {}: Info - {}", sessionId, message);
    }

    /**
     * Create a Consumer for code chunk streaming that sends chunks via WebSocket
     */
    public Consumer<String> createCodeChunkConsumer(String sessionId) {
        return chunk -> sendCodeChunk(sessionId, chunk);
    }

    /**
     * Get the number of active WebSocket connections
     */
    public int getActiveConnectionCount() {
        return webSocketHandler.getActiveConnectionCount();
    }

    /**
     * Get the number of subscribers for a specific session
     */
    public int getSubscriberCount(String sessionId) {
        return webSocketHandler.getSubscriberCount(sessionId);
    }
}
