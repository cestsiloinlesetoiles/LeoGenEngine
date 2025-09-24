#!/bin/bash
echo "🚀 Starting LeoForge Backend..."

# Check that the Anthropic API key is set
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "❌ Error: The ANTHROPIC_API_KEY environment variable is not set."
    echo "💡 Please set your API key:"
    echo "   export ANTHROPIC_API_KEY=\"your_api_key_here\""
    exit 1
fi

echo "✅ Anthropic API key detected"

# Stop existing Spring Boot processes on port 8080
echo "🔍 Searching for existing backend processes..."
BACKEND_PID=$(lsof -ti:8080 2>/dev/null)
if [ ! -z "$BACKEND_PID" ]; then
    echo "⚠️  Backend process detected on port 8080 (PID: $BACKEND_PID)"
    echo "🔥 Stopping existing process..."
    kill -TERM $BACKEND_PID 2>/dev/null
    
    # Wait for the process to terminate
    for i in {1..10}; do
        if ! kill -0 $BACKEND_PID 2>/dev/null; then
            echo "✅ Backend process stopped successfully"
            break
        fi
        echo "⏳ Waiting for the process to stop... ($i/10)"
        sleep 1
    done
    
    # Force kill if necessary
    if kill -0 $BACKEND_PID 2>/dev/null; then
        echo "⚡ Force killing backend process..."
        kill -KILL $BACKEND_PID 2>/dev/null
        sleep 2
    fi
else
    echo "✅ No existing backend process detected"
fi

# Stop existing Maven processes for this project
echo "🔍 Searching for existing Maven processes..."
MAVEN_PIDS=$(pgrep -f "maven.*LeoGenEngine" 2>/dev/null)
if [ ! -z "$MAVEN_PIDS" ]; then
    echo "⚠️  Maven processes detected: $MAVEN_PIDS"
    echo "🔥 Stopping existing Maven processes..."
    echo "$MAVEN_PIDS" | xargs kill -TERM 2>/dev/null
    sleep 3
    
    # Force kill if necessary
    REMAINING_MAVEN=$(pgrep -f "maven.*LeoGenEngine" 2>/dev/null)
    if [ ! -z "$REMAINING_MAVEN" ]; then
        echo "⚡ Force killing remaining Maven processes..."
        echo "$REMAINING_MAVEN" | xargs kill -KILL 2>/dev/null
    fi
    echo "✅ Maven processes stopped"
else
    echo "✅ No existing Maven processes detected"
fi

echo "🚀 Starting new backend..."
mvn spring-boot:run -Dspring-boot.run.mainClass="com.reglisseforge.WebApplication"
