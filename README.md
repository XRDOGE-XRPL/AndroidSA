# AndroidSA

SA:MP / Open:MP Client für Android mit Jetpack Compose, JNI-Bridge und nativen C++20-Komponenten.

**Ziel:** Eine Android-Basis für Multiplayer-Gameplay mit klarer Trennung zwischen UI, Kotlin/JNI-Brücke und nativer Laufzeit.

## Architektur

- **Layer 1 (UI):** Jetpack Compose in `app/src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`
- **Layer 2 (JNI Bridge):** `NativeBridge.kt` kapselt den Austausch zwischen Kotlin und C++
- **Layer 3 (Native):** C++20 mit CMake in `app/src/main/cpp/`
  - `native-lib.cpp` – JNI-Einstiegspunkte
  - `native/network/ClientState.*` – einfacher Client-Zustand und Command-Dispatch
  - `native/logging/Logger.*` – Android-Logging

## Build-Anforderungen

- Android SDK 34+
- minSdk 26
- NDK 27.x
- CMake 3.22.1+
- JDK 17
- ABIs: `arm64-v8a`, `armeabi-v7a`

## Quick Start

```bash
git clone https://github.com/XRDOGE-XRPL/AndroidSA.git
cd AndroidSA
./gradlew build
```

## Aktueller Stand

- Vollständiges Android-Gradle-Projekt mit Wrapper
- Compose-Startoberfläche für den nativen Status
- JNI-Bridge für Statusabfrage und Command-Refresh
- Native C++20-Bibliothek mit thread-sicherem Client-Zustand
- Kotlin-Unit-Test für die Parsing-Logik

## Hinweise zur lokalen Validierung

- Vollbuild: `./gradlew build`
- Nativen Teil isoliert prüfen: CMake mit dem Android-NDK gegen `app/src/main/cpp`

## Lizenz

Proprietary (XRDOGE-XRPL)
