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
- `transport:<name>`

Validierung im nativen Layer:

- Command nach `trim` darf nicht leer sein
- maximale Länge 64 Zeichen
- keine Steuerzeichen
- kein `|`
- `transport` muss exakt mit `:` getrennt sein

## Build-Hinweise

Der native Teil wird über das App-Modul gebaut. Direkter Einstieg:

```bash
./gradlew :app:build
```

Die CMake-Minimalversion ist in `CMakeLists.txt` auf `3.22.1` gesetzt.
