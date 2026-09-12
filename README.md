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
  - Diagnostik-Text
- Eingabefeld + Button senden native Commands über die Bridge.

### Layer 2 – Kotlin/JNI-Bridge

- Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`
- Aufgaben:
  - Laden der nativen Bibliothek `androidsa`
  - Validierung von Commands vor JNI-Dispatch
  - Parsen der nativen Summary (`client|transport|state|diagnostics`)

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
- `connect` → Zustand wird `connected`
- `disconnect` → Zustand wird `disconnected`
- `reset` → Transport/Zustand/Diagnostik auf Initialwerte
- `status` → erzeugt einen Diagnose-Snapshot ohne Zustandswechsel
- `transport:<name>` → aktiven Transport wechseln
- `diagnostics:<text>` → setzt eine manuelle Diagnostikmeldung

### Validierungsregeln (Kotlin + Native)

- Command wird getrimmt und darf nicht leer sein.
- Maximale Länge: 64 Zeichen.
- Keine Steuerzeichen erlaubt.
- Zeichen `|` ist verboten (Schutz des Summary-Formats).
- `transport`-Syntax muss exakt `transport:<value>` sein:
  - Keyword ist case-insensitive
  - kein Leerzeichen vor `:`
  - Wert nach `:` darf nicht leer sein
- `diagnostics`-Syntax muss exakt `diagnostics:<value>` sein:
  - Keyword ist case-insensitive
  - kein Leerzeichen vor `:`
  - Wert nach `:` darf nicht leer sein

### Summary-Format aus Native Layer

Die native Summary wird als Pipe-separierter String geliefert:

```text
AndroidSA|<transport>|<state>|<diagnostics>
```

Die Kotlin-Seite nutzt Fallbacks für fehlende/leere Segmente.
`<diagnostics>` darf selbst kein `|` enthalten, damit das 4-Felder-Format stabil bleibt.

## UI- und Laufzeitverhalten

- Beim App-Start wird der native Zustand asynchron geladen.
- Während laufender Operationen ist die Command-Eingabe deaktiviert.
- Fehler aus Bridge/Native werden im Diagnostics-Feld angezeigt.
- Command-Ausführung ist gegen paralleles Mehrfach-Dispatch abgesichert.

## CI

Workflow: `.github/workflows/android-background-build.yml`

Die CI-Pipeline:

1. Checkout (`actions/checkout@v4`)
2. JDK 17 + Gradle Cache (`actions/setup-java@v4`)
3. Gradle Setup + Wrapper Validation
4. Unit-Tests (`:app:testDebugUnitTest`)
5. Build mit:

```bash
./gradlew --no-daemon check build --stacktrace
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
- **Command wird abgelehnt:** auf Syntax (`transport:<value>`/`diagnostics:<value>`), Länge und verbotene Zeichen prüfen.

## Lizenz

Proprietary (XRDOGE-XRPL)
