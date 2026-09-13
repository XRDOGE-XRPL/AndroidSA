# Native C++ Layer

Der Ordner `app/src/main/cpp` enthält den nativen Kern von AndroidSA. Hier liegen JNI-Einstiegspunkte, Logging, Zustandsverwaltung, UDP-Probing und native Host-Tests.

## Bestandteile

- `CMakeLists.txt`  
  definiert die Shared Library `androidsa`, aktiviert C++20 und registriert optional den Host-Test `client_state_test`.
- `native-lib.cpp`  
  stellt die JNI-Funktionen `nativeGetClientSummary()`, `nativeGetRecentEvents()` und `nativeDispatchCommand()` bereit.
- `native/network/ClientState.h` / `ClientState.cpp`  
  implementieren den vollständigen Laufzeitzustand, Command-Dispatch, Summary-Erzeugung, Event-Historie und UDP-Probes.
- `native/network/ClientStateTest.cpp`  
  nativer Host-Test für zentrale Zustandsübergänge und Counter-Verhalten.
- `native/logging/Logger.h` / `Logger.cpp`  
  abstrahieren Log-Ausgaben über Android Logcat beziehungsweise `std::clog` auf Nicht-Android-Plattformen.

## Zustandsmodell

`ClientState` hält unter einem Mutex folgende Kernwerte:

- `transport_`
- `state_`
- `diagnostics_`
- `serverAddress_`
- `playerName_`
- `latencyMs_`
- `packetsSent_`
- `packetsReceived_`
- `connectionAttempts_`
- `lastCommand_`
- `eventLog_`

Zusätzlich verwaltet der State einen UDP-Socket und ein Flag, ob die Laufzeitumgebung für Probes bereits initialisiert wurde.

## Summary-Format

Der native Layer liefert aktuell ein elfspaltiges Pipe-Format:

```text
AndroidSA|<transport>|<state>|<diagnostics>|<server>|<player>|<latencyMs>|<packetsSent>|<packetsReceived>|<connectionAttempts>|<lastCommand>
```

Dieses Format wird direkt in Kotlin weiterverarbeitet. Änderungen daran erfordern immer eine Synchronisierung mit `app/src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt` und den zugehörigen Tests.

## Unterstützte Commands

`ClientState::dispatchCommand()` verarbeitet derzeit:

- `ping`
- `connect`
- `connect:<server>`
- `reconnect`
- `disconnect`
- `reset`
- `status`
- `transport:<name>`
- `diagnostics:<text>`
- `player:<name>`
- `latency:<ms>`
- `fail:<reason>`
- `simulate:rx`
- `simulate:tx`
- generische Fallback-Commands, die als `command:<input>` im State landen

## Validierungslogik

Noch bevor ein Command verarbeitet wird, prüft der Native-Layer:

- keine Steuerzeichen
- kein `|`
- nach `trim` nicht leer
- maximal 64 Zeichen
- exakte `keyword:<value>`-Form bei wertbasierten Commands
- nur nicht-negative Integer für `latency`
- nur `rx` oder `tx` für `simulate`

Ungültige Commands liefern `false` an die JVM zurück.

## UDP-Probing und Paketereignisse

Für `connect`, `connect:<server>`, `reconnect` und `simulate:*` wird ein Loopback-UDP-Flow verwendet:

- Socket-Erzeugung via `socket(AF_INET, SOCK_DGRAM, 0)`
- Nonblocking-Modus per `fcntl`
- Bind auf `127.0.0.1` mit ephemerem Port
- Probe-Sends via `sendto`
- Probe-Receives via `recvfrom`

Empfangene Payloads werden analysiert. Der Parser beschreibt derzeit u. a.:

- `0x00` → RakNet connected ping
- `0x1c` → RakNet open connection request
- `0x1d` → RakNet open connection reply
- `0x7d` → Open:MP/SA:MP RPC wrapper

Bei RPC-Wrappern werden zusätzlich RPC-ID und deklarierte Payload-Länge ins Event geschrieben.

## Logging

`Logger::info()` schreibt:

- auf Android in Logcat mit `ANDROID_LOG_INFO`
- auf Host-Systemen in `std::clog`

Das Logging wird nach erfolgreichen Dispatches mit der aktuellen Diagnostics-Meldung aufgerufen.

## Build und Tests

### Build über Gradle

```bash
./gradlew :app:build
```

### Direkter Host-Testlauf

```bash
cmake -S app/src/main/cpp -B /tmp/androidsa-native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build /tmp/androidsa-native-tests --target client_state_test
ctest --test-dir /tmp/androidsa-native-tests --output-on-failure
```

## Änderungsrichtlinien

- neue Commands immer in Kotlin und C++ spiegeln
- Änderungen an UDP-Probing oder Summary-Feldern mit Host-Test und JVM-Tests absichern
- Event-Texte bewusst wählen, da sie direkt in der Android-Oberfläche erscheinen
- Fehlerdiagnosen so formulieren, dass die Bridge sie konsistent als Fehlerzustand interpretieren kann

## Vollständiges Setup und Durchführung `run test`

### Setup für den nativen Layer

1. Im Repository-Root arbeiten.
2. Voraussetzungen sicherstellen:
   - JDK 17 (für Gradle/JVM-Tests)
   - CMake 3.22.1+
   - Android NDK `27.3.13750724`
3. Optional Gradle-Wrapper ausführbar machen:

   ```bash
   chmod +x ./gradlew
   ```

### Durchführung Testlauf

```bash
cmake -S app/src/main/cpp -B /tmp/androidsa-native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build /tmp/androidsa-native-tests --target client_state_test
ctest --test-dir /tmp/androidsa-native-tests --output-on-failure
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon check build --stacktrace
```
