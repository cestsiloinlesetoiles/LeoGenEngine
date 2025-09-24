#!/bin/bash
echo "⚛️  Starting LeoForge Frontend..."

# Stop existing Vite/Node processes on ports 5173/5174
echo "🔍 Searching for existing frontend processes..."

# Check common ports used by Vite (5173, 5174, 3000)
FRONTEND_PORTS=(5173 5174 3000 4173)
for port in "${FRONTEND_PORTS[@]}"; do
    FRONTEND_PID=$(lsof -ti:$port 2>/dev/null)
    if [ ! -z "$FRONTEND_PID" ]; then
        echo "⚠️  Frontend process detected on port $port (PID: $FRONTEND_PID)"
        echo "🔥 Stopping existing process..."
        kill -TERM $FRONTEND_PID 2>/dev/null
        
        # Wait for the process to terminate
        for i in {1..5}; do
            if ! kill -0 $FRONTEND_PID 2>/dev/null; then
                echo "✅ Frontend process on port $port stopped successfully"
                break
            fi
            echo "⏳ Waiting for the process to stop... ($i/5)"
            sleep 1
        done
        
        # Force kill if necessary
        if kill -0 $FRONTEND_PID 2>/dev/null; then
            echo "⚡ Force killing frontend process..."
            kill -KILL $FRONTEND_PID 2>/dev/null
            sleep 1
        fi
    fi
done

# Stop existing Node/NPM processes for this project
echo "🔍 Searching for existing Node/NPM processes..."
NODE_PIDS=$(pgrep -f "node.*vite\|npm.*dev\|vite.*dev" 2>/dev/null)
if [ ! -z "$NODE_PIDS" ]; then
    echo "⚠️  Node/NPM processes detected: $NODE_PIDS"
    echo "🔥 Stopping existing Node/NPM processes..."
    echo "$NODE_PIDS" | xargs kill -TERM 2>/dev/null
    sleep 2
    
    # Force kill if necessary
    REMAINING_NODE=$(pgrep -f "node.*vite\|npm.*dev\|vite.*dev" 2>/dev/null)
    if [ ! -z "$REMAINING_NODE" ]; then
        echo "⚡ Force killing remaining Node processes..."
        echo "$REMAINING_NODE" | xargs kill -KILL 2>/dev/null
    fi
    echo "✅ Node/NPM processes stopped"
else
    echo "✅ No existing Node/NPM processes detected"
fi

echo "🚀 Starting new frontend..."
cd frontend
npm run dev
