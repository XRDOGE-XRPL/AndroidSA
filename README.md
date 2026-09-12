# AndroidSA

AndroidSA ist ein Android-Projekt für einen SA:MP- / Open:MP-orientierten Client-Prototypen. Das Repository kombiniert eine Jetpack-Compose-Oberfläche, eine Kotlin/JNI-Bridge und einen nativen C++20-Kern, um Verbindungsstatus, Serverprofile, Diagnosemeldungen und simulierten Netzwerkverkehr gemeinsam abzubilden.

## Inhalt

- [Projektüberblick](#projektüberblick)
- [Ziele](#ziele)
- [Architektur](#architektur)
- [Repository-Struktur](#repository-struktur)
- [Funktionsumfang](#funktionsumfang)
- [Native Command-Spezifikation](#native-command-spezifikation)
- [Build, Tests und CI](#build-tests-und-ci)
- [Entwicklungsablauf](#entwicklungsablauf)
- [Troubleshooting](#troubleshooting)
- [Lizenz](#lizenz)

## Projektüberblick

Die App visualisiert einen nativen Laufzeitzustand als Android-Oberfläche. Nutzer können Serverprofile verwalten, geführte Commands auslösen, manuelle Commands an die JNI-Bridge senden und die daraus resultierenden Status-, Statistik- und Eventdaten direkt beobachten.

Die Standarddarstellung umfasst:

- Client-Name und aktiven Transport
- Verbindungszustand und Diagnostik
- Server- und Spielerprofil
- Latenz, Paket-Zähler und Verbindungsversuche
- letzte ausgeführte Aktion
- begrenzte Event-Historie aus dem nativen Layer

## Ziele

- Android-Grundgerüst für eine spätere SA:MP- / Open:MP-Integration bereitstellen
- UI-, Bridge- und Native-Logik klar voneinander trennen
- JNI-Kommunikation mit deterministischer Command-Validierung absichern
- Netzwerknahe Zustandswechsel und Telemetrie reproduzierbar simulieren
- Build-, Test- und Release-Abläufe nachvollziehbar dokumentieren

## Architektur

### 1. Android UI (Jetpack Compose)

Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`

Die Compose-Oberfläche stellt mehrere Funktionsbereiche bereit:

- Session-Überblick mit Transport, Connection State, Diagnostics, Server und Player
- Runtime-Statistiken für Latenz, TX/RX-Pakete, Reconnect-Versuche und letzten Command
- Server-Browser mit vordefinierten und benutzerdefinierten Serverprofilen
- Guided Controls für Connect, Reconnect, Disconnect, Ping, Player, Transport, Latenz, Diagnostics und Reset
- manuelle Command-Eingabe mit sofortiger Validierungsrückmeldung
- Anzeige der letzten nativen Events

Wichtige UI-Eigenschaften:

- Snapshot wird beim Start asynchron geladen
- parallele Command-Dispatches werden per `Mutex` blockiert
- bei aktiver Verbindung werden Metriken zyklisch nachgeladen
- lokale Validierungsfehler werden direkt als Fehler-Snapshot eingeblendet

### 2. Kotlin/JNI-Bridge

Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`

Die Bridge ist für folgende Aufgaben verantwortlich:

- Laden der nativen Bibliothek `androidsa`
- Validierung und Normalisierung eingehender Commands
- Parsen des nativen Summary-Formats
- Parsen und Begrenzen der nativen Event-Historie
- Vereinheitlichung von Fehlerzuständen, wenn Diagnostics auf native Fehler hindeuten

### 3. Nativer C++20-Layer

Dateien:

- `app/src/main/cpp/native-lib.cpp`
- `app/src/main/cpp/native/network/ClientState.h`
- `app/src/main/cpp/native/network/ClientState.cpp`
- `app/src/main/cpp/native/logging/Logger.h`
- `app/src/main/cpp/native/logging/Logger.cpp`

Der Native-Layer hält den Laufzeitzustand thread-sicher und stellt JNI-Einstiegspunkte für Summary, Event-Log und Command-Dispatch bereit. Netzwerknahe Abläufe werden über einen Loopback-UDP-Flow simuliert. Eingehende Probe-Pakete werden dekodiert und als Events protokolliert.

## Repository-Struktur

```text
AndroidSA/
├── .github/workflows/android-background-build.yml
├── CHANGELOG.md
├── Projectvorstellungs.md
├── README.md
├── docs/
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
└── settings.gradle.kts
```

## Funktionsumfang

### Laufzeitdaten aus dem nativen Layer

Die native Summary transportiert aktuell elf Felder:

```text
AndroidSA|<transport>|<state>|<diagnostics>|<server>|<player>|<latencyMs>|<packetsSent>|<packetsReceived>|<connectionAttempts>|<lastCommand>
```

Die Kotlin-Seite ergänzt Fallback-Werte, wenn Segmente fehlen, leer sind oder numerische Werte ungültig bleiben.

### Server- und Session-Verhalten

- Standard-Serverprofile: `Demo EU` und `Local Dev`
- benutzerdefinierte Serverprofile können in der UI ergänzt und wieder entfernt werden
- `connect` nutzt das aktuell gespeicherte Serverprofil
- `connect:<server>` setzt das Serverprofil und verbindet direkt
- `reconnect` verwendet die zuletzt aktive Serveradresse
- `reset` stellt Transport, Diagnostics, Profile, Statistiken und Event-Historie auf Ausgangswerte zurück

### Statistik- und Event-Verhalten

- `packetsSent` und `packetsReceived` werden über reale `sendto`- und `recvfrom`-Aufrufe erhöht
- empfangene UDP-Probes werden als RakNet-/Open:MP-orientierte Events beschrieben
- die UI zeigt die jüngsten Events; Kotlin kappt auf 48 Einträge, der native State hält eine kompakte Historie

## Native Command-Spezifikation

### Unterstützte Commands

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

### Validierungsregeln

Diese Regeln gelten in Kotlin und im nativen Layer:

- der Command wird getrimmt und darf nicht leer sein
- maximale Länge: `64` Zeichen
- keine Steuerzeichen
- `|` ist verboten
- wertbasierte Commands müssen die exakte Form `keyword:<value>` nutzen
- `latency:<ms>` akzeptiert nur nicht-negative Integer
- `simulate:<value>` akzeptiert nur `rx` oder `tx`

### Typische Zustandsänderungen

- `ping` setzt den State auf `ready`
- `connect` und `connect:<server>` setzen den State auf `connected`
- `disconnect` setzt den State auf `disconnected`
- `fail:<reason>` setzt den State auf `error`
- `status` erzeugt einen Diagnose-Snapshot ohne Profilwechsel

## Build, Tests und CI

### Voraussetzungen

- JDK 17
- Android SDK 34
- minSdk 26
- Android NDK `27.3.13750724`
- CMake 3.22.1+ (Android-Gradle-Konfiguration nutzt 3.31.5)

### Lokale Befehle

```bash
chmod +x ./gradlew
./gradlew build
./gradlew check build
./gradlew :app:build
./gradlew :app:check
./gradlew :app:testDebugUnitTest
```

Zusätzlich für native Host-Tests:

```bash
cmake -S app/src/main/cpp -B /tmp/androidsa-native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build /tmp/androidsa-native-tests --target client_state_test
ctest --test-dir /tmp/androidsa-native-tests --output-on-failure
```

### Lokaler Google-Maven-Fallback für blockierte Umgebungen

Wenn `dl.google.com` lokal nicht aufgelöst oder durch die Laufzeitumgebung blockiert wird, kann ein lokaler Mirror-Proxy für Google-Maven-Artefakte gestartet werden:

```bash
python3 tools/google_maven_proxy.py --port 38473
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew check build
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew :app:testDebugUnitTest :app:assemble
```

Die Standard-Konfiguration bleibt unverändert auf `google()`/`mavenCentral()`. Der Proxy wird nur verwendet, wenn `ANDROIDSA_GOOGLE_MAVEN_URL` oder `-Pandroidsa.google.maven.url=...` explizit gesetzt ist.

Copilot-Cloud-Agent-Sitzungen starten denselben Proxy automatisch über `.github/workflows/copilot-setup-steps.yml`, damit Gradle-Auflösung bereits vor aktivierter Firewall auf den lokalen Mirror umgebogen wird.

### Root-Build-Verhalten

Das Root-Projekt aktiviert `base` und verdrahtet:

- `build` → `:app:build`
- `check` → `:app:check`

### CI-Workflow

Workflow-Datei: `.github/workflows/android-background-build.yml`

Die Pipeline führt aus:

1. Checkout mit voller Historie
2. JDK-17-Setup und Gradle-Cache
3. Gradle-Setup und Wrapper-Validierung
4. Warmup der Plugin- und Dependency-Auflösung mit Retry
5. native Host-Tests via CMake/CTest
6. `./gradlew --no-daemon :app:testDebugUnitTest --stacktrace` mit Retry
7. `./gradlew --no-daemon :app:assemble --stacktrace` mit Retry
8. Upload der Testreports als Artefakt

Zusätzlich konfiguriert `.github/workflows/copilot-setup-steps.yml` Copilot-Cloud-Agent-Sitzungen vorab mit JDK 17, dem lokalen Google-Maven-Proxy und einem Gradle-Warmup, damit blockierte `dl.google.com`-Zugriffe keine Agent-Läufe abbrechen.

## Entwicklungsablauf

- fachliche Änderungen zuerst im passenden Layer lokalisieren
- Änderungen an Commands immer in Kotlin und C++ gegeneinander prüfen
- bei UI-Anpassungen Snapshot-, Busy- und Error-Flows mitdenken
- vor Releases `CHANGELOG.md` und `docs/RELEASE_CHECKLIST.md` aktualisieren
- für eine kompakte Projektvorstellung siehe `Projectvorstellungs.md`

## Troubleshooting

- **JNI-Library lädt nicht:** sicherstellen, dass `androidsa` erfolgreich gebaut wurde
- **Gradle-Abhängigkeiten schlagen fehl:** Google Maven und Maven Central Erreichbarkeit prüfen; in blockierten Agent-Umgebungen den lokalen Mirror via `tools/google_maven_proxy.py` und `ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/` verwenden
- **Native Tests schlagen fehl:** Build-Verzeichnis unter `/tmp/androidsa-native-tests` neu erzeugen
- **Command wird abgelehnt:** Syntax, Maximallänge, verbotene Zeichen und Wertebereich prüfen
- **Keine Paketereignisse sichtbar:** Connect- oder Simulations-Commands erneut auslösen, damit neue UDP-Probes erzeugt werden

## Lizenz

Proprietary (XRDOGE-XRPL)
