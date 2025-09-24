package com.reglisseforge.web.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class WebSocketStatsResponse {
    private int activeConnections;
    private long timestamp;
    
    public static WebSocketStatsResponse create(int activeConnections) {
        return WebSocketStatsResponse.builder()
                .activeConnections(activeConnections)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
