import { useEffect, useState, useCallback } from 'react';
import { webSocketService } from '../services/websocket';
import { webSocketApi } from '../services/api';
import { StreamEvent, ConnectionStatus } from '../types';

export const useWebSocket = () => {
  const [events, setEvents] = useState<StreamEvent[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>({ connected: false });
  const [isConnecting, setIsConnecting] = useState(false);

  const connect = useCallback(async () => {
    if (webSocketService.isConnected() || isConnecting) {
      return;
    }

    setIsConnecting(true);
    try {
      await webSocketService.connect();
    } catch (error) {
      console.error('Failed to connect to WebSocket:', error);
    } finally {
      setIsConnecting(false);
    }
  }, [isConnecting]);

  const disconnect = useCallback(() => {
    webSocketService.disconnect();
  }, []);

  const clearEvents = useCallback(() => {
    setEvents([]);
  }, []);

  const subscribeToSession = useCallback(async (sessionId: string) => {
    const webSocketSessionId = webSocketService.getWebSocketSessionId();
    if (!webSocketSessionId) {
      throw new Error('WebSocket not connected or session ID not available');
    }
    
    try {
      const response = await webSocketApi.subscribeToSession(sessionId, webSocketSessionId);
      console.log('Successfully subscribed to generation session:', response);
      return response;
    } catch (error) {
      console.error('Failed to subscribe to generation session:', error);
      throw error;
    }
  }, []);

  useEffect(() => {
    const unsubscribeEvents = webSocketService.onEvent((event: StreamEvent) => {
      const processTime = new Date().toISOString();
      
      setEvents(prev => {
        // Check for duplicate events based on sessionId, timestamp, and type
        const isDuplicate = prev.some(existingEvent => 
          existingEvent.sessionId === event.sessionId &&
          existingEvent.timestamp === event.timestamp &&
          existingEvent.type === event.type &&
          existingEvent.message === event.message &&
          existingEvent.data === event.data
        );
        
        if (isDuplicate) {
          console.warn('🚫 Duplicate event detected and ignored:', event);
          return prev;
        }
        
        // Log détaillé pour le debugging
        if (event.type === 'CODE_CHUNK') {
          const chunkNumber = prev.filter(e => e.type === 'CODE_CHUNK' && e.sessionId === event.sessionId).length + 1;
          console.log(`📝 [${processTime}] Processing CHUNK #${chunkNumber}:`, {
            sessionId: event.sessionId,
            chunkSize: event.data?.length || 0,
            totalEvents: prev.length,
            timestamp: event.timestamp,
            processTime: processTime
          });
        } else {
          console.log(`🎯 [${processTime}] Processing Event ${event.type}:`, {
            sessionId: event.sessionId,
            message: event.message,
            totalEvents: prev.length,
            timestamp: event.timestamp,
            processTime: processTime
          });
        }
        
        return [...prev, event];
      });
    });

    const unsubscribeStatus = webSocketService.onStatusChange((status: ConnectionStatus) => {
      setConnectionStatus(status);
    });

    return () => {
      unsubscribeEvents();
      unsubscribeStatus();
    };
  }, []);

  return {
    events,
    connectionStatus,
    isConnecting,
    connect,
    disconnect,
    clearEvents,
    subscribeToSession,
  };
};
