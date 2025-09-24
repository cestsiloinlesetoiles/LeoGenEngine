#!/bin/bash
echo "🚀 Starting LeoForge Backend..."

# Vérifier que la clé API Anthropic est définie
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "❌ Erreur: La variable d'environnement ANTHROPIC_API_KEY n'est pas définie."
    echo "💡 Veuillez définir votre clé API:"
    echo "   export ANTHROPIC_API_KEY=\"votre_clé_api_ici\""
    exit 1
fi

echo "✅ Clé API Anthropic détectée"

# Arrêter les processus Spring Boot existants sur le port 8080
echo "🔍 Recherche de processus backend existants..."
BACKEND_PID=$(lsof -ti:8080 2>/dev/null)
if [ ! -z "$BACKEND_PID" ]; then
    echo "⚠️  Processus backend détecté sur le port 8080 (PID: $BACKEND_PID)"
    echo "🔥 Arrêt du processus existant..."
    kill -TERM $BACKEND_PID 2>/dev/null
    
    # Attendre que le processus se termine
    for i in {1..10}; do
        if ! kill -0 $BACKEND_PID 2>/dev/null; then
            echo "✅ Processus backend arrêté avec succès"
            break
        fi
        echo "⏳ Attente de l'arrêt du processus... ($i/10)"
        sleep 1
    done
    
    # Force kill si nécessaire
    if kill -0 $BACKEND_PID 2>/dev/null; then
        echo "⚡ Force kill du processus backend..."
        kill -KILL $BACKEND_PID 2>/dev/null
        sleep 2
    fi
else
    echo "✅ Aucun processus backend existant détecté"
fi

# Arrêter les processus Maven existants pour ce projet
echo "🔍 Recherche de processus Maven existants..."
MAVEN_PIDS=$(pgrep -f "maven.*LeoGenEngine" 2>/dev/null)
if [ ! -z "$MAVEN_PIDS" ]; then
    echo "⚠️  Processus Maven détectés: $MAVEN_PIDS"
    echo "🔥 Arrêt des processus Maven existants..."
    echo "$MAVEN_PIDS" | xargs kill -TERM 2>/dev/null
    sleep 3
    
    # Force kill si nécessaire
    REMAINING_MAVEN=$(pgrep -f "maven.*LeoGenEngine" 2>/dev/null)
    if [ ! -z "$REMAINING_MAVEN" ]; then
        echo "⚡ Force kill des processus Maven restants..."
        echo "$REMAINING_MAVEN" | xargs kill -KILL 2>/dev/null
    fi
    echo "✅ Processus Maven arrêtés"
else
    echo "✅ Aucun processus Maven existant détecté"
fi

echo "🚀 Démarrage du nouveau backend..."
mvn spring-boot:run -Dspring-boot.run.mainClass="com.reglisseforge.WebApplication"
