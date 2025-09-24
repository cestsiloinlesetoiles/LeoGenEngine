package com.reglisseforge.web.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ConnectionsResponse {
    private int activeConnections;
    
    public static ConnectionsResponse create(int activeConnections) {
        return ConnectionsResponse.builder()
                .activeConnections(activeConnections)
                .build();
    }
}
