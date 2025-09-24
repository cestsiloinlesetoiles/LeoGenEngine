package com.reglisseforge.web.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class SubscriptionResponse {
    private String message;
    private String sessionId;
    private String webSocketSessionId;
    private boolean success;
    private String error;
    
    public static SubscriptionResponse create(String sessionId, String webSocketSessionId) {
        return SubscriptionResponse.builder()
                .message("Successfully subscribed to generation session")
                .sessionId(sessionId)
                .webSocketSessionId(webSocketSessionId)
                .success(true)
                .build();
    }
    
    public static SubscriptionResponse createError(String sessionId, String webSocketSessionId, String error) {
        return SubscriptionResponse.builder()
                .message("Failed to subscribe to generation session")
                .sessionId(sessionId)
                .webSocketSessionId(webSocketSessionId)
                .success(false)
                .error(error)
                .build();
    }
}
