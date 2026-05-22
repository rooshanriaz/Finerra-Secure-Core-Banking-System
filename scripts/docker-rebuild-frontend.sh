#!/usr/bin/env sh
# Rebuild and replace the running nginx frontend on host port 3001 (container fyp-frontend).
set -e
cd "$(dirname "$0")/.."

echo "Stopping and removing existing fyp-frontend (if any)..."
docker compose rm -fs frontend 2>/dev/null || true

echo "Building frontend image (--no-cache)..."
docker compose build --no-cache frontend

echo "Starting fresh container on http://localhost:3001 ..."
docker compose up -d --no-deps frontend

echo "Done. Open http://localhost:3001 (hard refresh if needed)."
