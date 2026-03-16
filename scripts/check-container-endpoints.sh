#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <image:tag> <host-port>"
  exit 2
fi

IMAGE="$1"
HOST_PORT="$2"
CONTAINER_NAME="kairos-endpoint-check"
BASE_URL="http://127.0.0.1:${HOST_PORT}"
REPORT_FILE="container-endpoint-latency.md"

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup

echo "Starting container ${IMAGE} on ${BASE_URL}"
docker run -d --rm --name "$CONTAINER_NAME" -p "${HOST_PORT}:8080" "$IMAGE" >/dev/null

# Wait for app startup (up to 120s)
startup_deadline=$((SECONDS + 120))
ready=0
while [ "$SECONDS" -lt "$startup_deadline" ]; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/api/resources" || true)
  if [ "$code" = "200" ]; then
    ready=1
    break
  fi
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  echo "Container did not become ready in time"
  docker logs "$CONTAINER_NAME" | tail -n 200 || true
  {
    echo "# Container Endpoint Check"
    echo
    echo "Status: FAILED"
    echo
    echo "Reason: Service did not become ready within 120 seconds"
  } > "$REPORT_FILE"
  exit 1
fi

check_endpoint_latency() {
  local endpoint="$1"
  local threshold_ms="$2"

  local total_ms=0
  local status=""
  local request_ms=0

  for _ in 1 2 3; do
    out=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' "${BASE_URL}${endpoint}")
    status=$(echo "$out" | awk '{print $1}')
    seconds=$(echo "$out" | awk '{print $2}')
    request_ms=$(awk -v t="$seconds" 'BEGIN { printf("%d", t * 1000) }')
    total_ms=$((total_ms + request_ms))
  done

  avg_ms=$((total_ms / 3))

  if [[ ! "$status" =~ ^2[0-9][0-9]$ && ! "$status" =~ ^3[0-9][0-9]$ ]]; then
    echo "${endpoint}|${status}|${avg_ms}|${threshold_ms}|FAIL"
    return 1
  fi

  if [ "$avg_ms" -gt "$threshold_ms" ]; then
    echo "${endpoint}|${status}|${avg_ms}|${threshold_ms}|FAIL"
    return 1
  fi

  echo "${endpoint}|${status}|${avg_ms}|${threshold_ms}|PASS"
  return 0
}

results=()
overall_fail=0

results+=("$(check_endpoint_latency "/" 2000 || overall_fail=1)")
results+=("$(check_endpoint_latency "/api/resources" 1500 || overall_fail=1)")
results+=("$(check_endpoint_latency "/actuator/health" 1200 || overall_fail=1)")

{
  echo "# Container Endpoint Check"
  echo
  echo "Image: ${IMAGE}"
  echo
  echo "| Endpoint | HTTP | Avg Latency (ms) | Threshold (ms) | Result |"
  echo "|---|---:|---:|---:|---|"
  for row in "${results[@]}"; do
    endpoint=$(echo "$row" | cut -d'|' -f1)
    status=$(echo "$row" | cut -d'|' -f2)
    avg=$(echo "$row" | cut -d'|' -f3)
    threshold=$(echo "$row" | cut -d'|' -f4)
    result=$(echo "$row" | cut -d'|' -f5)
    echo "| ${endpoint} | ${status} | ${avg} | ${threshold} | ${result} |"
  done
  echo
  if [ "$overall_fail" -eq 0 ]; then
    echo "Status: PASSED"
  else
    echo "Status: FAILED"
  fi
} > "$REPORT_FILE"

cat "$REPORT_FILE"

if [ "$overall_fail" -ne 0 ]; then
  echo "Container endpoint check failed"
  docker logs "$CONTAINER_NAME" | tail -n 200 || true
  exit 1
fi
