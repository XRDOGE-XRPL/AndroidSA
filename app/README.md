# App-Modul

Das Modul `app` enthält die vollständige Android-Anwendung. Hier laufen Compose-Oberfläche, JNI-Bridge, Native-Build-Einbindung und JVM-Tests zusammen.

## Modulzweck

Das App-Modul dient als Android-Hülle für den nativen AndroidSA-Diagnose-/Serverstatus-Zustand. Es zeigt Runtime-Daten an, validiert Nutzerkommandos vor dem JNI-Aufruf und synchronisiert UI-Eingaben mit dem Snapshot aus dem nativen Layer.

## Aktueller Scope des Moduls

Das App-Modul ist aktuell auf den klaren Naht-B-/Runtime-Shell-Scope fixiert:

- Phase 1: Host-Erkennung und Launch für die lokale GTA-SA-Mobile-Laufzeit
- Manifest-Queries nur auf die offiziellen Host-Pakete der Android-App
- Host-Status im UI: Package, Version, Launch-Intent, lokale Laufzeit- und Stream-Info
- Phase 2: nur Surface-/Capture-/Status-Flow, kein Multiplayer-Client, kein Join, kein Sync

Damit bleibt die App eine lokale Host-/Diagnose-Schicht, die den GTA-SA-Mobile-Host nur erkennt, startet und beobachtet, aber keine echte spielbare GTA-Client-Integration anbietet.

## Produktgrenzen und realistischer Scope

Das Modul ist bewusst keine GTA-SA-Mobile-Spielclient-Implementierung. Es ist eine Server-/Status-/Launcher-Schicht für:

- Serverprobe, Ping und Query-ähnliche Verbindungschecks
- Paket-/RPC-/Wrapper-Analyse
- Event-History, Laufzeit-Status und Diagnoseausgaben
- per-server Probe- und Status-Tracking

Es ist nicht vorgesehen für:

- echtes Joinen in eine Laufzeitwelt mit Gameplay-Sync
- RenderWare- oder Mobile-Game-Integration
- Anbieten eines fertigen Open:MP-Android-Clients
- die Behauptung, AndroidSA sei der „Open:MP Mobile Client“

Aktuell beginnt der RakNet-/Open:MP-Teil mit klarer Paket- und Wrapper-Erkennung (z. B. `0x00`, `0x1c`, `0x1d`, `0x7d`) als diagnostische Server-Health-Schicht. Eine echte mobile Multiplayer-Variante braucht ein separates, legales Projekt auf Basis von GTA SA Mobile mit eigener MP-Schicht; AndroidSA bleibt in dieser Sicht die diagnostische und launcherartige Vorstufe.

## Implementierungs-Reihenfolge im App-Modul

Die Anwendung darf die Projektgrenze nicht aus den Augen verlieren. Die Reihenfolge ist bewusst:

1. GTA APK / Stream als erste reale Datenquelle
2. Native Bridge und UDP-/Packet-Validierung
3. Event-Diagnostik, Filterung und Dashboard-Sichtbarkeit
4. RakNet/Open:MP-Observability nur als Analyse- und Diagnosewerkzeug
5. Erst danach echte Spiel-/Multiplayer-Interpretation

AndroidSA ist und bleibt ein Diagnose-/Probe-Layer. Es gibt keine Gameplay-Synchronisation, keinen echten GTA-SA-Android-Client und kein SA:MP-Join vor einem stabilen APK-Stream.

## Inhalt

- `src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`  
  Compose-UI mit Session-Überblick, Runtime-Stats, Guided Controls, Server-Browser, manueller Command-Eingabe und Event-Liste.
- `src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`  
  JNI-Bridge, Command-Validierung, Parsing des Summary-Formats und Fehlernormalisierung.
- `src/main/cpp/`  
  nativer C++20-Code inklusive CMake-Konfiguration und Host-Tests.
- `src/test/java/com/xrdoge/xrpl/androidsa/NativeBridgeTest.kt`  
  Unit-Tests für Parser-, Event- und Validierungslogik.
- `src/main/AndroidManifest.xml`  
  deklariert `INTERNET`-Permission und `MainActivity` als Launcher-Entry.
- `src/main/res/values/strings.xml`  
  enthält derzeit den App-Namen `AndroidSA`.

## Android-Konfiguration

