# app Modul

Dieses Modul enthält die Android-Anwendung inklusive Compose-UI, JNI-Bridge, nativer Integration und Unit-Tests.

## Inhalt

- `src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`  
  Compose-UI, Statusanzeige, Command-Eingabe und asynchrones Dispatching.
- `src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`  
  JNI-Bridge, Command-Validierung, Parsing der nativen Summary.
- `src/main/cpp/`  
  Native C++20-Komponenten inkl. CMake-Konfiguration.
- `src/test/java/com/xrdoge/xrpl/androidsa/NativeBridgeTest.kt`  
  Kotlin-Unit-Tests für Parsing- und Validierungslogik.

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
- für Transportwechsel exakt `transport:<value>`

Diese Regeln werden zusätzlich im nativen Layer abgesichert.
