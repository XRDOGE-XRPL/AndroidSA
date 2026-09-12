# Native C++ Layer (`app/src/main/cpp`)

Der native Layer kapselt JNI-Einstiegspunkte, Zustandsverwaltung und Logging für AndroidSA.

## Dateien

- `CMakeLists.txt`  
  Definiert die Shared Library `androidsa`, aktiviert C++20 und bindet Android `log`.
- `native-lib.cpp`  
  JNI-Funktionen:
  - `nativeGetClientSummary()`
  - `nativeDispatchCommand(command)`
- `native/network/ClientState.h/.cpp`  
  Thread-sicherer Client-Zustand + Command-Dispatch.
- `native/logging/Logger.h/.cpp`  
  Logging-Helfer über Android Logcat.

## Command-Verarbeitung

`ClientState::dispatchCommand` unterstützt aktuell:

- `ping`
- `connect`
- `disconnect`
- `reset`
- `status`
- `transport:<name>`
- `diagnostics:<text>`

Validierung im nativen Layer:

- Command nach `trim` darf nicht leer sein
- maximale Länge 64 Zeichen
- keine Steuerzeichen
- kein `|`
- `transport` muss exakt mit `:` getrennt sein
- `diagnostics` muss exakt mit `:` getrennt sein

## Build-Hinweise

Der native Teil wird über das App-Modul gebaut. Direkter Einstieg:

```bash
./gradlew :app:build
```

Die CMake-Minimalversion ist in `CMakeLists.txt` auf `3.22.1` gesetzt.

Für Host-Tests (ohne Android-Ziel) kann zusätzlich der native Test ausgeführt werden:

```bash
cmake -S app/src/main/cpp -B /tmp/androidsa-native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build /tmp/androidsa-native-tests --target client_state_test
ctest --test-dir /tmp/androidsa-native-tests --output-on-failure
```
