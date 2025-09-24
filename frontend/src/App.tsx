import React, { useState, useEffect, useCallback } from 'react';
import MonacoEditor from './components/MonacoEditor';
import EventsPanel from './components/EventsPanel';
import ProjectForm from './components/ProjectForm';
import { WebSocketTest } from './components/WebSocketTest';
import { useWebSocket } from './hooks/useWebSocket';
import { generationApi } from './services/api';
import { GenerationRequest, EventType } from './types';

const App: React.FC = () => {
  const [generatedCode, setGeneratedCode] = useState('');
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [showForm, setShowForm] = useState(true);
  const [processedEventCount, setProcessedEventCount] = useState(0);
  const [currentView, setCurrentView] = useState<'main' | 'test'>('main');
  
  const { events, connectionStatus, connect, clearEvents, subscribeToSession } = useWebSocket();

  // Connect to WebSocket on component mount
  useEffect(() => {
    connect();
  }, [connect]);

  // Handle incoming events
  useEffect(() => {
    if (events.length <= processedEventCount) return;

    // Process only new events
    const newEvents = events.slice(processedEventCount);
    
    newEvents.forEach(event => {
      // Only process events for the current session
      if (currentSessionId && event.sessionId !== currentSessionId) {
        return;
      }

      // Accumulate code chunks
      if (event.type === EventType.CODE_CHUNK && event.data) {
        setGeneratedCode(prev => {
          const newCode = prev + event.data;
          const chunkNumber = newEvents.filter(e => e.type === EventType.CODE_CHUNK).indexOf(event) + 1;
          console.log(`💻 [${new Date().toISOString()}] Code accumulated - Chunk #${chunkNumber}:`, {
            chunkSize: event.data.length,
            totalCodeLength: newCode.length,
            sessionId: event.sessionId,
            timestamp: event.timestamp
          });
          return newCode;
        });
      }

      // Handle generation completion
      if (event.type === EventType.PROJECT_COMPLETE) {
        setIsGenerating(false);
      }

      // Handle errors
      if (event.type === EventType.ERROR) {
        setIsGenerating(false);
      }
    });

    // Update processed count
    setProcessedEventCount(events.length);
  }, [events, processedEventCount, currentSessionId]);

  const startGeneration = useCallback(async (request: GenerationRequest) => {
    try {
      setIsGenerating(true);
      setShowForm(false);
      setGeneratedCode('');
      clearEvents();
      setProcessedEventCount(0); // Reset processed event count

      // Ensure WebSocket is connected before starting generation
      if (!connectionStatus.connected) {
        console.log('WebSocket not connected, attempting to connect...');
        await connect();
        
        // Wait a bit for the connection to stabilize
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      const response = await generationApi.startGeneration(request);
      
      if (response.success && response.sessionId) {
        setCurrentSessionId(response.sessionId);
        
        // Subscribe WebSocket to the generation session
        try {
          await subscribeToSession(response.sessionId);
          console.log('WebSocket subscribed to generation session:', response.sessionId);
        } catch (subscriptionError) {
          console.warn('Failed to subscribe WebSocket to session, but continuing:', subscriptionError);
          // Don't fail the entire generation process if subscription fails
        }
      } else {
        throw new Error(response.error || 'Failed to start generation');
      }
    } catch (error) {
      console.error('Failed to start generation:', error);
      setIsGenerating(false);
      alert(`Failed to start generation: ${error}`);
    }
  }, [clearEvents, connectionStatus.connected, connect, subscribeToSession]);

  const resetToForm = useCallback(() => {
    setShowForm(true);
    setGeneratedCode('');
    setCurrentSessionId(null);
    setIsGenerating(false);
    clearEvents();
  }, [clearEvents]);

  const handleCodeChange = useCallback((value: string) => {
    setGeneratedCode(value);
  }, []);

  // Check URL for test mode
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('test') === 'true') {
      setCurrentView('test');
    }
  }, []);

  // Render test page if in test mode
  if (currentView === 'test') {
    return (
      <div className="min-h-screen bg-gray-100">
        <div className="bg-white shadow-sm border-b">
          <div className="max-w-6xl mx-auto px-4 py-3 flex justify-between items-center">
            <h1 className="text-2xl font-bold text-gray-800">WebSocket Test Suite</h1>
            <button
              onClick={() => {
                setCurrentView('main');
                window.history.replaceState({}, '', window.location.pathname);
              }}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              Back to Main App
            </button>
          </div>
        </div>
        <WebSocketTest />
      </div>
    );
  }

  if (showForm) {
    return (
      <div>
        <div className="absolute top-4 right-4 z-10">
          <button
            onClick={() => setCurrentView('test')}
            className="px-3 py-2 bg-gray-600 text-white text-sm rounded hover:bg-gray-700 transition-colors"
          >
            WebSocket Test
          </button>
        </div>
        <ProjectForm
          onSubmit={startGeneration}
          isLoading={isGenerating}
          isConnected={connectionStatus.connected}
        />
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-leo-dark">
      {/* Header */}
      <div className="bg-leo-gray border-b border-gray-600 px-4 py-2 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1 className="text-xl font-bold text-white">LeoForge</h1>
          {currentSessionId && (
            <span className="text-sm text-gray-400">
              Session: {currentSessionId.slice(0, 8)}...
            </span>
          )}
        </div>
        
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${connectionStatus.connected ? 'bg-green-400' : 'bg-red-400'}`}></div>
            <span className="text-sm text-gray-400">
              {connectionStatus.connected ? 'Connected' : 'Disconnected'}
            </span>
          </div>
          
          {isGenerating && (
            <div className="flex items-center gap-2 text-yellow-400">
              <div className="animate-spin w-4 h-4 border-2 border-yellow-400 border-t-transparent rounded-full"></div>
              <span className="text-sm">Generating...</span>
            </div>
          )}
          
          <button
            onClick={resetToForm}
            className="px-3 py-1 bg-gray-600 hover:bg-gray-700 text-white text-sm rounded transition-colors"
          >
            New Project
          </button>
        </div>
      </div>

      {/* Main content */}
      <div className="flex-1 flex">
        {/* Events panel - left side */}
        <div className="w-1/3 border-r border-gray-600">
          <EventsPanel events={events} onClear={clearEvents} />
        </div>

        {/* Code editor - right side */}
        <div className="flex-1 flex flex-col">
          <div className="bg-leo-gray px-4 py-2 border-b border-gray-600">
            <h2 className="text-lg font-semibold text-white">Generated Leo Code</h2>
            <p className="text-sm text-gray-400">
              Code appears here in real-time as it's generated
            </p>
          </div>
          
          <div className="flex-1">
            <MonacoEditor
              value={generatedCode}
              onChange={handleCodeChange}
              language="leo"
              theme="vs-dark"
              options={{
                readOnly: isGenerating,
                wordWrap: 'on',
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                fontSize: 14,
                fontFamily: 'Monaco, Menlo, Ubuntu Mono, monospace',
              }}
            />
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="bg-leo-gray border-t border-gray-600 px-4 py-2 flex items-center justify-between text-sm text-gray-400">
        <div>
          Events: {events.length} | Code: {generatedCode.length} chars
        </div>
        <div>
          LeoForge - AI-Powered Leo Code Generation
        </div>
      </div>
    </div>
  );
};

export default App;
