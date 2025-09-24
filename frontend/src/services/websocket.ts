import SockJS from 'sockjs-client';
import { StreamEvent, ConnectionStatus } from '../types';
import { webSocketApi } from './api';

export class WebSocketService {
  private socket: WebSocket | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private eventHandlers: ((event: StreamEvent) => void)[] = [];
  private statusHandlers: ((status: ConnectionStatus) => void)[] = [];
  private webSocketSessionId: string | null = null;
  private isConnecting = false;
  private reconnectTimer: NodeJS.Timeout | null = null;

  constructor(private url: string = '/ws/generation') {}

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      // Prevent multiple simultaneous connection attempts
      if (this.isConnecting || this.isConnected()) {
        resolve();
        return;
      }

      this.isConnecting = true;
      
      try {
        // Use SockJS for better compatibility
        const sockjs = new SockJS(`http://localhost:8080${this.url}`);
        this.socket = sockjs as unknown as WebSocket;

        this.socket.onopen = () => {
          console.log('WebSocket connected');
          this.reconnectAttempts = 0;
          this.isConnecting = false;
          // Extract session ID from the SockJS connection
          this.webSocketSessionId = this.extractSessionId(sockjs);
          console.log('WebSocket session ID:', this.webSocketSessionId);
          this.notifyStatusHandlers({ connected: true });
          resolve();
        };

        this.socket.onmessage = (event) => {
          try {
            const receiveTime = new Date().toISOString();
            const streamEvent: StreamEvent = JSON.parse(event.data);
            
            // Log détaillé pour le debugging des chunks
            if (streamEvent.type === 'CODE_CHUNK') {
              console.log(`🔧 [${receiveTime}] CHUNK reçu:`, {
                sessionId: streamEvent.sessionId,
                chunkSize: streamEvent.data?.length || 0,
                timestamp: streamEvent.timestamp,
                receiveTime: receiveTime,
                delay: new Date(receiveTime).getTime() - new Date(streamEvent.timestamp).getTime()
              });
            } else {
              console.log(`📡 [${receiveTime}] Event ${streamEvent.type}:`, {
                sessionId: streamEvent.sessionId,
                message: streamEvent.message,
                timestamp: streamEvent.timestamp,
                receiveTime: receiveTime
              });
            }
            
            this.notifyEventHandlers(streamEvent);
          } catch (error) {
            console.error('Error parsing WebSocket message:', error, event.data);
          }
        };

        this.socket.onclose = () => {
          console.log('WebSocket disconnected');
          this.webSocketSessionId = null;
          this.isConnecting = false;
          this.notifyStatusHandlers({ connected: false });
          this.attemptReconnect();
        };

        this.socket.onerror = (error) => {
          console.error('WebSocket error:', error);
          this.isConnecting = false;
          this.notifyStatusHandlers({ 
            connected: false, 
            error: 'Connection error' 
          });
          reject(error);
        };

      } catch (error) {
        console.error('Failed to create WebSocket connection:', error);
        this.isConnecting = false;
        reject(error);
      }
    });
  }

  disconnect(): void {
    // Clear any pending reconnection attempts
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    
    if (this.socket) {
      this.socket.close();
      this.socket = null;
      this.webSocketSessionId = null;
    }
    
    this.isConnecting = false;
    this.reconnectAttempts = 0;
  }

  private attemptReconnect(): void {
    // Don't reconnect if already connecting or connected
    if (this.isConnecting || this.isConnected()) {
      return;
    }
    
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Attempting to reconnect... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
      
      this.reconnectTimer = setTimeout(() => {
        this.connect().catch(error => {
          console.error('Reconnection failed:', error);
        });
      }, this.reconnectDelay * this.reconnectAttempts);
    } else {
      console.error('Max reconnection attempts reached');
      this.notifyStatusHandlers({ 
        connected: false, 
        error: 'Max reconnection attempts reached' 
      });
    }
  }

  onEvent(handler: (event: StreamEvent) => void): () => void {
    this.eventHandlers.push(handler);
    return () => {
      const index = this.eventHandlers.indexOf(handler);
      if (index > -1) {
        this.eventHandlers.splice(index, 1);
      }
    };
  }

  onStatusChange(handler: (status: ConnectionStatus) => void): () => void {
    this.statusHandlers.push(handler);
    return () => {
      const index = this.statusHandlers.indexOf(handler);
      if (index > -1) {
        this.statusHandlers.splice(index, 1);
      }
    };
  }

  private notifyEventHandlers(event: StreamEvent): void {
    this.eventHandlers.forEach(handler => {
      try {
        handler(event);
      } catch (error) {
        console.error('Error in event handler:', error);
      }
    });
  }

  private notifyStatusHandlers(status: ConnectionStatus): void {
    this.statusHandlers.forEach(handler => {
      try {
        handler(status);
      } catch (error) {
        console.error('Error in status handler:', error);
      }
    });
  }

  isConnected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  getWebSocketSessionId(): string | null {
    return this.webSocketSessionId;
  }

  /**
   * Subscribe this WebSocket to a specific generation session
   */
  async subscribeToGenerationSession(sessionId: string): Promise<boolean> {
    if (!this.webSocketSessionId) {
      console.error('Cannot subscribe: WebSocket session ID not available');
      return false;
    }

    try {
      await webSocketApi.subscribeToSession(sessionId, this.webSocketSessionId);
      console.log(`Successfully subscribed WebSocket session ${this.webSocketSessionId} to generation session ${sessionId}`);
      return true;
    } catch (error) {
      console.error('Failed to subscribe to generation session:', error);
      return false;
    }
  }

  private extractSessionId(sockjs: any): string | null {
    // SockJS provides session ID in the URL path
    try {
      console.log('Attempting to extract WebSocket session ID from SockJS object:', sockjs);
      
      // Method 1: Direct session ID from SockJS
      if (sockjs.id) {
        console.log('Extracted WebSocket session ID from SockJS.id:', sockjs.id);
        return sockjs.id;
      }
      
      // Method 2: From URL (most reliable)
      const url = sockjs.url || sockjs._transport?.url;
      if (url) {
        console.log('Checking URL for session ID:', url);
        const matches = url.match(/\/(\d+)\/([^\/]+)\/(websocket|xhr_streaming)/);
        if (matches && matches[2]) {
          console.log('Extracted WebSocket session ID from URL:', matches[2]);
          return matches[2];
        }
      }
      
      // Method 3: From _transport if available
      if (sockjs._transport && sockjs._transport.url) {
        const transportUrl = sockjs._transport.url;
        console.log('Checking transport URL for session ID:', transportUrl);
        const matches = transportUrl.match(/\/(\d+)\/([^\/]+)\/(websocket|xhr_streaming)/);
        if (matches && matches[2]) {
          console.log('Extracted WebSocket session ID from transport URL:', matches[2]);
          return matches[2];
        }
      }
      
      // Method 4: Check all properties for session-like IDs
      const sessionIdPattern = /^[a-zA-Z0-9_-]{8,}$/;
      for (const [key, value] of Object.entries(sockjs)) {
        if (typeof value === 'string' && sessionIdPattern.test(value) && key.toLowerCase().includes('session')) {
          console.log(`Extracted WebSocket session ID from ${key}:`, value);
          return value;
        }
      }
      
      // Method 5: Generate a fallback ID based on connection timestamp
      const fallbackId = `fallback-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      console.warn('Could not extract WebSocket session ID from SockJS, using fallback:', fallbackId);
      console.log('SockJS object details:', JSON.stringify(sockjs, null, 2));
      return fallbackId;
    } catch (error) {
      console.error('Failed to extract WebSocket session ID:', error);
      const errorFallbackId = `error-${Date.now()}`;
      console.log('Using error fallback ID:', errorFallbackId);
      return errorFallbackId;
    }
  }
}

export const webSocketService = new WebSocketService();

// Make it available globally for testing
(window as any).webSocketService = webSocketService;
