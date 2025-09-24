package com.reglisseforge.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reglisseforge.web.model.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler for streaming Leo code generation events
 */
@Component
public class LeoGenerationWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(LeoGenerationWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    
    // Track sessions by sessionId for targeted messaging
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> sessionGroups = new ConcurrentHashMap<>();
    
    // Track all active sessions
    private final CopyOnWriteArraySet<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    public LeoGenerationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        allSessions.add(session);
        logger.info("WebSocket connection established: {}", session.getId());
        
        // Send welcome message
        StreamEvent welcomeEvent = StreamEvent.info(null, "Connected to Leo Generation Stream");
        sendEventToSession(session, welcomeEvent);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        allSessions.remove(session);
        
        // Remove from all session groups
        sessionGroups.values().forEach(sessions -> sessions.remove(session));
        
        logger.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        
        // Send error event to client
        StreamEvent errorEvent = StreamEvent.error(null, "Connection error: " + exception.getMessage());
        sendEventToSession(session, errorEvent);
    }

    /**
     * Subscribe a WebSocket session to events for a specific generation session
     */
    public void subscribeToSession(WebSocketSession webSocketSession, String sessionId) {
        sessionGroups.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>())
                     .add(webSocketSession);
        logger.info("WebSocket session {} subscribed to generation session {}", 
                   webSocketSession.getId(), sessionId);
    }

    /**
     * Subscribe a WebSocket session by ID to events for a specific generation session
     */
    public boolean subscribeSessionById(String webSocketSessionId, String sessionId) {
        logger.info("Attempting to subscribe WebSocket session {} to generation session {}", 
                   webSocketSessionId, sessionId);
        logger.info("Available WebSocket sessions: {}", 
                   allSessions.stream().map(WebSocketSession::getId).toArray());
        
        WebSocketSession session = findSessionById(webSocketSessionId);
        if (session != null) {
            subscribeToSession(session, sessionId);
            return true;
        } else {
            logger.warn("WebSocket session {} not found in active sessions", webSocketSessionId);
            
            // Fallback: if we have exactly one active session, use it
            if (allSessions.size() == 1) {
                WebSocketSession fallbackSession = allSessions.iterator().next();
                logger.info("Using fallback: subscribing the only active session {} to generation session {}", 
                           fallbackSession.getId(), sessionId);
                subscribeToSession(fallbackSession, sessionId);
                return true;
            }
        }
        return false;
    }

    /**
     * Find a WebSocket session by its ID
     */
    private WebSocketSession findSessionById(String webSocketSessionId) {
        return allSessions.stream()
                .filter(session -> session.getId().equals(webSocketSessionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Subscribe all active WebSocket sessions to a generation session
     * This is useful when a generation starts and we want all connected clients to receive events
     */
    public int subscribeAllActiveSessionsTo(String sessionId) {
        int subscribed = 0;
        for (WebSocketSession session : allSessions) {
            if (session.isOpen()) {
                subscribeToSession(session, sessionId);
                subscribed++;
            }
        }
        logger.info("Subscribed {} active WebSocket sessions to generation session {}", subscribed, sessionId);
        return subscribed;
    }

    /**
     * Check if any sessions are subscribed to a generation session, and retry auto-subscription if needed
     */
    public int ensureSessionsSubscribed(String sessionId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionGroups.get(sessionId);
        int currentSubscribers = sessions != null ? sessions.size() : 0;
        
        if (currentSubscribers == 0) {
            logger.info("No sessions subscribed to generation session {}, attempting auto-subscription", sessionId);
            return subscribeAllActiveSessionsTo(sessionId);
        }
        
        return currentSubscribers;
    }

    /**
     * Unsubscribe a WebSocket session from a specific generation session
     */
    public void unsubscribeFromSession(WebSocketSession webSocketSession, String sessionId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionGroups.get(sessionId);
        if (sessions != null) {
            sessions.remove(webSocketSession);
            if (sessions.isEmpty()) {
                sessionGroups.remove(sessionId);
            }
        }
        logger.info("WebSocket session {} unsubscribed from generation session {}", 
                   webSocketSession.getId(), sessionId);
    }

    /**
     * Send an event to all sessions subscribed to a specific generation session
     */
    public void sendEventToSession(String sessionId, StreamEvent event) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionGroups.get(sessionId);
        if (sessions != null && !sessions.isEmpty()) {
            logger.debug("Sending event {} to {} sessions for sessionId {}", 
                        event.getType(), sessions.size(), sessionId);
            
            sessions.forEach(session -> sendEventToSession(session, event));
        } else {
            logger.warn("No WebSocket sessions found for generation session: {}", sessionId);
        }
    }

    /**
     * Send an event to a specific WebSocket session
     */
    public void sendEventToSession(WebSocketSession session, StreamEvent event) {
        if (session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(event);
                TextMessage message = new TextMessage(json);
                session.sendMessage(message);
                
                logger.debug("Sent event {} to WebSocket session {}", event.getType(), session.getId());
            } catch (IOException e) {
                logger.error("Failed to send event to WebSocket session {}: {}", 
                           session.getId(), e.getMessage());
                
                // Remove failed session
                allSessions.remove(session);
                sessionGroups.values().forEach(sessions -> sessions.remove(session));
            }
        } else {
            logger.warn("Attempted to send message to closed WebSocket session: {}", session.getId());
            allSessions.remove(session);
            sessionGroups.values().forEach(sessions -> sessions.remove(session));
        }
    }

    /**
     * Broadcast an event to all connected WebSocket sessions
     */
    public void broadcastEvent(StreamEvent event) {
        logger.debug("Broadcasting event {} to {} sessions", event.getType(), allSessions.size());
        allSessions.forEach(session -> sendEventToSession(session, event));
    }

    /**
     * Get the number of active WebSocket connections
     */
    public int getActiveConnectionCount() {
        return allSessions.size();
    }

    /**
     * Get the number of sessions subscribed to a specific generation session
     */
    public int getSubscriberCount(String sessionId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionGroups.get(sessionId);
        return sessions != null ? sessions.size() : 0;
    }
}
