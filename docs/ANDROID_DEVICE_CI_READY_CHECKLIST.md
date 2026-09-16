# Ready-for-CI: Android Emulator / Device Validation

Kompakte Checkliste für einen echten Android-Emulator- oder Device-Lauf in einer vollwertigen Android-Umgebung.

## 1) Umgebung und Toolchain

- [ ] JDK 17 ist installiert und in `PATH`
- [ ] Android SDK + commandline-tools sind installiert
- [ ] `ANDROID_HOME` bzw. `ANDROID_SDK_ROOT` sind gesetzt
- [ ] Android Platform 34 und passende System-Image (z. B. x86_64 / arm64-v8a) sind installiert
- [ ] NDK `27.3.13750724` und CMake `3.22.1+` sind verfügbar
- [ ] `adb` und `emulator` aus dem Android SDK sind in `PATH`
- [ ] bei blockiertem Google-Maven: lokaler Proxy gestartet (`python3 tools/google_maven_proxy.py --port 38473`)
- [ ] Gradle-Warmup erfolgreich: `./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies`

## 2) Emulator / Device-Health

- [ ] Emulator oder physisches Gerät online
- [ ] `adb devices -l` zeigt genau ein gültiges Gerät / eine gültige VM
- [ ] Boot abgeschlossen: `adb shell getprop sys.boot_completed` liefert `1`
- [ ] `adb logcat -c` erfolgreich ausgeführt
- [ ] kein aktiver ANR-/Crash-Blocker vor dem Teststart

## 3) Native und JVM-Build-Checks

- [ ] Native Host-Tests grün:
  - `cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON`
  - `cmake --build build/native-tests --target client_state_test`
  - `ctest --test-dir build/native-tests --output-on-failure`
- [ ] JVM-Unit-Tests grün:
  - `./gradlew --no-daemon :app:testDebugUnitTest --stacktrace`
- [ ] Debug-App und Android-Test-APK bauen:
  - `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace`

## 4) Instrumentierte UI-Tests auf Android

- [ ] App auf Emulator/Device installiert
- [ ] Instrumentation-Runner aktiv (`AndroidJUnitRunner`)
- [ ] UI-Tests auf dem Gerät ausgeführt:
  - `./gradlew --no-daemon :app:connectedDebugAndroidTest --stacktrace`
- [ ] Server-Browser-Flow validiert:
  - eigener Server-Pfad kann hinzugefügt werden
  - Auswahl / Connect / Ping funktionieren
- [ ] Busy-State validiert:
  - während laufender Native-Operation erscheint kein zweiter Dispatch
  - UI bleibt konsistent und blockiert keine Controls außer der erwarteten
- [ ] Runtime-Stats validiert:
  - Latenz, Packets sent/received, TX/RX ratio, Last command und Connection state werden korrekt dargestellt
  - TX/RX-Ratio zeigt `0.00`, `∞` oder einen sinnvollen numerischen Wert je nach Messwerten

## 5) Remote-Socket / Netzwerk-Szenario

- [ ] Produktive Remote-UDP-Variante nur in explizitem, bewusst aktiviertem Modus
- [ ] Loopback-/Host-Test-Pfad bleibt stabil und unverändert
- [ ] bei echter Remote-Verbindung: Zieladresse wird mit `getaddrinfo` korrekt aufgelöst
- [ ] kein Test-/Produktionsregressionsfehler bei Loopback-Probing und Host-Tests

## 6) Ergebnis und Abschluss

- [ ] Testreporte geprüft (`app/build/reports/` und Android-Test-Reports)
- [ ] Logcat überprüft: keine kritischen Java/Kotlin-/Native-Fehler
- [ ] Bildschirm-/UI-Ablauf bestätigt und dokumentiert
- [ ] PR-/CI-Status abgeschlossen, ohne offene Blocker

## 7) Kurzform für CI-Runner

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON \
  && cmake --build build/native-tests --target client_state_test \
  && ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
./gradlew --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Weitere direkte Hinweise zur Android-/Gradle-Umgebung finden sich in `README.md`, `app/README.md`, `app/src/main/cpp/README.md` und `docs/RELEASE_CHECKLIST.md`.
