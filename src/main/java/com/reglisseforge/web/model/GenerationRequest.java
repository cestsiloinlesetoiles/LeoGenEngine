package com.reglisseforge.web.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request DTO for Leo code generation
 */
@Data
public class GenerationRequest {
    
    @NotBlank(message = "Project name is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Project name must start with lowercase letter and contain only lowercase letters, numbers, and underscores")
    private String projectName;
    
    @NotBlank(message = "Project description is required")
    private String projectDescription;
    
    /**
     * Optional workspace path. If not provided, will use default workspace
     */
    private String workspacePath;
    
    /**
     * Session ID to track the generation process
     */
    private String sessionId;
}
