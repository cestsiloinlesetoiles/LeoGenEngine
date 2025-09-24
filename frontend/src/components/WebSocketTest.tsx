import React, { useState, useEffect } from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import { webSocketApi } from '../services/api';

interface TestResult {
  timestamp: number;
  activeConnections: number;
  testBroadcastSent?: boolean;
  message?: string;
  testSessionId?: string;
  subscriptionSuccessful?: boolean;
  webSocketSessionId?: string;
  specificSubscription?: boolean;
  autoSubscribedSessions?: number;
  testEventsScheduled?: boolean;
  subscriberCount?: number;
  chunkedEventsStarted?: boolean;
}

export const WebSocketTest: React.FC = () => {
  const { 
    events, 
    connectionStatus, 
    isConnecting, 
    connect, 
    disconnect, 
    clearEvents,
    subscribeToSession 
  } = useWebSocket();

  const [testResults, setTestResults] = useState<TestResult[]>([]);
  const [isRunningTest, setIsRunningTest] = useState(false);
  const [testSessionId, setTestSessionId] = useState<string>('');

  useEffect(() => {
    // Auto-connect when component mounts
    if (!connectionStatus.connected && !isConnecting) {
      connect();
    }
  }, [connectionStatus.connected, isConnecting, connect]);

  const runConnectionTest = async () => {
    setIsRunningTest(true);
    try {
      const response = await fetch('/api/websocket/test/connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      const result: TestResult = await response.json();
      setTestResults(prev => [result, ...prev]);
      console.log('Connection test result:', result);
    } catch (error) {
      console.error('Connection test failed:', error);
    } finally {
      setIsRunningTest(false);
    }
  };

  const runSubscriptionTest = async () => {
    setIsRunningTest(true);
    try {
      // Get WebSocket session ID
      const webSocketSessionId = (window as any).webSocketService?.getWebSocketSessionId?.() || 
                                 (window as any).webSocketService?.webSocketSessionId || 
                                 null;
      
      console.log('Using WebSocket session ID for test:', webSocketSessionId);

      const params = new URLSearchParams();
      if (webSocketSessionId) {
        params.append('webSocketSessionId', webSocketSessionId);
      }

      const response = await fetch('/api/websocket/test/subscription', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString()
      });
      
      const result: TestResult = await response.json();
      setTestResults(prev => [result, ...prev]);
      setTestSessionId(result.testSessionId || '');
      console.log('Subscription test result:', result);
    } catch (error) {
      console.error('Subscription test failed:', error);
    } finally {
      setIsRunningTest(false);
    }
  };

  const runChunkedEventsTest = async () => {
    if (!testSessionId) {
      alert('Please run subscription test first to get a test session ID');
      return;
    }

    setIsRunningTest(true);
    try {
      const response = await fetch(`/api/websocket/test/chunked-events/${testSessionId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      
      const result: TestResult = await response.json();
      setTestResults(prev => [result, ...prev]);
      console.log('Chunked events test result:', result);
    } catch (error) {
      console.error('Chunked events test failed:', error);
    } finally {
      setIsRunningTest(false);
    }
  };

  const runErrorTest = async () => {
    if (!testSessionId) {
      alert('Please run subscription test first to get a test session ID');
      return;
    }

    try {
      const response = await fetch(`/api/websocket/test/error/${testSessionId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      
      const result = await response.json();
      console.log('Error test result:', result);
    } catch (error) {
      console.error('Error test failed:', error);
    }
  };

  const forceSubscribe = async () => {
    if (!testSessionId) {
      alert('Please run subscription test first to get a test session ID');
      return;
    }

    try {
      const response = await fetch(`/api/websocket/test/force-subscribe/${testSessionId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      
      const result = await response.json();
      setTestResults(prev => [result, ...prev]);
      console.log('Force subscribe result:', result);
    } catch (error) {
      console.error('Force subscribe failed:', error);
    }
  };

  const testManualSubscription = async () => {
    if (!testSessionId) {
      alert('Please run subscription test first to get a test session ID');
      return;
    }

    try {
      await subscribeToSession(testSessionId);
      console.log('Manual subscription successful');
    } catch (error) {
      console.error('Manual subscription failed:', error);
    }
  };

  const getStats = async () => {
    try {
      const [wsStats, testStats] = await Promise.all([
        webSocketApi.getStats(),
        fetch('/api/websocket/test/stats').then(r => r.json())
      ]);
      
      console.log('WebSocket Stats:', wsStats);
      console.log('Test Stats:', testStats);
    } catch (error) {
      console.error('Failed to get stats:', error);
    }
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <h1 className="text-3xl font-bold mb-6">WebSocket Connection Test</h1>
      
      {/* Connection Status */}
      <div className="mb-6 p-4 rounded-lg bg-gray-100">
        <h2 className="text-xl font-semibold mb-2">Connection Status</h2>
        <div className="flex items-center gap-4">
          <div className={`w-3 h-3 rounded-full ${connectionStatus.connected ? 'bg-green-500' : 'bg-red-500'}`}></div>
          <span className="font-medium">
            {connectionStatus.connected ? 'Connected' : 'Disconnected'}
            {isConnecting && ' (Connecting...)'}
          </span>
          {connectionStatus.error && (
            <span className="text-red-600">Error: {connectionStatus.error}</span>
          )}
        </div>
        <div className="mt-2 flex gap-2">
          <button
            onClick={connect}
            disabled={connectionStatus.connected || isConnecting}
            className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400"
          >
            Connect
          </button>
          <button
            onClick={disconnect}
            disabled={!connectionStatus.connected}
            className="px-4 py-2 bg-red-500 text-white rounded disabled:bg-gray-400"
          >
            Disconnect
          </button>
          <button
            onClick={getStats}
            className="px-4 py-2 bg-gray-500 text-white rounded"
          >
            Get Stats
          </button>
        </div>
      </div>

      {/* Test Controls */}
      <div className="mb-6 p-4 rounded-lg bg-blue-50">
        <h2 className="text-xl font-semibold mb-4">Test Controls</h2>
        {testSessionId && (
          <div className="mb-4 p-2 bg-blue-100 rounded">
            <strong>Current Test Session ID:</strong> {testSessionId}
          </div>
        )}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          <button
            onClick={runConnectionTest}
            disabled={isRunningTest}
            className="px-4 py-2 bg-green-500 text-white rounded disabled:bg-gray-400"
          >
            Test Connection
          </button>
          <button
            onClick={runSubscriptionTest}
            disabled={isRunningTest || !connectionStatus.connected}
            className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400"
          >
            Test Subscription
          </button>
          <button
            onClick={runChunkedEventsTest}
            disabled={isRunningTest || !testSessionId}
            className="px-4 py-2 bg-purple-500 text-white rounded disabled:bg-gray-400"
          >
            Test Chunked Events
          </button>
          <button
            onClick={runErrorTest}
            disabled={!testSessionId}
            className="px-4 py-2 bg-red-500 text-white rounded disabled:bg-gray-400"
          >
            Test Error
          </button>
          <button
            onClick={forceSubscribe}
            disabled={!testSessionId}
            className="px-4 py-2 bg-orange-500 text-white rounded disabled:bg-gray-400"
          >
            Force Subscribe
          </button>
          <button
            onClick={testManualSubscription}
            disabled={!testSessionId || !connectionStatus.connected}
            className="px-4 py-2 bg-indigo-500 text-white rounded disabled:bg-gray-400"
          >
            Manual Subscribe
          </button>
          <button
            onClick={clearEvents}
            className="px-4 py-2 bg-gray-500 text-white rounded"
          >
            Clear Events
          </button>
        </div>
      </div>

      {/* Test Results */}
      {testResults.length > 0 && (
        <div className="mb-6 p-4 rounded-lg bg-yellow-50">
          <h2 className="text-xl font-semibold mb-4">Test Results</h2>
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {testResults.map((result, index) => (
              <div key={index} className="p-2 bg-white rounded border text-sm">
                <pre className="whitespace-pre-wrap">{JSON.stringify(result, null, 2)}</pre>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* WebSocket Events */}
      <div className="p-4 rounded-lg bg-gray-50">
        <h2 className="text-xl font-semibold mb-4">WebSocket Events ({events.length})</h2>
        <div className="space-y-2 max-h-96 overflow-y-auto">
          {events.length === 0 ? (
            <p className="text-gray-500">No events received yet</p>
          ) : (
            events.slice().reverse().map((event, index) => (
              <div key={index} className="p-3 bg-white rounded border-l-4 border-blue-500">
                <div className="flex justify-between items-start mb-1">
                  <span className={`px-2 py-1 rounded text-xs font-medium ${
                    event.type === 'ERROR' ? 'bg-red-100 text-red-800' :
                    event.type === 'INFO' ? 'bg-blue-100 text-blue-800' :
                    event.type === 'PROJECT_COMPLETE' ? 'bg-green-100 text-green-800' :
                    'bg-gray-100 text-gray-800'
                  }`}>
                    {event.type}
                  </span>
                  <span className="text-xs text-gray-500">{event.timestamp}</span>
                </div>
                <div className="text-sm">
                  <div className="font-medium">Session: {event.sessionId || 'N/A'}</div>
                  {event.message && <div className="mt-1">{event.message}</div>}
                  {event.data && (
                    <div className="mt-1 p-2 bg-gray-100 rounded text-xs">
                      <pre className="whitespace-pre-wrap">{event.data}</pre>
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
