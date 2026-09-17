#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -x "./gradlew" ]]; then
  echo "Gradle wrapper is missing or not executable: ${ROOT_DIR}/gradlew" >&2
  exit 1
fi

ensure_local_google_maven_proxy() {
  if [[ -n "${ANDROIDSA_GOOGLE_MAVEN_URL:-}" ]]; then
    echo "Using configured AndroidSA Google Maven mirror: ${ANDROIDSA_GOOGLE_MAVEN_URL}"
    return 0
  fi

  if python3 - <<'PY'
import socket
import urllib.request

try:
    socket.getaddrinfo("dl.google.com", 443)
    with urllib.request.urlopen("https://dl.google.com/android/repository/repository2-1.xml", timeout=15) as response:
        if response.status >= 400:
            raise RuntimeError(f"Unexpected HTTP status: {response.status}")
except Exception:
    raise SystemExit(1)
raise SystemExit(0)
PY
  then
    echo "Google Maven is reachable; no proxy fallback required."
    return 0
  fi

  PROXY_PORT="38473"
  PROXY_URL="http://127.0.0.1:${PROXY_PORT}/"
  PROXY_LOG="/tmp/androidsa-google-maven-proxy.log"

  if [[ -f "${PROXY_LOG}" ]]; then
    rm -f "${PROXY_LOG}"
  fi

  nohup python3 tools/google_maven_proxy.py --host 127.0.0.1 --port "${PROXY_PORT}" >"${PROXY_LOG}" 2>&1 &
  echo $! > /tmp/androidsa-google-maven-proxy.pid

  for attempt in $(seq 1 30); do
    if curl --silent --show-error --fail "${PROXY_URL}" >/dev/null 2>&1; then
      export ANDROIDSA_GOOGLE_MAVEN_URL="${PROXY_URL}"
      echo "Started local Google Maven proxy and exported ANDROIDSA_GOOGLE_MAVEN_URL=${ANDROIDSA_GOOGLE_MAVEN_URL}"
      return 0
    fi
    sleep 1
  done

  echo "Local Google Maven proxy did not become ready. Logs:"
  cat "${PROXY_LOG}" || true
  exit 1
}

ensure_local_google_maven_proxy

echo "[1/5] Warming Gradle dependency/plugin resolution"
./gradlew --no-daemon help --stacktrace --refresh-dependencies

echo "[2/5] Running native host tests"
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure

echo "[3/5] Running JVM unit tests"
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace

echo "[4/5] Assembling app artifacts"
./gradlew --no-daemon :app:assemble --stacktrace

echo "[5/5] Release validation completed successfully"
