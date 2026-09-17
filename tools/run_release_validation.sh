#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -x "./gradlew" ]]; then
  echo "Gradle wrapper is missing or not executable: ${ROOT_DIR}/gradlew" >&2
  exit 1
fi

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
