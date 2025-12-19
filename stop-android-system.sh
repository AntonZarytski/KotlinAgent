#!/bin/bash

# Stop Android Agent System

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "🛑 Stopping Android Agent System..."
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}✅${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ️${NC}  $1"
}

# Stop local agent
if [ -f "logs/local-agent.pid" ]; then
    LOCAL_AGENT_PID=$(cat logs/local-agent.pid)
    if ps -p $LOCAL_AGENT_PID > /dev/null 2>&1; then
        kill $LOCAL_AGENT_PID
        print_status "Local agent stopped (PID: $LOCAL_AGENT_PID)"
    else
        print_info "Local agent was not running"
    fi
    rm logs/local-agent.pid
else
    # Try to find and kill by name
    pkill -f "localAgentClient.jar" && print_status "Local agent stopped" || print_info "Local agent was not running"
fi

# Stop remote server
if [ -f "logs/server.pid" ]; then
    SERVER_PID=$(cat logs/server.pid)
    if ps -p $SERVER_PID > /dev/null 2>&1; then
        kill $SERVER_PID
        print_status "Remote server stopped (PID: $SERVER_PID)"
    else
        print_info "Remote server was not running"
    fi
    rm logs/server.pid
else
    # Try to find and kill by port
    SERVER_PID=$(lsof -ti:8443)
    if [ -n "$SERVER_PID" ]; then
        kill $SERVER_PID
        print_status "Remote server stopped (PID: $SERVER_PID)"
    else
        print_info "Remote server was not running"
    fi
fi

echo ""
print_status "System stopped"

