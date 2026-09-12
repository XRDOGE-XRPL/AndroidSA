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
- `transport`, `connect:<server>`, `player:<name>`, `latency:<ms>`, `simulate:<value>`, `diagnostics:<value>` und `fail:<reason>` nutzen exakte `keyword:<value>`-Syntax
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

## UDP-Probing und Paket-Parsing

Der aktuelle Native-Kern verwendet für `connect`, `reconnect`, `simulate:rx` und `simulate:tx` einen realen UDP-Socket-Flow auf Loopback-Basis:

- Öffnen und Binden eines UDP-Sockets auf `127.0.0.1` (ephemerer Port)
- tatsächliche `sendto`/`recvfrom`-Operationen für Paketzählung
- Auswertung eingehender Pakete über einen RakNet/Open:MP-orientierten Bytestream-Pfad

Der Parser erkennt derzeit grundlegende Pakettypen (inkl. RPC-Wrapper `0x7d`) und schreibt dekodierte Ereignisse in die Event-Historie.

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
