#!/usr/bin/env bash
set -euo pipefail
if [[ -f app.pid ]]; then kill "$(cat app.pid)" 2>/dev/null || true; rm -f app.pid; fi
