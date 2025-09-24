package com.reglisseforge.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the fix history tracking system for Leo code generation.
 * Records all versions, errors, and corrections until the final solution.
 */
public class FixHistoryManager {
    private static final Logger logger = LoggerFactory.getLogger(FixHistoryManager.class);
    
    private final ObjectMapper objectMapper;
    private final Path baseFixHistoryDir;
    
    public FixHistoryManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Create fixhistory directory in project root
        this.baseFixHistoryDir = Paths.get(".").toAbsolutePath().normalize().resolve("fixhistory");
        try {
            Files.createDirectories(this.baseFixHistoryDir);
            logger.info("Fix history directory created at: {}", this.baseFixHistoryDir);
        } catch (IOException e) {
            logger.error("Failed to create fix history directory", e);
            throw new RuntimeException("Failed to initialize fix history", e);
        }
    }
    
    /**
     * Initialize a new session history
     */
    public void initializeSession(String sessionId, String projectName, String projectDescription, String workspacePath) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            Files.createDirectories(sessionDir);
            Files.createDirectories(sessionDir.resolve("initial"));
            Files.createDirectories(sessionDir.resolve("attempts"));
            Files.createDirectories(sessionDir.resolve("solution"));
            
            // Create session info
            SessionInfo sessionInfo = SessionInfo.builder()
                    .sessionId(sessionId)
                    .projectName(projectName)
                    .projectDescription(projectDescription)
                    .workspacePath(workspacePath)
                    .startTime(LocalDateTime.now())
                    .status("STARTED")
                    .build();
            
            saveSessionInfo(sessionId, sessionInfo);
            
            logger.info("Initialized fix history session: {}", sessionId);
        } catch (IOException e) {
            logger.error("Failed to initialize session history for: {}", sessionId, e);
        }
    }
    
    /**
     * Record the initial generated code
     */
    public void recordInitialGeneration(String sessionId, Path generatedCodeFile, String generationLog) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            Path initialDir = sessionDir.resolve("initial");
            
            // Copy the generated code
            if (Files.exists(generatedCodeFile)) {
                Files.copy(generatedCodeFile, initialDir.resolve("main.leo"), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Save generation log
            GenerationLog genLog = GenerationLog.builder()
                    .timestamp(LocalDateTime.now())
                    .codeFile("main.leo")
                    .generationDetails(generationLog)
                    .status("GENERATED")
                    .build();
            
            objectMapper.writeValue(initialDir.resolve("generation_log.json").toFile(), genLog);
            
            // Update session status
            updateSessionStatus(sessionId, "INITIAL_GENERATED");
            
            logger.info("Recorded initial generation for session: {}", sessionId);
        } catch (IOException e) {
            logger.error("Failed to record initial generation for session: {}", sessionId, e);
        }
    }
    
    /**
     * Record a correction attempt
     */
    public void recordAttempt(String sessionId, int attemptNumber, Path currentCodeFile, 
                              String buildError, String aiAnalysis, List<String> fixesApplied) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            Path attemptDir = sessionDir.resolve("attempts").resolve(String.format("attempt_%03d", attemptNumber));
            Files.createDirectories(attemptDir);
            
            // Copy current code version
            if (Files.exists(currentCodeFile)) {
                Files.copy(currentCodeFile, attemptDir.resolve("main.leo"), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Save build error
            Files.write(attemptDir.resolve("build_error.txt"), buildError.getBytes());
            
            // Save AI analysis
            AIAnalysis analysis = AIAnalysis.builder()
                    .timestamp(LocalDateTime.now())
                    .attemptNumber(attemptNumber)
                    .errorAnalysis(aiAnalysis)
                    .build();
            objectMapper.writeValue(attemptDir.resolve("ai_analysis.json").toFile(), analysis);
            
            // Save fixes applied
            FixesApplied fixes = FixesApplied.builder()
                    .timestamp(LocalDateTime.now())
                    .attemptNumber(attemptNumber)
                    .fixes(fixesApplied)
                    .build();
            objectMapper.writeValue(attemptDir.resolve("fixes_applied.json").toFile(), fixes);
            
            // Update session status
            updateSessionStatus(sessionId, "ATTEMPT_" + attemptNumber);
            
            logger.info("Recorded attempt {} for session: {}", attemptNumber, sessionId);
        } catch (IOException e) {
            logger.error("Failed to record attempt {} for session: {}", attemptNumber, sessionId, e);
        }
    }
    
    /**
     * Record the final solution
     */
    public void recordSolution(String sessionId, Path solutionCodeFile, String buildSuccessLog, 
                               int totalAttempts, List<String> allErrorsEncountered) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            Path solutionDir = sessionDir.resolve("solution");
            
            // Copy final working code
            if (Files.exists(solutionCodeFile)) {
                Files.copy(solutionCodeFile, solutionDir.resolve("main.leo"), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Save build success log
            Files.write(solutionDir.resolve("build_success.txt"), buildSuccessLog.getBytes());
            
            // Create solution summary
            SolutionSummary summary = SolutionSummary.builder()
                    .timestamp(LocalDateTime.now())
                    .sessionId(sessionId)
                    .totalAttempts(totalAttempts)
                    .allErrorsEncountered(allErrorsEncountered)
                    .finalStatus("SUCCESS")
                    .build();
            objectMapper.writeValue(solutionDir.resolve("summary.json").toFile(), summary);
            
            // Update session status
            updateSessionStatus(sessionId, "COMPLETED_SUCCESS");
            
            logger.info("Recorded successful solution for session: {} after {} attempts", sessionId, totalAttempts);
        } catch (IOException e) {
            logger.error("Failed to record solution for session: {}", sessionId, e);
        }
    }
    
    /**
     * Record a failed session (max attempts reached)
     */
    public void recordFailure(String sessionId, int totalAttempts, List<String> allErrorsEncountered, String lastError) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            Path solutionDir = sessionDir.resolve("solution");
            
            // Create failure summary
            SolutionSummary summary = SolutionSummary.builder()
                    .timestamp(LocalDateTime.now())
                    .sessionId(sessionId)
                    .totalAttempts(totalAttempts)
                    .allErrorsEncountered(allErrorsEncountered)
                    .finalStatus("FAILED")
                    .lastError(lastError)
                    .build();
            objectMapper.writeValue(solutionDir.resolve("summary.json").toFile(), summary);
            
            // Update session status
            updateSessionStatus(sessionId, "COMPLETED_FAILED");
            
            logger.info("Recorded failed session: {} after {} attempts", sessionId, totalAttempts);
        } catch (IOException e) {
            logger.error("Failed to record failure for session: {}", sessionId, e);
        }
    }
    
    /**
     * Get the session directory
     */
    private Path getSessionDir(String sessionId) {
        return baseFixHistoryDir.resolve(sessionId);
    }
    
    /**
     * Save session info
     */
    private void saveSessionInfo(String sessionId, SessionInfo sessionInfo) throws IOException {
        Path sessionInfoFile = getSessionDir(sessionId).resolve("session_info.json");
        objectMapper.writeValue(sessionInfoFile.toFile(), sessionInfo);
    }
    
    /**
     * Update session status
     */
    private void updateSessionStatus(String sessionId, String status) {
        try {
            Path sessionInfoFile = getSessionDir(sessionId).resolve("session_info.json");
            if (Files.exists(sessionInfoFile)) {
                SessionInfo sessionInfo = objectMapper.readValue(sessionInfoFile.toFile(), SessionInfo.class);
                sessionInfo.setStatus(status);
                sessionInfo.setLastUpdated(LocalDateTime.now());
                objectMapper.writeValue(sessionInfoFile.toFile(), sessionInfo);
            }
        } catch (IOException e) {
            logger.error("Failed to update session status for: {}", sessionId, e);
        }
    }
    
    /**
     * Get session history summary
     */
    public Map<String, Object> getSessionSummary(String sessionId) {
        try {
            Path sessionDir = getSessionDir(sessionId);
            if (!Files.exists(sessionDir)) {
                return Map.of("error", "Session not found");
            }
            
            Map<String, Object> summary = new HashMap<>();
            
            // Read session info
            Path sessionInfoFile = sessionDir.resolve("session_info.json");
            if (Files.exists(sessionInfoFile)) {
                SessionInfo sessionInfo = objectMapper.readValue(sessionInfoFile.toFile(), SessionInfo.class);
                summary.put("sessionInfo", sessionInfo);
            }
            
            // Count attempts
            Path attemptsDir = sessionDir.resolve("attempts");
            if (Files.exists(attemptsDir)) {
                long attemptCount = Files.list(attemptsDir).count();
                summary.put("totalAttempts", attemptCount);
            }
            
            // Check if solution exists
            Path solutionDir = sessionDir.resolve("solution");
            boolean hasSolution = Files.exists(solutionDir.resolve("main.leo"));
            summary.put("hasSolution", hasSolution);
            
            return summary;
        } catch (IOException e) {
            logger.error("Failed to get session summary for: {}", sessionId, e);
            return Map.of("error", "Failed to read session data");
        }
    }
    
    // Data classes for JSON serialization
    public static class SessionInfo {
        private String sessionId;
        private String projectName;
        private String projectDescription;
        private String workspacePath;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdated;
        private String status;
        
        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private SessionInfo sessionInfo = new SessionInfo();
            
            public Builder sessionId(String sessionId) {
                sessionInfo.sessionId = sessionId;
                return this;
            }
            
            public Builder projectName(String projectName) {
                sessionInfo.projectName = projectName;
                return this;
            }
            
            public Builder projectDescription(String projectDescription) {
                sessionInfo.projectDescription = projectDescription;
                return this;
            }
            
            public Builder workspacePath(String workspacePath) {
                sessionInfo.workspacePath = workspacePath;
                return this;
            }
            
            public Builder startTime(LocalDateTime startTime) {
                sessionInfo.startTime = startTime;
                return this;
            }
            
            public Builder status(String status) {
                sessionInfo.status = status;
                return this;
            }
            
            public SessionInfo build() {
                return sessionInfo;
            }
        }
        
        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public String getProjectDescription() { return projectDescription; }
        public void setProjectDescription(String projectDescription) { this.projectDescription = projectDescription; }
        
        public String getWorkspacePath() { return workspacePath; }
        public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    public static class GenerationLog {
        private LocalDateTime timestamp;
        private String codeFile;
        private String generationDetails;
        private String status;
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private GenerationLog log = new GenerationLog();
            
            public Builder timestamp(LocalDateTime timestamp) {
                log.timestamp = timestamp;
                return this;
            }
            
            public Builder codeFile(String codeFile) {
                log.codeFile = codeFile;
                return this;
            }
            
            public Builder generationDetails(String generationDetails) {
                log.generationDetails = generationDetails;
                return this;
            }
            
            public Builder status(String status) {
                log.status = status;
                return this;
            }
            
            public GenerationLog build() {
                return log;
            }
        }
        
        // Getters and setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getCodeFile() { return codeFile; }
        public void setCodeFile(String codeFile) { this.codeFile = codeFile; }
        
        public String getGenerationDetails() { return generationDetails; }
        public void setGenerationDetails(String generationDetails) { this.generationDetails = generationDetails; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    public static class AIAnalysis {
        private LocalDateTime timestamp;
        private int attemptNumber;
        private String errorAnalysis;
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private AIAnalysis analysis = new AIAnalysis();
            
            public Builder timestamp(LocalDateTime timestamp) {
                analysis.timestamp = timestamp;
                return this;
            }
            
            public Builder attemptNumber(int attemptNumber) {
                analysis.attemptNumber = attemptNumber;
                return this;
            }
            
            public Builder errorAnalysis(String errorAnalysis) {
                analysis.errorAnalysis = errorAnalysis;
                return this;
            }
            
            public AIAnalysis build() {
                return analysis;
            }
        }
        
        // Getters and setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public int getAttemptNumber() { return attemptNumber; }
        public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
        
        public String getErrorAnalysis() { return errorAnalysis; }
        public void setErrorAnalysis(String errorAnalysis) { this.errorAnalysis = errorAnalysis; }
    }
    
    public static class FixesApplied {
        private LocalDateTime timestamp;
        private int attemptNumber;
        private List<String> fixes;
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private FixesApplied fixesApplied = new FixesApplied();
            
            public Builder timestamp(LocalDateTime timestamp) {
                fixesApplied.timestamp = timestamp;
                return this;
            }
            
            public Builder attemptNumber(int attemptNumber) {
                fixesApplied.attemptNumber = attemptNumber;
                return this;
            }
            
            public Builder fixes(List<String> fixes) {
                fixesApplied.fixes = fixes;
                return this;
            }
            
            public FixesApplied build() {
                return fixesApplied;
            }
        }
        
        // Getters and setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public int getAttemptNumber() { return attemptNumber; }
        public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
        
        public List<String> getFixes() { return fixes; }
        public void setFixes(List<String> fixes) { this.fixes = fixes; }
    }
    
    public static class SolutionSummary {
        private LocalDateTime timestamp;
        private String sessionId;
        private int totalAttempts;
        private List<String> allErrorsEncountered;
        private String finalStatus;
        private String lastError;
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private SolutionSummary summary = new SolutionSummary();
            
            public Builder timestamp(LocalDateTime timestamp) {
                summary.timestamp = timestamp;
                return this;
            }
            
            public Builder sessionId(String sessionId) {
                summary.sessionId = sessionId;
                return this;
            }
            
            public Builder totalAttempts(int totalAttempts) {
                summary.totalAttempts = totalAttempts;
                return this;
            }
            
            public Builder allErrorsEncountered(List<String> allErrorsEncountered) {
                summary.allErrorsEncountered = allErrorsEncountered;
                return this;
            }
            
            public Builder finalStatus(String finalStatus) {
                summary.finalStatus = finalStatus;
                return this;
            }
            
            public Builder lastError(String lastError) {
                summary.lastError = lastError;
                return this;
            }
            
            public SolutionSummary build() {
                return summary;
            }
        }
        
        // Getters and setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        
        public List<String> getAllErrorsEncountered() { return allErrorsEncountered; }
        public void setAllErrorsEncountered(List<String> allErrorsEncountered) { this.allErrorsEncountered = allErrorsEncountered; }
        
        public String getFinalStatus() { return finalStatus; }
        public void setFinalStatus(String finalStatus) { this.finalStatus = finalStatus; }
        
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }
}