- Namespace: `com.xrdoge.xrpl.androidsa`
- Application ID: `com.xrdoge.xrpl.androidsa`
- compileSdk: `34`
- targetSdk: `34`
- minSdk: `26`
- NDK: `27.3.13750724`
- ABIs: `arm64-v8a`, `armeabi-v7a`
- Java/Kotlin-Ziel: `17`
- Compose Compiler Extension: `1.5.14`

## UI-Flows im Modul

### Initialer Start

Beim Start lädt die UI asynchron einen kompletten Snapshot über `NativeBridge.snapshot()`. Falls dies fehlschlägt, wird ein lokaler Fehler-Snapshot mit `connectionState = error` erzeugt.

### Command-Dispatch

Die UI sperrt parallele Dispatches über `Mutex`, `Job`-Tracking und Busy-State. Dadurch werden doppelte Requests aus Buttons und manueller Eingabe verhindert.

### Auto-Refresh

Sobald der Connection State `connected` ist, werden Runtime-Metriken im Sekundentakt aktualisiert, solange keine andere Aktion läuft.

### Server-Browser

- Standardprofile: `Demo EU` und `Local Dev`
- neue Profile werden aus Label, Host und Port erzeugt
- Profile speichern zuletzt bekannte Latenz und den letzten bekannten State
- Profile ohne `default-`-Präfix können wieder entfernt werden

### Diagnostik- und Statusansicht

Die App zeigt nicht nur „online/offline“, sondern eine kleine Diagnose-Diagnostik-Stack:

- Probe-Status: idle, handshake, reply, payload, timeout
- Paket-Metriken: sent/received/Zähler
- letzter Command und letzte Native-Fehlerdiagnose
- RakNet/Open:MP-Signal-Erkennung als technische Hinweise, nicht als gameplay-proof

## Native-Bridge-Verhalten

Die Bridge stellt zwei zentrale Datenstrukturen bereit:

- `NativeOverview` für den zusammengefassten Laufzeitstatus
- `NativeClientSnapshot` für Overview plus Event-Liste

Zusätzlich kapselt sie:

- `requireValidNativeCommand()` für die gemeinsame JVM-seitige Validierung
- `parseNativeOverview()` für das Pipe-basierte Summary-Format
- `parseNativeEventLog()` für die Zeilenliste nativer Events
- `normalizeNativeSnapshot()` für deterministische Fehlerabbildung

## Command-Regeln im Modul

Vor dem JNI-Aufruf gelten diese Regeln:

- kein leerer Input
- maximal 64 Zeichen
- keine Steuerzeichen
- kein `|`
- exakte Form `transport:<value>`
- exakte Form `connect:<server>`
- exakte Form `player:<name>`
- exakte Form `latency:<ms>` mit nicht-negativem Integer
- exakte Form `diagnostics:<value>`
- exakte Form `protocol:<idle|handshake|reply|payload|status>`
- exakte Form `simulate:rx|tx`
- exakte Form `fail:<reason>`

Diese Regeln werden im nativen Layer erneut abgesichert.

## Build- und Testbefehle

```bash
./gradlew :app:build
./gradlew :app:check
./gradlew :app:testDebugUnitTest
```

## Hinweise für Änderungen im Modul

- Änderungen an Commands immer zusammen mit `NativeBridgeTest.kt` prüfen
- Änderungen an Summary-Feldern müssen mit dem nativen Format synchron bleiben
- UI-Texte sollten zum tatsächlichen Laufzeitverhalten der nativen Commands passen
- Race-Conditions besonders bei Busy-State, Auto-Refresh und manuellem Dispatch beachten
- Dokumentationsänderungen sollten das reale Projekt- und Produktverständnis nicht verschieben

## Vollständiges Setup und Durchführung `run test`

### Setup im App-Modul

1. In das Repository-Root wechseln.
2. Gradle Wrapper aktivieren:

   ```bash
   chmod +x ./gradlew
   ```

3. Lokale Toolchain sicherstellen:
   - JDK 17
   - Android SDK 34
   - NDK `27.3.13750724`
   - CMake 3.22.1+
4. Bei blockiertem Google Maven optional:

   ```bash
   python3 tools/google_maven_proxy.py --port 38473
   ```

### Durchführung Testlauf (App-Modul)

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:check :app:build --stacktrace
```

Für den vollständigen projektweiten `run test` (inkl. Root-`check build`) siehe `/README.md`.
