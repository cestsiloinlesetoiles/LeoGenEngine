import axios from 'axios';
import { GenerationRequest, GenerationResponse } from '../types';

const API_BASE_URL = '/api';

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

export const generationApi = {
  startGeneration: async (request: GenerationRequest): Promise<GenerationResponse> => {
    const response = await api.post('/generation/start', request);
    return response.data;
  },

  getStatus: async (sessionId: string) => {
    const response = await api.get(`/generation/status/${sessionId}`);
    return response.data;
  },

  getConnectionsInfo: async () => {
    const response = await api.get('/generation/connections');
    return response.data;
  },

  healthCheck: async () => {
    const response = await api.get('/generation/health');
    return response.data;
  },
};

export const webSocketApi = {
  subscribeToSession: async (sessionId: string, webSocketSessionId: string) => {
    const response = await api.post(`/websocket/subscribe/${sessionId}`, null, {
      params: { webSocketSessionId }
    });
    return response.data;
  },

  getStats: async () => {
    const response = await api.get('/websocket/stats');
    return response.data;
  },
};
