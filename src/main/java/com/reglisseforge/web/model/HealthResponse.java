package com.reglisseforge.web.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class HealthResponse {
    private String status;
    private long timestamp;
    private int activeConnections;
    
    public static HealthResponse create(int activeConnections) {
        return HealthResponse.builder()
                .status("healthy")
                .timestamp(System.currentTimeMillis())
                .activeConnections(activeConnections)
                .build();
    }
}
