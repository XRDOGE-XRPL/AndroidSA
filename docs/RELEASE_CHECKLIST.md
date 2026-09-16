# Release Checklist

## Ziel

Diese Checkliste bildet den finalen Release- und Qualitätsstandard des AndroidSA-Prototyps ab. Sie muss vor jedem größeren Android-Device-/Emulator-Lauf, vor einem Merge in den Hauptbranch und vor einem Release vollständig erfüllt sein.

## 0) Lokale Basisvalidierung (Pflicht)

Vor jedem Android-Emulator-/Device-Run muss die lokale Basisvalidierung erfolgreich sein. Ohne diese Stufe ist das Gerät-/Emulator-Deployment nicht als freigegeben zu betrachten.

- [ ] Repository-Root aktiv und `./gradlew` ausführbar
- [ ] lokaler Google-Maven-Proxy gestartet, falls erforderlich: `python3 tools/google_maven_proxy.py --port 38473`
- [ ] Umgebung gesetzt, falls nötig: `ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/`
- [ ] Gradle-Warmup erfolgreich: `./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies`
- [ ] Native Host-Tests erfolgreich: `cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON && cmake --build build/native-tests --target client_state_test && ctest --test-dir build/native-tests --output-on-failure`
- [ ] JVM-Unit-Tests erfolgreich: `./gradlew --no-daemon :app:testDebugUnitTest --stacktrace`
- [ ] Gesamtbuild/Checks erfolgreich: `./gradlew --no-daemon check build --stacktrace`

## 1) Architektur- und Dokumentationsstatus

- [ ] `README.md` deckt Architektur, Commands, UDP-Netzwerkmodi, Build, Test und Troubleshooting vollständig ab
- [ ] `Projectvorstellungs.md` enthält den aktuellen Projektstatus, die Komponentenstruktur und die Validierungs-Highlights
- [ ] `CHANGELOG.md` dokumentiert den finalen Prototype-Stand und alle relevanten Änderungen
- [ ] `HowtoSetup.md` enthält vollständige Setup-Anweisungen für localhost und Android-Mobile/Emulator
- [ ] `app/README.md` und `app/src/main/cpp/README.md` bleiben konsistent zum Projektstand

## 2) Technische Integrität

- [ ] C++20-Kern `ClientState` und JNI-Brücke auf denselben Summary-/Statuskontext synchronisiert
- [ ] 11-Felder Summary bleibt zwischen Kotlin und C++ unverändert konsistent
- [ ] Command-Syntax-Regeln für `transport`, `connect`, `player`, `latency`, `diagnostics`, `simulate` und `fail` unverändert gültig
- [ ] `std::mutex`-Sperren schützen Status, Event-Historie, Paketzähler und Summary-Extraktion
- [ ] Loopback- und Remote-UDP-Pfade sind sauber getrennt und dokumentiert
- [ ] keine unzusammenhängenden oder veralteten Build-/CI-Anweisungen im Repo

## 3) Funktionsprüfung

- [ ] Server-Browser-Flow funktioniert: Profil hinzufügen, auswählen, Connect und Ping
- [ ] Busy-/Mutex-State blockiert keine zweiten Dispatches und zeigt konsistente UI-Reaktionen
- [ ] Runtime-Stats korrekt: Latenz, Packets sent/received, TX/RX ratio, Last command, Connection state
- [ ] Event-Historie zeigt lesbare Paket- und Latenzereignisse an
- [ ] Loopback-Testpfad bleibt stabil, auch ohne echte Remote-Serververbindung
- [ ] Remote-UDP-Pfad kann bei bewusst konfiguriertem Remote-Target echte `sendto`/`recvfrom`-Flows ausführen

## 4) Android-/Geräte-Ready (optional)

- [ ] Android SDK 34, Android NDK `27.3.13750724` und CMake 3.22.1+ vorhanden
- [ ] `adb devices` zeigt ein gültiges Gerät oder einen laufenden Emulator
- [ ] Entwicklungsoptionen für Android-Gerät aktiv, falls physisches Gerät verwendet wird
- [ ] `minSdk 26` und `targetSdk 34` korrekt konfiguriert
- [ ] lokale APK-Verifikation ohne Fehler: `./gradlew --no-daemon :app:assembleDebug --stacktrace`
- [ ] Gerät-/Emulator-Checks sind optional und nicht Teil des Required CI-Pfads

## 5) Security / Repository Hygiene

- [ ] kein Secret, API-Key oder Token im Repository enthalten
- [ ] Beispielbefehle enthalten keine realen Server- oder Produktivdaten
- [ ] lokaler Proxy- und Build-Konfigurationen sind sauber dokumentiert und nachweisbar reproduzierbar

## 6) Release- und Abschlusskriterien

- [ ] `CHANGELOG.md` als finaler Release-Text akzeptiert
- [ ] Branch und Commit-Status in sauberem Zustand
- [ ] alle dokumentierten Checklisten und Setup-Anweisungen verifiziert
- [ ] Release-Tag/Versionierung und Deployment-Schritt vorbereitet
- [ ] Post-Release-Sanity-Checks durchgeführt

## 7) Verwendete Referenzbefehle

```bash
chmod +x ./gradlew
python3 tools/google_maven_proxy.py --port 38473
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon check build --stacktrace
./gradlew --no-daemon :app:assembleDebug --stacktrace
```
