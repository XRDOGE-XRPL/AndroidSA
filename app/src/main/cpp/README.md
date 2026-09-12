# Native C++ Layer (`app/src/main/cpp`)

Der native Layer kapselt JNI-Einstiegspunkte, Zustandsverwaltung und Logging für AndroidSA.

## Dateien

- `CMakeLists.txt`  
  Definiert die Shared Library `androidsa`, aktiviert C++20 und bindet Android `log`.
- `native-lib.cpp`  
  JNI-Funktionen:
  - `nativeGetClientSummary()`
  - `nativeGetRecentEvents()`
  - `nativeDispatchCommand(command)`
- `native/network/ClientState.h/.cpp`  
  Thread-sicherer Client-Zustand, Event-Historie und Command-Dispatch.
- `native/logging/Logger.h/.cpp`  
  Logging-Helfer über Android Logcat.

## Command-Verarbeitung

`ClientState::dispatchCommand` unterstützt aktuell:

- `ping`
- `connect`
- `connect:<server>`
- `reconnect`
- `disconnect`
- `reset`
- `status`
- `transport:<name>`
- `player:<name>`
- `latency:<ms>`
- `simulate:rx`
- `simulate:tx`
- `diagnostics:<text>`
- `fail:<reason>`

Validierung im nativen Layer:

- Command nach `trim` darf nicht leer sein
- maximale Länge 64 Zeichen
- keine Steuerzeichen
- kein `|`
- `transport`, `connect:<server>`, `player:<name>`, `latency:<ms>`, `simulate:<value>` und `fail:<reason>` nutzen exakte `keyword:<value>`-Syntax
- `diagnostics` muss exakt mit `:` getrennt sein
- `latency` akzeptiert nur nicht-negative Integer
- `simulate` akzeptiert nur `rx` oder `tx`

## Laufzeitdaten

Der Native-State liefert eine erweiterte Summary inklusive:

- Transport
- Connection-State
- Diagnostics
- Server-Adresse
- Spielername
- Latenz
- Paket-Zähler (TX/RX)
- Anzahl der Verbindungsversuche
- letzter erfolgreicher Command
- begrenzte Event-Historie für Debug-Ausgaben

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
