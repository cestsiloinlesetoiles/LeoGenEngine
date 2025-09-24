package com.reglisseforge.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * WebSocket event for streaming Leo code generation
 */
@Data
@Builder
public class StreamEvent {
    
    public enum EventType {
        THINKING,          // AI is thinking about the problem
        GENERATING,        // Code generation in progress
        CODE_CHUNK,        // Piece of generated code
        BUILD_STARTED,     // Leo build process started
        BUILD_SUCCESS,     // Leo build succeeded
        BUILD_FAILED,      // Leo build failed
        FIXING_STARTED,    // Auto-correction started
        FIXING_PROGRESS,   // Auto-correction in progress
        FIXING_SUCCESS,    // Auto-correction succeeded
        FIXING_FAILED,     // Auto-correction failed
        PROJECT_COMPLETE,  // Entire project generation completed
        ERROR,             // General error occurred
        INFO               // General information message
    }
    
    private EventType type;
    private String sessionId;
    private String message;
    private String data;           // Optional data payload (e.g., code chunk, error details)
    private Integer attempt;       // For correction attempts
    private Integer maxAttempts;   // Maximum correction attempts
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    public static StreamEvent thinking(String sessionId, String message) {
        return StreamEvent.builder()
                .type(EventType.THINKING)
                .sessionId(sessionId)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent generating(String sessionId, String message) {
        return StreamEvent.builder()
                .type(EventType.GENERATING)
                .sessionId(sessionId)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent codeChunk(String sessionId, String chunk) {
        return StreamEvent.builder()
                .type(EventType.CODE_CHUNK)
                .sessionId(sessionId)
                .data(chunk)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent buildStarted(String sessionId) {
        return StreamEvent.builder()
                .type(EventType.BUILD_STARTED)
                .sessionId(sessionId)
                .message("Starting Leo build...")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent buildSuccess(String sessionId) {
        return StreamEvent.builder()
                .type(EventType.BUILD_SUCCESS)
                .sessionId(sessionId)
                .message("✅ Leo build succeeded!")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent buildFailed(String sessionId, String errorOutput) {
        return StreamEvent.builder()
                .type(EventType.BUILD_FAILED)
                .sessionId(sessionId)
                .message("❌ Leo build failed")
                .data(errorOutput)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent fixingStarted(String sessionId, int attempt, int maxAttempts) {
        return StreamEvent.builder()
                .type(EventType.FIXING_STARTED)
                .sessionId(sessionId)
                .message(String.format("🔄 Starting auto-correction attempt %d/%d", attempt, maxAttempts))
                .attempt(attempt)
                .maxAttempts(maxAttempts)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent fixingProgress(String sessionId, String message, int attempt) {
        return StreamEvent.builder()
                .type(EventType.FIXING_PROGRESS)
                .sessionId(sessionId)
                .message(message)
                .attempt(attempt)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent fixingSuccess(String sessionId, int attempt) {
        return StreamEvent.builder()
                .type(EventType.FIXING_SUCCESS)
                .sessionId(sessionId)
                .message(String.format("✅ Auto-correction succeeded on attempt %d!", attempt))
                .attempt(attempt)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent fixingFailed(String sessionId, int maxAttempts) {
        return StreamEvent.builder()
                .type(EventType.FIXING_FAILED)
                .sessionId(sessionId)
                .message(String.format("❌ Auto-correction failed after %d attempts", maxAttempts))
                .maxAttempts(maxAttempts)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent projectComplete(String sessionId, String projectPath) {
        return StreamEvent.builder()
                .type(EventType.PROJECT_COMPLETE)
                .sessionId(sessionId)
                .message("🎉 Project generation completed successfully!")
                .data(projectPath)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent error(String sessionId, String error) {
        return StreamEvent.builder()
                .type(EventType.ERROR)
                .sessionId(sessionId)
                .message("❌ Error occurred")
                .data(error)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static StreamEvent info(String sessionId, String message) {
        return StreamEvent.builder()
                .type(EventType.INFO)
                .sessionId(sessionId)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
