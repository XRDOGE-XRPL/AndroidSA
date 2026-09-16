# AndroidSA

AndroidSA ist ein Android-Projekt für einen SA:MP-/Open:MP-orientierten Client-Prototypen. Das Repository kombiniert eine Jetpack-Compose-Oberfläche, eine Kotlin/JNI-Bridge und einen nativen C++20-Kern, um Verbindungsstatus, Serverprofile, Diagnosemeldungen, Laufzeitstatistiken und generiertes UDP-Netzwerkverhalten an einer klaren, testbaren Architektur abzubilden.

## Inhalt

- [Projektüberblick](#projektüberblick)
- [Architektur](#architektur)
- [Erweiterter Befehlssatz](#erweiterter-befehlssatz)
- [UDP-Netzwerkmodi](#udp-netzwerkmodi)
- [Repository-Struktur](#repository-struktur)
- [Build, Tests und CI](#build-tests-und-ci)
- [Troubleshooting](#troubleshooting)

## Projektüberblick

Die App visualisiert einen nativen Laufzeitzustand als Android-Oberfläche. Nutzer verwalten Serverprofile, senden geführte oder manuelle Commands über die JNI-Bridge und beobachten unmittelbar Status-, Diagnose-, Statistik- und Eventdaten aus dem nativen Kern.

Das Produkt ist aktuell als halbproduktiver Prototyp mit ca. 85-90% Fertigstellung zu verstehen: UI, JNI-/C++-Integration, event-basierte Diagnostik und host-testbare Zustandslogik sind bereits weitgehend abgeschlossen; die verbleibenden Schritte liegen vor allem in der Verfeinerung größerer Android-/Netzwerk-Szenarien und in der abschließenden Release-Validierung.

## Architektur

### 1. Jetpack Compose UI

Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`

Die Compose-Schicht stellt die vollständige Laufzeitansicht bereit:

- Verbindungsstatus, Transport, Server, Player und Diagnostics
- Latenz, Paketzähler, Reconnect-Versuche und letzter Command
- Server-Browser mit Standard- und benutzerdefinierten Profilen
- Steuerungen für Connect, Reconnect, Disconnect, Status, Reset und UDP-Simulation
- eindeutige Event-Historie und direkte Validierungsrückmeldungen für Command-Fehler

### 2. Kotlin/JNI-Bridge

Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`

Die Java/Kotlin-Brücke übernimmt:

- Laden der nativen Bibliothek `androidsa`
- Validierung und Normalisierung eingehender Commands
- Parsing des elfteiligen nativen `summary()`-Formats
- Event-Parsing und Begrenzung der Historie in der JVM-Schicht
- Normalisierung von Fehlerzuständen aus native Diagnostics in einen konsistenten Android-UI-State

### 3. C++20-Kern

Dateien:

- `app/src/main/cpp/native-lib.cpp`
- `app/src/main/cpp/native/network/ClientState.h`
- `app/src/main/cpp/native/network/ClientState.cpp`
- `app/src/main/cpp/native/logging/Logger.h`
- `app/src/main/cpp/native/logging/Logger.cpp`

Der native Kern verwaltet den Laufzeitzustand, validiert Commands, erzeugt die Summary-Zeichenkette und führt schwarze, reproduzierbare UDP-Probes aus. Der Zustand wird unter `std::mutex` geschützt; Dispatches, Event-Log und Paketzähler laufen damit deterministisch und thread-sicher.

## Erweiterter Befehlssatz

Die JVM- und native Validierung akzeptieren aktuell diese exakten Befehle:

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
- `diagnostics:<text>`
- `simulate:rx`
- `simulate:tx`
- `fail:<reason>`

Wichtige Regeln:

- Commands werden vor dem Dispatch auf Leerzeichen, Steuerzeichen und `|` geprüft.
- Die maximale Länge beträgt `64` Zeichen.
- Wertbasierte Befehle müssen exakt `keyword:<value>` verwenden.
- `latency:<ms>` akzeptiert nur nicht-negative Integer.
- `simulate:<value>` akzeptiert nur `rx` oder `tx`.
- `transport:<value>` wird case-insensitive verarbeitet und darf kein Leerzeichen vor dem Doppelpunkt enthalten.

## UDP-Netzwerkmodi

Der native Layer unterstützt zwei wesentliche Betriebsarten für UDP-Probes:

- Loopback-Modus: lokale Host-/Probe-Tests auf `127.0.0.1`, als Null- oder Schnellpfad für deterministische native Tests und Arbeitsabläufe ohne echtes Remote-Target.
- real-udp / remote-udp: echte socket-basierte sendto/recvfrom-Flows gegen ein Ziel wie `demo.sa-mp.local:7777` oder einen konfigurierten Remote-Endpunkt; die Probe wird dabei als echte UDP-Kommunikation behandelt und in Status-/Eventstruktur weitergereicht.

Die Implementierung unterscheidet intern zwischen loopbackartigen Hostnamen (z. B. `localhost`, `127.0.0.1`, `::1`, `loopback`, `0.0.0.0`) und echten Remote-Adressen. Dadurch bleibt der lokale Testpfad stabil, während der real-udp / remote-udp-Pfad bei bewusst aktivierter Konfiguration verwendet werden kann.

## Repository-Struktur

```text
AndroidSA/
├── .github/workflows/android-background-build.yml
├── CHANGELOG.md
├── Projectvorstellungs.md
├── README.md
├── docs/
│   ├── ANDROID_DEVICE_CI_READY_CHECKLIST.md
│   └── RELEASE_CHECKLIST.md
├── app/
│   ├── README.md
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── cpp/
│       │   │   ├── README.md
│       │   │   ├── CMakeLists.txt
│       │   │   ├── native-lib.cpp
│       │   │   └── native/
│       │   │       ├── logging/
│       │   │       └── network/
│       │   ├── java/com/xrdoge/xrpl/androidsa/
│       │   │   ├── MainActivity.kt
│       │   │   └── NativeBridge.kt
│       │   └── res/values/strings.xml
│       └── test/java/com/xrdoge/xrpl/androidsa/
│           └── NativeBridgeTest.kt
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── settings.gradle.kts
├── tools/
│   └── google_maven_proxy.py
├── .gitignore
└── ToDo.md
```

## Build, Tests und CI

### Voraussetzungen

- JDK 17
- Android SDK 34
- minSdk 26
- Android NDK `27.3.13750724`
- CMake 3.22.1+
- optional: lokaler Google-Maven-Proxy für blockierte Umgebungen

### Lokaler Mirror-Proxy für blockiertes Google Maven

Falls `dl.google.com` in der Laufzeitumgebung nicht erreichbar ist, kann der lokale Proxy gestartet werden:

```bash
python3 tools/google_maven_proxy.py --port 38473
```

Dann die Gradle-Resolution mit dem lokalen Proxy exponieren:

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
```

### Native Host-Tests (CMake/CTest)

```bash
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
```

### JVM-Unit-Tests (Gradle mit lokalem Maven-Proxy)

```bash
chmod +x ./gradlew
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

Wenn ein lokaler Maven-Proxy verwendet werden muss:

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

### Gesamtvalidierung

```bash
./gradlew --no-daemon check build --stacktrace
```

Der vollständige Repository-Check kann zusätzlich mit dem Warmup-Schritt ausgeführt werden:

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon check build --stacktrace
```

## Troubleshooting

- Keine Native-Events sichtbar: Verbindung erneut aufbauen oder `simulate:rx`/`simulate:tx` auslösen, damit neue UDP-Probes erzeugt werden.
- Command wird abgelehnt: auf exakte Syntax prüfen (`transport:<value>`, `latency:<ms>`, `simulate:rx|tx` usw.).
- Gradle-Resolver blockiert: lokalen Maven-Proxy starten und `ANDROIDSA_GOOGLE_MAVEN_URL` auf `http://127.0.0.1:38473/` setzen.
- Native Host-Tests fehlschlagen: CMake-Output ansehen, `build/native-tests` bereinigen und erneut `ctest --test-dir build/native-tests --output-on-failure` ausführen.
