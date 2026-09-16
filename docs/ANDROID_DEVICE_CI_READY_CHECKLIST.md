# Ready-for-CI: Android Device / Local Validation

Kompakte Checkliste für einen stabilen Android-Geräte- oder lokalen Validierungs-Lauf ohne die instabile Emulator-UI-Test-Pipeline im Standard-Workflow.

## 1) Umgebung und Toolchain

- [ ] JDK 17 ist installiert und in `PATH`
- [ ] Android SDK + commandline-tools sind installiert
- [ ] `ANDROID_HOME` bzw. `ANDROID_SDK_ROOT` sind gesetzt
- [ ] Android Platform 34 und passende System-Image (z. B. x86_64 / arm64-v8a) sind installiert
- [ ] NDK `27.3.13750724` und CMake `3.22.1+` sind verfügbar
- [ ] `adb` und `emulator` aus dem Android SDK sind in `PATH`
- [ ] bei blockiertem Google-Maven: lokaler Proxy gestartet (`python3 tools/google_maven_proxy.py --port 38473`)
- [ ] Gradle-Warmup erfolgreich: `./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies`

## 2) Gerät / Emulator-Health (optional)

- [ ] Emulator oder physisches Gerät online
- [ ] `adb devices -l` zeigt genau ein gültiges Gerät / eine gültige VM
- [ ] Boot abgeschlossen: `adb shell getprop sys.boot_completed` liefert `1`
- [ ] `adb logcat -c` erfolgreich ausgeführt
- [ ] kein aktiver ANR-/Crash-Blocker vor dem lokalen Geräte-Check

## 3) Native und JVM-Build-Checks

- [ ] Native Host-Tests grün:
  - `cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON`
  - `cmake --build build/native-tests --target client_state_test`
  - `ctest --test-dir build/native-tests --output-on-failure`
- [ ] JVM-Unit-Tests grün:
  - `./gradlew --no-daemon :app:testDebugUnitTest --stacktrace`
- [ ] Debug-App bauen:
  - `./gradlew --no-daemon :app:assembleDebug --stacktrace`

## 4) Optionaler Geräte-/UI-Check

- [ ] App auf Emulator/Device installiert, falls eine lokale Geräte-Verifikation gewünscht ist
- [ ] lokale APK-Validierung kann ohne Main-CI-Dependency erfolgen
- [ ] Haupt-Workflow bleibt auf native Host-Tests, JVM-Unit-Tests und Assemble fokussiert

## 5) Remote-Socket / Netzwerk-Szenario

- [ ] Produktive Remote-UDP-Variante nur in explizitem, bewusst aktiviertem Modus
- [ ] Loopback-/Host-Test-Pfad bleibt stabil und unverändert
- [ ] bei echter Remote-Verbindung: Zieladresse wird mit `getaddrinfo` korrekt aufgelöst
- [ ] kein Test-/Produktionsregressionsfehler bei Loopback-Probing und Host-Tests

## 6) Ergebnis und Abschluss

- [ ] Testreporte geprüft (`app/build/reports/`)
- [ ] Logcat überprüft: keine kritischen Java/Kotlin-/Native-Fehler
- [ ] Gerät-/Emulator-Lauf nur als optionale lokale Verifikation dokumentiert
- [ ] PR-/CI-Status abgeschlossen, ohne offene Blocker

## 7) Kurzform für CI-Runner

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON \
  && cmake --build build/native-tests --target client_state_test \
  && ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:assembleDebug --stacktrace
```

Weitere direkte Hinweise zur Android-/Gradle-Umgebung finden sich in `README.md`, `app/README.md`, `app/src/main/cpp/README.md` und `docs/RELEASE_CHECKLIST.md`.
