# Projektvorstellung: AndroidSA

## Kurzbeschreibung

AndroidSA ist eine Diagnose- und Launcher-Schicht für die lokal installierte GTA SA Mobile-Laufzeit auf demselben Android-Gerät. Das Repository verbindet eine Jetpack-Compose-Oberfläche mit einer Kotlin/JNI-Brücke und einem nativen C++20-Kern. Der Schwerpunkt liegt auf einem vollständigen, dokumentierten Laufzeitmodell für Host-Detection, Start, lokaler Capture-/Stream-Status, Diagnosemeldungen, Event-Historie, Paketstatistiken und UDP-Transport-Probing.

## Reale Produktpositionierung

AndroidSA ist kein „fake gameplay client“ und kein behaupteter Open:MP-Android-Client. Die Architektur arbeitet bewusst auf drei Ebenen:

1. UI-Layer: Serverstatus, Profilverwaltung, Diagnoseansicht, Event-Log
2. Kotlin/JNI-Layer: Validierung, Snapshot-Parsing und Command-Dispatch
3. Native C++-Layer: UDP-Proby, Statusmodell, Paket- und Signal-Erkennung

Die Kernidee ist: AndroidSA ist ein Sammler und Diagnostiker für das Netzwerk-/Server-Umfeld um SA:MP/Open:MP, nicht die komplette Spielwelt bzw. der mobile Spiel-Renderer.

## Aktueller Stand

Der Prototyp befindet sich im Naht-B-Abschnitt für den Host-/Runtime-Stack. Die Kernarchitektur und die wichtigsten Qualitätsbereiche sind bereits abgeschlossen:

- Jetpack Compose UI mit Host-Status, Package Detection und Stream-Controls
- Kotlin/JNI-Bridge mit 11-Felder Summary-Format und erweiterten `stream:*`-Commands
- C++20 ClientState mit Mutex-geschütztem Zustand, Event-Historie und Stream-Lifecycle
- UDP-Probe- und Paket-Erkenner für Loopback- und Remote-Targets
- native Host-Tests via CMake/CTest
- JVM-Unit-Tests via Gradle
- lokaler Maven-Proxy für blockierte Build-Umgebungen

## Zentrale Bestandteile

### Android-Oberfläche

Die Compose-UI zeigt:

- aktuellen Verbindungsstatus
- Transportprofil
- Serveradresse, Spielername und Diagnostics
- Latenz, Paketzähler, TX/RX-Ratio und Verbindungsversuche
- letzte Commands und native Events
- Server-Browser mit Standard- und benutzerdefinierten Profilen
- Bedien- und Testaktionen für Connect, Reconnect, Disconnect, Reset, Status und Simulate

### Kotlin/JNI-Bridge

Die Bridge validiert Commands, lädt die Native-Library, verarbeitet den elfteiligen Summary und normalisiert native Fehlerzustände für die UI. Die JVM-Seite verarbeitet dabei Fallback-Werte, Diagnose-Normalisierung und Event-Parsing robust und konsistent.

### Nativer C++20-Kern

Der C++-Layer verwaltet den Clientzustand thread-sicher, verarbeitet Commands, erzeugt die Summary-Zeichenkette und führt echte UDP-Probes aus. Empfangene Payloads werden dekodiert, als Events in die Historie geschrieben und für die UI als kompakter Status-Report zugänglich gemacht.

## 11 Laufzeitfelder

Die Java/Kotlin-Seite verarbeitet einen elfspaltigen Summary, exakt in diesem Format:

```text
AndroidSA|<transport>|<state>|<diagnostics>|<server>|<player>|<latencyMs>|<packetsSent>|<packetsReceived>|<connectionAttempts>|<lastCommand>
```

Die 11 Felder sind:

1. `clientName`
2. `transport`
3. `connectionState`
4. `diagnostics`
5. `serverAddress`
6. `playerName`
7. `latencyMs`
8. `packetsSent`
9. `packetsReceived`
10. `connectionAttempts`
11. `lastCommand`

Die Parsing-Logik sorgt für robuste Fallbacks, wenn Segmente fehlen, leer sind oder numerische Felder nicht validierbar sind.

## Event-Historie und Paketanalyse

Die Event-Historie ist eine kompakte Folge aus native Paket- und Statusereignissen. Typische Einträge sind:

- `Ping acknowledged by native runtime`
- `Connected to ... (udp tx=..., rx=...)`
- `Reconnect flow completed ...`
- `RX RakNet connected ping`
- `RX Open:MP/SA:MP RPC wrapper ...`
- `Session reset to initial state`

