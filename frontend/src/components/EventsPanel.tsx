import React from 'react';
import { StreamEvent, EventType } from '../types';

interface EventsPanelProps {
  events: StreamEvent[];
  onClear: () => void;
}

const EventsPanel: React.FC<EventsPanelProps> = ({ events, onClear }) => {
  const getEventIcon = (type: EventType): string => {
    switch (type) {
      case EventType.THINKING:
        return '🤔';
      case EventType.GENERATING:
        return '⚡';
      case EventType.CODE_CHUNK:
        return '📝';
      case EventType.BUILD_STARTED:
        return '🔨';
      case EventType.BUILD_SUCCESS:
        return '✅';
      case EventType.BUILD_FAILED:
        return '❌';
      case EventType.FIXING_STARTED:
        return '🔄';
      case EventType.FIXING_PROGRESS:
        return '⚙️';
      case EventType.FIXING_SUCCESS:
        return '✅';
      case EventType.FIXING_FAILED:
        return '❌';
      case EventType.PROJECT_COMPLETE:
        return '🎉';
      case EventType.ERROR:
        return '🚨';
      case EventType.INFO:
        return 'ℹ️';
      default:
        return '📄';
    }
  };

  const getEventColor = (type: EventType): string => {
    switch (type) {
      case EventType.THINKING:
        return 'text-yellow-400';
      case EventType.GENERATING:
        return 'text-blue-400';
      case EventType.CODE_CHUNK:
        return 'text-gray-400';
      case EventType.BUILD_STARTED:
        return 'text-orange-400';
      case EventType.BUILD_SUCCESS:
      case EventType.FIXING_SUCCESS:
      case EventType.PROJECT_COMPLETE:
        return 'text-green-400';
      case EventType.BUILD_FAILED:
      case EventType.FIXING_FAILED:
      case EventType.ERROR:
        return 'text-red-400';
      case EventType.FIXING_STARTED:
      case EventType.FIXING_PROGRESS:
        return 'text-cyan-400';
      case EventType.INFO:
        return 'text-gray-300';
      default:
        return 'text-gray-400';
    }
  };

  const formatTimestamp = (timestamp: string): string => {
    return new Date(timestamp).toLocaleTimeString();
  };

  const renderEventContent = (event: StreamEvent): React.ReactNode => {
    if (event.type === EventType.CODE_CHUNK) {
      // Don't show code chunks in events panel to avoid spam
      return null;
    }

    return (
      <div key={`${event.sessionId}-${event.timestamp}-${event.type}`} className="mb-2 p-2 rounded bg-leo-gray">
        <div className="flex items-start gap-2">
          <span className="text-lg">{getEventIcon(event.type)}</span>
          <div className="flex-1 min-w-0">
            <div className={`font-medium ${getEventColor(event.type)}`}>
              {event.message}
            </div>
            {event.data && (
              <div className="mt-1 p-2 bg-black rounded text-xs font-mono text-gray-300 overflow-x-auto">
                <pre className="whitespace-pre-wrap">{event.data}</pre>
              </div>
            )}
            {(event.attempt !== undefined || event.maxAttempts !== undefined) && (
              <div className="text-xs text-gray-500 mt-1">
                {event.attempt && event.maxAttempts && 
                  `Attempt ${event.attempt}/${event.maxAttempts}`}
              </div>
            )}
            <div className="text-xs text-gray-500 mt-1">
              {formatTimestamp(event.timestamp)}
            </div>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="h-full flex flex-col bg-leo-dark">
      <div className="flex items-center justify-between p-4 border-b border-gray-600">
        <h2 className="text-lg font-semibold text-white">Generation Events</h2>
        <button
          onClick={onClear}
          className="px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
        >
          Clear
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-2">
        {events.length === 0 ? (
          <div className="text-center text-gray-500 mt-8">
            No events yet. Start a generation to see real-time updates.
          </div>
        ) : (
          events.map((event, index) => (
            <React.Fragment key={`event-${index}-${event.timestamp}-${event.type}`}>
              {renderEventContent(event)}
            </React.Fragment>
          ))
        )}
      </div>
    </div>
  );
};

export default EventsPanel;
