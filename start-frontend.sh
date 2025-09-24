#!/bin/bash
echo "⚛️  Starting LeoForge Frontend..."

# Arrêter les processus Vite/Node existants sur les ports 5173/5174
echo "🔍 Recherche de processus frontend existants..."

# Vérifier les ports couramment utilisés par Vite (5173, 5174, 3000)
FRONTEND_PORTS=(5173 5174 3000 4173)
for port in "${FRONTEND_PORTS[@]}"; do
    FRONTEND_PID=$(lsof -ti:$port 2>/dev/null)
    if [ ! -z "$FRONTEND_PID" ]; then
        echo "⚠️  Processus frontend détecté sur le port $port (PID: $FRONTEND_PID)"
        echo "🔥 Arrêt du processus existant..."
        kill -TERM $FRONTEND_PID 2>/dev/null
        
        # Attendre que le processus se termine
        for i in {1..5}; do
            if ! kill -0 $FRONTEND_PID 2>/dev/null; then
                echo "✅ Processus frontend sur le port $port arrêté avec succès"
                break
            fi
            echo "⏳ Attente de l'arrêt du processus... ($i/5)"
            sleep 1
        done
        
        # Force kill si nécessaire
        if kill -0 $FRONTEND_PID 2>/dev/null; then
            echo "⚡ Force kill du processus frontend..."
            kill -KILL $FRONTEND_PID 2>/dev/null
            sleep 1
        fi
    fi
done

# Arrêter les processus Node/NPM existants pour ce projet
echo "🔍 Recherche de processus Node/NPM existants..."
NODE_PIDS=$(pgrep -f "node.*vite\|npm.*dev\|vite.*dev" 2>/dev/null)
if [ ! -z "$NODE_PIDS" ]; then
    echo "⚠️  Processus Node/NPM détectés: $NODE_PIDS"
    echo "🔥 Arrêt des processus Node/NPM existants..."
    echo "$NODE_PIDS" | xargs kill -TERM 2>/dev/null
    sleep 2
    
    # Force kill si nécessaire
    REMAINING_NODE=$(pgrep -f "node.*vite\|npm.*dev\|vite.*dev" 2>/dev/null)
    if [ ! -z "$REMAINING_NODE" ]; then
        echo "⚡ Force kill des processus Node restants..."
        echo "$REMAINING_NODE" | xargs kill -KILL 2>/dev/null
    fi
    echo "✅ Processus Node/NPM arrêtés"
else
    echo "✅ Aucun processus Node/NPM existant détecté"
fi

echo "🚀 Démarrage du nouveau frontend..."
cd frontend
npm run dev
