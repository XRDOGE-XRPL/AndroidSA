# AndroidSA

AndroidSA ist eine Android-Basis für einen SA:MP / Open:MP-orientierten Client mit klarer Trennung zwischen Compose-UI, Kotlin/JNI-Bridge und nativer C++-Laufzeit.

## Inhalt

- [Projektziel](#projektziel)
- [Architektur](#architektur)
- [Repository-Struktur](#repository-struktur)
- [Voraussetzungen](#voraussetzungen)
- [Schnellstart](#schnellstart)
- [Build- und Testbefehle](#build--und-testbefehle)
- [Native Command-Spezifikation](#native-command-spezifikation)
- [Erweiterter Laufzeitstatus](#erweiterter-laufzeitstatus)
- [UI- und Laufzeitverhalten](#ui--und-laufzeitverhalten)
- [CI](#ci)
- [Release-Management](#release-management)
- [Troubleshooting](#troubleshooting)
- [Lizenz](#lizenz)

## Projektziel

Das Projekt stellt ein erweiterbares Grundgerüst bereit, um Multiplayer-Logik auf Android kontrolliert über eine Kotlin/JNI-Schicht mit nativen Komponenten zu verbinden.

## Architektur

### Layer 1 – Android UI (Jetpack Compose)

- Einstieg: `app/src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`
- Oberfläche zeigt:
  - Client-Name
  - aktiven Transport
  - Verbindungszustand
  - Server- und Spielerprofil
  - Laufzeitstatistiken
  - Diagnose- und Event-Ansicht
- Geführte Eingaben + Buttons senden native Commands über die Bridge.

### Layer 2 – Kotlin/JNI-Bridge

- Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`
- Aufgaben:
  - Laden der nativen Bibliothek `androidsa`
  - Validierung von Commands vor JNI-Dispatch
  - Parsen der nativen Summary
  - Parsen der nativen Event-Historie in einen gemeinsamen Snapshot

### Layer 3 – Native C++20-Laufzeit

- Einstieg: `app/src/main/cpp/native-lib.cpp`
- Zustand/Command-Dispatch: `app/src/main/cpp/native/network/ClientState.*`
- Logging: `app/src/main/cpp/native/logging/Logger.*`
- Build über CMake in `app/src/main/cpp/CMakeLists.txt`

## Repository-Struktur

```text
AndroidSA/
├── app/
│   ├── src/main/java/com/xrdoge/xrpl/androidsa/
│   │   ├── MainActivity.kt
│   │   └── NativeBridge.kt
│   ├── src/main/cpp/
│   │   ├── CMakeLists.txt
│   │   ├── native-lib.cpp
│   │   └── native/
│   │       ├── logging/Logger.*
│   │       └── network/ClientState.*
│   └── src/test/java/com/xrdoge/xrpl/androidsa/NativeBridgeTest.kt
├── .github/workflows/android-background-build.yml
├── build.gradle.kts
└── settings.gradle.kts
```

## Voraussetzungen

- JDK 17
- Android SDK 34+
- minSdk 26
- Android NDK 27.x
- CMake 3.22.1+
- unterstützte ABIs: `arm64-v8a`, `armeabi-v7a`

## Schnellstart

```bash
git clone https://github.com/XRDOGE-XRPL/AndroidSA.git
cd AndroidSA
chmod +x ./gradlew
./gradlew build
```

## Build- und Testbefehle

- Gesamtbuild: `./gradlew build`
- Checks + Build: `./gradlew check build`
- App-spezifisch: `./gradlew :app:build`
- App-Checks: `./gradlew :app:check`
- Unit-Tests: `./gradlew :app:testDebugUnitTest`

Hinweis: Das Root-Projekt verdrahtet `build` und `check` auf `:app:build` bzw. `:app:check`.

## Native Command-Spezifikation

### Unterstützte Commands

- `ping` → Zustand wird `ready`
- `connect` → verbindet mit dem aktuell gespeicherten Serverprofil
- `connect:<server>` → setzt Serverprofil und verbindet direkt
- `reconnect` → erneuter Verbindungsaufbau zum gespeicherten Server
- `disconnect` → Zustand wird `disconnected`
- `reset` → Transport/Zustand/Statistiken/Event-Historie auf Initialwerte
- `status` → erzeugt einen Diagnose-Snapshot ohne Zustandswechsel
- `transport:<name>` → aktiven Transport wechseln
- `player:<name>` → aktives Spielerprofil setzen
- `latency:<ms>` → Latenz zu Testzwecken überschreiben
- `simulate:rx` / `simulate:tx` → eingehenden bzw. ausgehenden Traffic simulieren
- `diagnostics:<text>` → setzt eine manuelle Diagnostikmeldung
- `fail:<reason>` → simuliert einen Fehlerzustand

### Validierungsregeln (Kotlin + Native)

- Command wird getrimmt und darf nicht leer sein.
- Maximale Länge: 64 Zeichen.
- Keine Steuerzeichen erlaubt.
- Zeichen `|` ist verboten (Schutz des Summary-Formats).
- `transport`, `connect:<server>`, `player:<name>`, `latency:<ms>`, `simulate:<value>` und `fail:<reason>` nutzen exakte `keyword:<value>`-Syntax ohne Leerzeichen vor `:`.
- `diagnostics:<value>` nutzt exakte `keyword:<value>`-Syntax ohne Leerzeichen vor `:`.
- `simulate` akzeptiert nur `rx` oder `tx`.
- `latency` akzeptiert nur nicht-negative Integerwerte.

### Summary-Format aus Native Layer

Die native Summary wird als Pipe-separierter String mit erweiterten Feldern geliefert:

```text
AndroidSA|<transport>|<state>|<diagnostics>|<server>|<player>|<latencyMs>|<packetsSent>|<packetsReceived>|<connectionAttempts>|<lastCommand>
```

Die Kotlin-Seite nutzt Fallbacks für fehlende/leere Segmente und setzt ungültige Zahlenfelder auf `0` zurück.

## Erweiterter Laufzeitstatus

Der Native-State hält zusätzlich zu Transport, State und Diagnostics nun fest:

- aktives Serverprofil
- Spielerprofil
- Latenz
- gesendete und empfangene Paket-Zähler
- Anzahl der Verbindungsversuche
- zuletzt akzeptierter Command
- begrenzte Event-Historie für UI und Debugging

## UI- und Laufzeitverhalten

- Beim App-Start wird ein kompletter Snapshot aus Summary + Event-Historie asynchron geladen.
- Die UI zeigt getrennte Bereiche für Session-Überblick, Laufzeitstatistiken, geführte Controls, manuelle Commands und Events.
- Während laufender Operationen sind Eingaben und Buttons deaktiviert.
- Fehler aus Bridge/Native werden im Diagnostics-Feld und in der Event-Liste sichtbar.
- Command-Ausführung ist gegen paralleles Mehrfach-Dispatch abgesichert.

## CI

Workflow: `.github/workflows/android-background-build.yml`

Die CI-Pipeline:

1. Checkout (`actions/checkout@v4`)
2. JDK 17 + Gradle Cache (`actions/setup-java@v4`)
3. Gradle Setup + Wrapper Validation
4. Native Host-Tests (`client_state_test` via CMake/CTest)
5. Unit-Tests (`:app:testDebugUnitTest`)
6. Assemble mit:

```bash
./gradlew --no-daemon :app:assemble --stacktrace
```

Zusätzlich werden Testreports als CI-Artefakt hochgeladen.

## Release-Management

- Changelog: `CHANGELOG.md`
- Checkliste: `docs/RELEASE_CHECKLIST.md`
- Empfohlener Ablauf:
  1. `CHANGELOG.md` unter `Unreleased` aktualisieren
  2. lokale Checks/Build ausführen
  3. CI-Ergebnisse und Testreports prüfen
  4. Release-Tag und Notes vorbereiten

## Troubleshooting

- **Gradle/Plugin kann nicht aufgelöst werden:** Netzwerkzugriff auf Google Maven prüfen.
- **NDK/CMake-Probleme:** installierte Versionen mit `app/build.gradle.kts` abgleichen.
- **JNI-Library lädt nicht:** sicherstellen, dass `androidsa` erfolgreich gebaut wurde.
- **Command wird abgelehnt:** auf Syntax (`connect:<server>`, `transport:<value>`, `player:<value>`, `latency:<ms>`, `simulate:rx|tx`, `diagnostics:<value>`, `fail:<reason>`), Länge und verbotene Zeichen prüfen.

## Lizenz

Proprietary (XRDOGE-XRPL)
