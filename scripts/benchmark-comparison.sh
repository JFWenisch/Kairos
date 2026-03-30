#!/usr/bin/env bash
# benchmark-comparison.sh
#
# Compares startup time, memory usage, and endpoint latency between a JVM-based
# and a GraalVM native Docker image of the same application.
#
# Usage: $0 <jvm-image:tag> <native-image:tag> [report-file]
#
# Both images must expose the application on container port 8080.
# The script starts each image sequentially on host port 18090 and measures:
#   - Time from 'docker run' until the first successful /actuator/health response
#   - RSS memory reported by 'docker stats' after the app has settled
#   - Average latency (5 warm requests) for /actuator/health, /, and /api/resources

set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <jvm-image:tag> <native-image:tag> [report-file]"
  exit 2
fi

JVM_IMAGE="$1"
NATIVE_IMAGE="$2"
REPORT_FILE="${3:-benchmark-comparison.md}"

CONTAINER_NAME="kairos-benchmark"
HOST_PORT=18090
BASE_URL="http://127.0.0.1:${HOST_PORT}"

# ── Helpers ───────────────────────────────────────────────────────────────────

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# current_ms: millisecond-precision UNIX timestamp.
# Uses GNU date (+%s%3N) on Linux; falls back to Python for BSD/macOS.
current_ms() {
  date +%s%3N 2>/dev/null || python3 -c 'import time; print(int(time.time() * 1000))'
}

# avg_latency_ms <url> [n]
# Prints the average response time in ms over n requests (default 5).
avg_latency_ms() {
  local url="$1"
  local n="${2:-5}"
  local total=0
  local i=0
  while [ "$i" -lt "$n" ]; do
    local t
    t=$(curl -s -o /dev/null -w '%{time_total}' "$url" || echo "0")
    local ms
    ms=$(awk -v t="$t" 'BEGIN { printf "%d", t * 1000 }')
    total=$(( total + ms ))
    i=$(( i + 1 ))
  done
  echo $(( total / n ))
}

# run_benchmark <image> <label>
# Starts the image, measures startup time, memory, and endpoint latency.
# Results are written to the global variables:
#   _startup_ms  _memory_mb  _health_ms  _root_ms  _api_ms
run_benchmark() {
  local image="$1"
  local label="$2"

  cleanup

  echo ""
  echo "=== [$label] Starting: ${image} ==="
  local start_ts
  start_ts=$(current_ms)
  docker run -d --rm --name "$CONTAINER_NAME" -p "${HOST_PORT}:8080" "$image" >/dev/null

  # Poll /actuator/health until ready or 120 s timeout
  local ready=0
  local tries=0
  while [ "$tries" -lt 240 ]; do
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/actuator/health" || true)
    if [ "$code" = "200" ]; then
      ready=1
      break
    fi
    sleep 0.5
    tries=$(( tries + 1 ))
  done

  if [ "$ready" -ne 1 ]; then
    echo "[$label] Container did not become ready within 120s"
    docker logs "$CONTAINER_NAME" | tail -n 50 || true
    _startup_ms="TIMEOUT"
    _memory_mb="N/A"
    _health_ms="N/A"
    _root_ms="N/A"
    _api_ms="N/A"
    return 0
  fi

  _startup_ms=$(( $(current_ms) - start_ts ))
  echo "[$label] Ready in ${_startup_ms}ms"

  # Allow JVM JIT / GC to settle before measuring memory
  sleep 3

  # Memory: docker stats returns "X.XXMiB / Y.YYGiB"; take the first field
  local mem_raw
  mem_raw=$(docker stats --no-stream --format "{{.MemUsage}}" "$CONTAINER_NAME" | awk '{print $1}')
  case "$mem_raw" in
    *GiB) _memory_mb=$(printf '%s' "$mem_raw" | sed 's/GiB//' | awk '{printf "%d", $1 * 1024}') ;;
    *MiB) _memory_mb=$(printf '%s' "$mem_raw" | sed 's/MiB//' | awk '{printf "%d", $1}') ;;
    *GB)  _memory_mb=$(printf '%s' "$mem_raw" | sed 's/GB//'  | awk '{printf "%d", $1 * 1024}') ;;
    *MB)  _memory_mb=$(printf '%s' "$mem_raw" | sed 's/MB//'  | awk '{printf "%d", $1}') ;;
    *kB)  _memory_mb=$(printf '%s' "$mem_raw" | sed 's/kB//'  | awk '{printf "%d", $1 / 1024}') ;;
    *)    _memory_mb="?" ;;
  esac
  echo "[$label] Memory: ${_memory_mb}MB"

  # Endpoint latency (5-request averages)
  _health_ms=$(avg_latency_ms "${BASE_URL}/actuator/health")
  _root_ms=$(avg_latency_ms "${BASE_URL}/")
  _api_ms=$(avg_latency_ms "${BASE_URL}/api/resources")
  echo "[$label] Latency: health=${_health_ms}ms  root=${_root_ms}ms  api=${_api_ms}ms"
}

# ── Measure both images ───────────────────────────────────────────────────────

run_benchmark "$JVM_IMAGE" "JVM"
jvm_startup="$_startup_ms"
jvm_memory="$_memory_mb"
jvm_health="$_health_ms"
jvm_root="$_root_ms"
jvm_api="$_api_ms"

run_benchmark "$NATIVE_IMAGE" "Native"
native_startup="$_startup_ms"
native_memory="$_memory_mb"
native_health="$_health_ms"
native_root="$_root_ms"
native_api="$_api_ms"

# ── Compute percentage improvements (positive = native outperforms JVM) ───────

improvement() {
  local jvm_val="$1"
  local native_val="$2"
  if [[ "$jvm_val" =~ ^[0-9]+$ ]] && [[ "$native_val" =~ ^[0-9]+$ ]] && [ "$jvm_val" -gt 0 ]; then
    awk -v j="$jvm_val" -v n="$native_val" 'BEGIN { printf "%+.1f%%", (j - n) / j * 100 }'
  else
    echo "N/A"
  fi
}

startup_change=$(improvement "$jvm_startup" "$native_startup")
memory_change=$(improvement "$jvm_memory" "$native_memory")

# ── Write markdown report ─────────────────────────────────────────────────────

{
  echo "# GraalVM Native Image Benchmark Comparison"
  echo ""
  echo "Positive change values indicate the native image outperforms the JVM image."
  echo ""
  echo "## Resource Usage"
  echo ""
  echo "| Metric | JVM | Native | Change |"
  echo "|--------|-----|--------|--------|"
  echo "| Startup time (ms) | ${jvm_startup} | ${native_startup} | ${startup_change} |"
  echo "| Memory usage (MB) | ${jvm_memory} | ${native_memory} | ${memory_change} |"
  echo ""
  echo "## Endpoint Latency (5-request average, ms)"
  echo ""
  echo "| Endpoint | JVM | Native |"
  echo "|----------|-----|--------|"
  echo "| /actuator/health | ${jvm_health} | ${native_health} |"
  echo "| / | ${jvm_root} | ${native_root} |"
  echo "| /api/resources | ${jvm_api} | ${native_api} |"
  echo ""
  echo "---"
  echo "JVM Image: \`${JVM_IMAGE}\`"
  echo "Native Image: \`${NATIVE_IMAGE}\`"
} > "$REPORT_FILE"

cat "$REPORT_FILE"
