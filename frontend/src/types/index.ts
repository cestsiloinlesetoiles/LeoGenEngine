export interface GenerationRequest {
  projectName: string;
  projectDescription: string;
  workspacePath?: string;
  sessionId?: string;
}

export interface GenerationResponse {
  success: boolean;
  message: string;
  sessionId?: string;
  projectPath?: string;
  error?: string;
}

export enum EventType {
  THINKING = 'THINKING',
  GENERATING = 'GENERATING',
  CODE_CHUNK = 'CODE_CHUNK',
  BUILD_STARTED = 'BUILD_STARTED',
  BUILD_SUCCESS = 'BUILD_SUCCESS',
  BUILD_FAILED = 'BUILD_FAILED',
  FIXING_STARTED = 'FIXING_STARTED',
  FIXING_PROGRESS = 'FIXING_PROGRESS',
  FIXING_SUCCESS = 'FIXING_SUCCESS',
  FIXING_FAILED = 'FIXING_FAILED',
  PROJECT_COMPLETE = 'PROJECT_COMPLETE',
  ERROR = 'ERROR',
  INFO = 'INFO',
}

export interface StreamEvent {
  type: EventType;
  sessionId: string;
  message?: string;
  data?: string;
  attempt?: number;
  maxAttempts?: number;
  timestamp: string;
}

export interface ConnectionStatus {
  connected: boolean;
  sessionId?: string;
  error?: string;
}
