# AndroidSA

SA:MP / Open:MP Client für Android mit nativen C++20-Komponenten (NDK).

**Ziel:** Mobilisierung von Multiplayer-Gameplay auf Android mit stabiler Netzwerkkommunikation, Speicherverwaltung und Thread-Safety via JNI/Kotlin-Bridge.

## Architektur

- **Layer 1 (UI):** Kotlin/Jetpack Compose
- **Layer 2 (JNI Bridge):** `NativeBridge.kt` – sichere Marshalling zwischen Kotlin und C++
- **Layer 3 (Native):** C++20 mit CMake
  - `native-lib.cpp` – Haupt-Einstiegspunkt
  - `native/network/` – Netzwerk-Stack + RPC-Implementierung
  - `native/logging/` – Crash-Diagnostik + Logging

## Build-Anforderungen

- Android SDK 34+ (compileSdk)
- minSdk: 26 (Android 8.0)
- NDK 27.x (oder aktuell verfügbar)
- CMake 3.22.1+
- Kotlin 1.9.24+
- ABIs: `arm64-v8a`, `armeabi-v7a`

## Quick Start

```bash
git clone https://github.com/XRDOGE-XRPL/AndroidSA.git
cd AndroidSA
git checkout feature/ndk-bootstrap
./gradlew build
```

## Lizenz

Proprietary (XRDOGE-XRPL)
