#!/usr/bin/env bash
# Runs the structural mapper (CubiCasa5k model) on this computer with Docker and publishes it through a temporary
# Cloudflare tunnel, so the API deployed on Render can interpret plans during a live demo.
# Usage: scripts/live-mapper.sh          start and print the address
#        scripts/live-mapper.sh stop     stop the mapper and the tunnel
# Requires Docker Desktop running, with at least 4 GB of memory for containers (the model peaks near 1.7 GB).
set -euo pipefail
cd "$(dirname "$0")/.."

compose() { docker compose --profile live-demo "$@"; }

if [ "${1:-start}" = "stop" ]; then
  compose stop mapper-tunnel structural-mapper
  echo "Mapper and tunnel stopped. Remember that the API keeps pointing to the old address until you change it."
  exit 0
fi

echo "1/3 Starting the structural mapper (the first run builds the image and downloads the model, a few minutes)..."
compose up -d --build structural-mapper

echo "    Waiting until the model is loaded..."
for _ in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8010/health 2>/dev/null | grep -q '"ml_backend":"cubicasa5k-onnx"'; then
    break
  fi
  sleep 2
done
curl -fsS http://127.0.0.1:8010/health | grep -q '"ml_backend":"cubicasa5k-onnx"' || {
  echo "The mapper did not report the model. Check: docker compose logs structural-mapper" >&2
  exit 1
}

echo "2/3 Opening the public tunnel..."
compose up -d mapper-tunnel
url=""
for _ in $(seq 1 60); do
  url=$(compose logs mapper-tunnel 2>/dev/null | grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -n 1 || true)
  [ -n "$url" ] && break
  sleep 2
done
[ -n "$url" ] || { echo "No tunnel address yet. Check: docker compose --profile live-demo logs mapper-tunnel" >&2; exit 1; }

echo "3/3 Checking the mapper through the tunnel..."
for _ in $(seq 1 30); do
  curl -fsS "$url/health" 2>/dev/null | grep -q cubicasa5k-onnx && break
  sleep 2
done
curl -fsS "$url/health" | grep -q cubicasa5k-onnx || { echo "The tunnel does not answer yet: $url" >&2; exit 1; }

cat <<MSG

The model is live at:

    $url

In Render, open rumbo-api-demo > Environment, set
    STRUCTURAL_MAPPER_URL=$url
and choose "Save and deploy". Keep this computer awake and connected while the demo runs.
Stop everything with: scripts/live-mapper.sh stop
MSG
