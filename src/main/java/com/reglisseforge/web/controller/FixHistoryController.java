package com.reglisseforge.web.controller;

import com.reglisseforge.tools.FixHistoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for accessing fix history data
 */
@RestController
@RequestMapping("/api/fixhistory")
@CrossOrigin(origins = "*") // Configure appropriately for production
public class FixHistoryController {
    
    private static final Logger logger = LoggerFactory.getLogger(FixHistoryController.class);
    
    private final FixHistoryManager fixHistoryManager;
    
    public FixHistoryController() {
        this.fixHistoryManager = new FixHistoryManager();
    }
    
    /**
     * Get session history summary
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionHistory(@PathVariable String sessionId) {
        try {
            logger.info("Retrieving fix history for session: {}", sessionId);
            
            Map<String, Object> summary = fixHistoryManager.getSessionSummary(sessionId);
            
            if (summary.containsKey("error")) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve session history for: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve session history: " + e.getMessage()));
        }
    }
    
    /**
     * Health check for fix history system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "fix-history",
                "timestamp", java.time.LocalDateTime.now()
        ));
    }
}
