# app Modul

Dieses Modul enthält die Android-Anwendung inklusive Compose-UI, JNI-Bridge, nativer Integration und Unit-Tests.

## Inhalt

- `src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`  
  Compose-UI, geführte Controls, Statusanzeige, Event-Log, Command-Eingabe, anti-race Dispatching und Live-Metrik-Refresh bei aktiver Verbindung.
- `src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`  
  JNI-Bridge, Command-Validierung, Parsing/Normalisierung der nativen Summary und Event-Historie inkl. deterministischer Fehlerweitergabe.
- `src/main/cpp/`  
  Native C++20-Komponenten inkl. CMake-Konfiguration.
- `src/test/java/com/xrdoge/xrpl/androidsa/NativeBridgeTest.kt`  
  Kotlin-Unit-Tests für Parsing-, Event- und Validierungslogik.

## Android-Konfiguration (Kurzüberblick)

- Namespace/ApplicationId: `com.xrdoge.xrpl.androidsa`
- compileSdk/targetSdk: 34
- minSdk: 26
- NDK: `27.3.13750724`
- ABIs: `arm64-v8a`, `armeabi-v7a`
- Java/Kotlin Target: 17

## Relevante Aufgaben im Modul

- Build: `./gradlew :app:build`
- Checks: `./gradlew :app:check`
- Unit-Tests: `./gradlew :app:testDebugUnitTest`

## Native-Command-Regeln im Modul

Die Command-Eingabe wird vor dem JNI-Aufruf validiert:

- nicht leer
- maximal 64 Zeichen
- keine Steuerzeichen
- kein `|`
- `connect` verbindet mit dem aktuell gespeicherten Serverprofil
- für Transportwechsel exakt `transport:<value>`
- für Serverwechsel exakt `connect:<server>`
- für Spielerwechsel exakt `player:<name>`
- für Latenztests exakt `latency:<ms>`
- für manuelle Diagnostik exakt `diagnostics:<value>`
- für simulierten Traffic exakt `simulate:rx` oder `simulate:tx`
- für Fehlerzustände exakt `fail:<reason>`

Diese Regeln werden zusätzlich im nativen Layer abgesichert.
