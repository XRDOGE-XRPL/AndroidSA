# Projektvorstellung: AndroidSA

## Kurzbeschreibung

AndroidSA ist ein Android-Prototyp für einen SA:MP-/Open:MP-orientierten Client. Das Repository verbindet eine moderne Jetpack-Compose-Oberfläche mit einer Kotlin/JNI-Brücke und einem nativen C++20-Kern. Der Prototyp ist derzeit mit ca. 85-90% Fertigstellung in einem stabilen, dokumentierten und testbaren Zustand und zeigt bereits den Kern einer realen Client-/Runtime-Architektur.

## Aktueller Projektstatus

- Prototypstatus: ca. 85-90% fertig
- fertig: UI, Bridge, Zustandsmodell, Event-Historie, native UDP-Proben, CMake/CTest-Tests, JVM-Unit-Tests
- offen/weiterentwicklungswürdig: größere Remote-Transport-Szenarien, verfeinerte Android-/Emulator-Integration und Release-Readiness der Letztvalidierung

## Zentrale Bestandteile

### Android-Oberfläche

Die Compose-UI zeigt:

- aktuellen Verbindungsstatus
- Transportprofil
- Serveradresse, Spielername und Diagnostics
- Latenz und Paketstatistiken
- letzte Commands und native Ereignisse
- Server-Browser mit definierten und benutzerdefinierten Profilen

### Kotlin/JNI-Bridge

Die Bridge validiert Commands, lädt die Native-Library, verarbeitet den elfteiligen Summary und normalisiert native Fehlerzustände für die UI. Ihre Laufzeitdaten kommen aus dem nativen `ClientState` und werden mit Fallbacks und Validierungen in die JVM-Schicht übernommen.

### Nativer C++20-Kern

Der C++-Layer verwaltet den Clientzustand thread-sicher, verarbeitet Commands und simuliert Netzwerkaktivität über einen Loopback- und real-udp / remote-udp-Pfad. Empfangene Probe-Pakete werden dekodiert, als readable Events in die Historie geschrieben und für die UI als kompaktes, serialisiertes Laufzeitbild zugänglich gemacht.

## 11 Laufzeitfelder der JNI-Bridge

Die Java/Kotlin-Seite verarbeitet einen elfspaltigen Summary, der exakt wie folgt aufgebaut ist:

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

Die Parsing-Logik in `NativeBridge.kt` ergänzt Fallback-Werte, sobald Segmente fehlen, leer sind oder numerische Werte nicht parsebar sind.

## Event-Historie und native Paketlogik

Die Event-Historie ist eine kompakte, deterministische Folge von Ereignissen aus dem nativen Layer. Sie enthält unter anderem:

- `Ping acknowledged by native runtime`
- `Connected to ... (udp tx=..., rx=...)`
- `Reconnected ...`
- `Inbound traffic simulation recorded ...`
- `Outbound traffic simulation recorded ...`
- `RX RakNet connected ping`
- `RX Open:MP/SA:MP RPC wrapper ...`

Bei UDP-Inputs werden Pakete nach RakNet-/Open:MP-Mustern analysiert. Vom Bytestream werden Packet-IDs und RPC-Wrapper-Details extrahiert; daraus entstehen lesbare, kurze Eventzeilen, die sowohl im nativen State als auch in der UI-Beschriftung verwendet werden.

Die native Historie hält eine kompakte Anzahl von Einträgen; die JVM-Seite begrenzt die Anzeige zusätzlich auf die jüngsten 48 Events.

## Mutex-Sperren-Architektur

Der zentrale Synchronisationspunkt liegt in `ClientState`:

- `std::mutex mutex_` schützt den Laufzeitzustand
- `std::lock_guard` schützt `summary()`, `recentEvents()`, `dispatchCommand()` und alle Zustandsänderungen
- alle Paketzähler, Event-Einträge, Transport- und Diagnose-Änderungen erfolgen atomar innerhalb dieser Sperren

Dadurch bleiben Paketzähler, Event-Historie und Statusübergänge bei parallelen Anfragen konsistent. Die UI-Seite nutzt ebenfalls einen `Mutex` bei Command-Dispatches, damit keine zwei gleichzeitigen UI-Aktionen denselben Laufzeitzustand in konfligierende Zustände schreiben.

## Typische Einsatzfelder im aktuellen Stand

- technisches Grundgerüst für spätere Multiplayer-Clientlogik
- Demo- und Testprojekt für Kotlin/JNI-Interop mit C++20
- Basis für Diagnose-, Status- und Verbindungs-Workflows auf Android
- Referenzprojekt für Compose + NDK + CMake in einem GitHub-Repository

## Technische Highlights

- Jetpack Compose als UI-Schicht
- Kotlin mit JVM-Target 17 und Java 17
- Android SDK 34 und minSdk 26
- C++20 mit CMake
- native Host-Tests plus JVM-Unit-Tests
- GitHub Actions Workflow für Build, Tests und Artefakte

## Für wen das Projekt interessant ist

- Android-Entwickler mit Interesse an NDK-/JNI-Integration
- Entwickler, die Kotlin- und C++-Interop evaluieren möchten
- Teams, die einen kontrollierten Multiplayer-nahen Clientzustand visualisieren wollen
- Mitwirkende, die ein gut dokumentiertes Compose/NDK-Beispiel für Diagramme und Architektur-Reviews nutzen möchten

## Vollständiges Setup und Durchführung `run test`

### Setup

1. Repository lokal öffnen und in das Root-Verzeichnis wechseln.
2. Gradle Wrapper freischalten:

   ```bash
   chmod +x ./gradlew
   ```

3. Toolchain sicherstellen:
   - JDK 17
   - Android SDK 34
   - Android NDK `27.3.13750724`
   - CMake 3.22.1+

### Testdurchführung

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon check build --stacktrace
```

