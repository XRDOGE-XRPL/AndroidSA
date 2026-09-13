# App-Modul

Das Modul `app` enthält die vollständige Android-Anwendung. Hier laufen Compose-Oberfläche, JNI-Bridge, Native-Build-Einbindung und JVM-Tests zusammen.

## Modulzweck

Das App-Modul dient als Android-Hülle für den nativen AndroidSA-Clientzustand. Es zeigt Runtime-Daten an, validiert Nutzerkommandos vor dem JNI-Aufruf und synchronisiert UI-Eingaben mit dem Snapshot aus dem nativen Layer.

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

## Vollständiges Setup und Durchführung `run test`

### Setup im App-Modul

1. In das Repository-Root wechseln (`/home/runner/work/AndroidSA/AndroidSA`).
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

### Durchführung Testlauf

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:check :app:build --stacktrace
```
