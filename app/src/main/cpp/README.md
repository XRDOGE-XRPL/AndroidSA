# Native C++ Layer

Der Ordner `app/src/main/cpp` enthält den nativen Kern von AndroidSA. Hier liegen JNI-Einstiegspunkte, Logging, Zustandsverwaltung, UDP-Probing und native Host-Tests.

## Umfang und reale Grenzen

Der native Layer arbeitet bewusst auf der Ebene von Diagnose, Status und Server-/Netzwerk-Interaktion. Er kann:

- UDP-Probes, Ping- und Connect-Checks simulieren
- Paket- und RPC-Wrapper-Flüsse analysieren
- Event-History und Laufzeitstatus für UI und Debugging bereitstellen

Er ist nicht dafür vorgesehen, ein echtes GTA-SA-Mobile-Spiel-Binary zu injizieren oder eine fertige Open:MP-Android-Gameplay-Schicht zu liefern. AndroidSA modelliert also die diagnostische und launcherartige Server-/Netzwerk-Seite, nicht den vollständigen Spielclient.

Open:MP bleibt im realen Stack ein PC-/Server- und Launcher-Ökosystem. Eine legale Android-Variante würde ein eigenständiges Projekt mit eigener GTA-SA-Mobile-MP-Schicht erfordern.

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
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
```

## Änderungsrichtlinien

- neue Commands immer in Kotlin und C++ spiegeln
- Änderungen an UDP-Probing oder Summary-Feldern mit Host-Test und JVM-Tests absichern
- Event-Texte bewusst wählen, da sie direkt in der Android-Oberfläche erscheinen
- Fehlerdiagnosen so formulieren, dass die Bridge sie konsistent als Fehlerzustand interpretieren kann
- keine Gameplay-Fiktion in native Logs oder Summary-Daten einbringen

## Praktische Betriebsregeln

Für den nativen Stack bleiben diese Regeln verbindlich:

- Diagnose und Protokollanalyse sind Gegenstand des Projekts
- echte Spiel-Gameplay-Integration ist nicht Bestandteil
- Paketsignale als technische Hinweise behandeln, nicht als Gameplay-Metadaten
- Server- und Netzwerk-Status bleiben die primäre produktive Funktionalität

## Vollständiges Setup und Durchführung `run test`

### Setup für den nativen Layer

1. Im Repository-Root arbeiten.
2. Voraussetzungen sicherstellen:
   - JDK 17 (für Gradle/JVM-Tests)
   - Android SDK 34 (für Gradle-Validierung)
   - CMake 3.22.1+
   - Android NDK `27.3.13750724`
3. Optional Gradle-Wrapper ausführbar machen:

   ```bash
   chmod +x ./gradlew
   ```

### Durchführung Testlauf

```bash
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
```

Für den vollständigen app-/projektweiten Gradle-Testlauf siehe die zentrale Anleitung in `/README.md`.
