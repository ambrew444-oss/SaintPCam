#!/bin/zsh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${1:-8080}"

cd "$SCRIPT_DIR"

echo "Запуск локального сервера на http://127.0.0.1:$PORT"
echo "Оставьте это окно открытым, пока пользуетесь приложением."

python3 -m http.server "$PORT" &
SERVER_PID=$!

sleep 1
open "http://127.0.0.1:$PORT"

cleanup() {
  if kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT INT TERM

wait "$SERVER_PID"