Bei UDP-Inputs werden Payloads nach RakNet-/Open:MP-Mustern analysiert. Packet-IDs und RPC-Wrapper-Details werden aus dem Bytestream extrahiert und als lesbare Eventzeilen weitergereicht. Dadurch bleibt die History sowohl für Debugging als auch für UI-Darstellung nutzbar.

## Signal- und Protokollschichten

AndroidSA arbeitet bewusst nur mit einer diagnostischen Signal- und Paketschicht. Die wichtigsten Marker sind:

- `0x00` → RakNet connected ping
- `0x1c` → RakNet open connection request
- `0x1d` → RakNet open connection reply
- `0x7d` → Open:MP/SA:MP RPC wrapper

Diese Signale zeigen an, dass der Stack Netzwerk-/Protokoll-Charakteristika überhaupt erkennt. Sie belegen aber keinen spielbaren Android-Client-Status und keine laufende GTA-Gameplay-Synchronisierung.

## Mutex-Sperren-Architektur

Der zentrale Synchronisationspunkt liegt in `ClientState`:

- `std::mutex mutex_` schützt Laufzeitstatus und Paketzähler
- `std::lock_guard` schützt `summary()`, `recentEvents()` und `dispatchCommand()`
- Event-Einträge, Transportwechsel und Diagnoseänderungen erfolgen atomar in derselben Sperre

Die UI-Seite nutzt ebenfalls einen `Mutex` für Command-Dispatches, sodass keine zwei parallelen Actions denselben Native-Zustand in widersprüchliche Zustände schreiben.

## UDP-Netzwerkmodi

Der native Layer unterstützt zwei wesentliche Betriebsarten für UDP-Probe-Flows:

### Loopback-Modus

Der Loopback-Pfad nutzt `socket(AF_INET, SOCK_DGRAM, 0)`, bindet auf `127.0.0.1` bzw. `INADDR_LOOPBACK` und arbeitet non-blocking. Dadurch bleibt der Host-Testpfad schnell, deterministisch und reproduzierbar, ohne ein echtes Remote-Target zu benötigen.

### real-udp / remote-udp

Wenn der Host kein Loopback-ähnlicher Wert ist, wird der Endpoint mit `getaddrinfo()` aufgelöst und als `sockaddr_in` gesetzt. Anschließend werden echte UDP-Pakete mit `sendto()` und `recvfrom()` an den konfigurierten Remote-Endpunkt gesendet. Das Remote-Szenario ist bewusst eigenständig vom Loopback-Testpfad getrennt und nur dann aktiv, wenn ein echtes Remote-Target verwendet wird.

Damit bleiben lokale native Tests stabil, während echte Produktivserver sauber adressiert werden können.

## Typische Einsatzfelder

- technische Grundlage für spätere Multiplayer-Clientlogik
- Demo-/Prototyp-Stack für Kotlin/JNI-Interop mit C++20
- native Diagnose-, Status- und Verbindungs-Repräsentation auf Android
- Referenzarchitektur für Compose + NDK + CMake in einem GitHub-Repository
- Diagnose-/Launcher- und Server-Browser-Auslegung statt Fake-Gameplay-Client

## Technische Highlights

- Jetpack Compose als UI-Schicht
- Kotlin mit JVM-Target 17
- Android SDK 34 und minSdk 26
- C++20 mit CMake
- native Host-Tests und JVM-Unit-Tests
- lokaler Google-Maven-Proxy als Build-Resilience-Mechanismus

## Validierung und Release-Readiness

Die folgenden Validierungen sind in der finalen Projektphase vorgesehen und dokumentiert:

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon check build --stacktrace
```

## Setup

1. Repository klonen und in das Root-Verzeichnis wechseln.
2. Gradle Wrapper freischalten:

```bash
chmod +x ./gradlew
```

3. Toolchain prüfen:
   - JDK 17
   - Android SDK 34
   - Android NDK `27.3.13750724`
   - CMake 3.22.1+

4. Falls Google Maven blockiert ist, lokalen Proxy starten:

```bash
python3 tools/google_maven_proxy.py --port 38473
```

5. Dann Gradle mit Proxy konfigurieren:

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
```

Damit ist der Prototyp in einem stabilen, testbaren, dokumentierten Zustand für lokale Entwicklung, Host-Tests und mobile Android-Validierung.

## Abschluss

AndroidSA ist eine ehrliche, technisch gebundene Diagnose-/Launcher-Architektur für das Umfeld von SA:MP/Open:MP – aber kein Spielclient, keine gameplay-injektive Android-Übersetzung und kein Ersatz für einen echten MP-Client auf GTA SA Mobile. Die Entwicklung bleibt an der realen technischen Grenze orientiert: Netzwerk-Signale, Server-Health und klare, reproduzierbare Diagnose.

