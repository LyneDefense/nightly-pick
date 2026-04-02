#!/usr/bin/env bash
set -euo pipefail

if [ ! -f .env ]; then
  echo ".env file not found. Start from .env.minimax.example or .env.example"
  exit 1
fi

uvicorn app.main:app --host 0.0.0.0 --port 8000
