package com.reglisseforge.web.model;

import lombok.Data;
import lombok.Builder;

/**
 * Response DTO for generation requests
 */
@Data
@Builder
public class GenerationResponse {
    
    private boolean success;
    private String message;
    private String sessionId;
    private String projectPath;
    private String error;
    
    public static GenerationResponse success(String sessionId, String projectPath) {
        return GenerationResponse.builder()
                .success(true)
                .sessionId(sessionId)
                .projectPath(projectPath)
                .message("Generation started successfully")
                .build();
    }
    
    public static GenerationResponse error(String error) {
        return GenerationResponse.builder()
                .success(false)
                .error(error)
                .message("Failed to start generation")
                .build();
    }
}
