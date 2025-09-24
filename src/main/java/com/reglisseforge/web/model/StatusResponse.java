package com.reglisseforge.web.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class StatusResponse {
    private String sessionId;
    private int subscriberCount;
    private String status;
    
    public static StatusResponse create(String sessionId, int subscriberCount) {
        return StatusResponse.builder()
                .sessionId(sessionId)
                .subscriberCount(subscriberCount)
                .status(subscriberCount > 0 ? "active" : "inactive")
                .build();
    }
}
