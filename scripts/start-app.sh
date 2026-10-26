#!/usr/bin/env bash
# Start target/shop.jar in the background and wait for /health. Fault-injection variables
# (SHOP_CHECKOUT_DELAY_MS, SHOP_FAIL_EVERY) are passed through from the environment.
set -euo pipefail
PORT="${SHOP_PORT:-8080}"
nohup java -Xmx512m -jar target/shop.jar > app.log 2>&1 &
echo $! > app.pid
for _ in $(seq 1 60); do
  if curl -fsS "http://localhost:${PORT}/health" > /dev/null 2>&1; then echo "shop is up on ${PORT}"; exit 0; fi
  sleep 1
done
echo "shop did not start"; cat app.log; exit 1
