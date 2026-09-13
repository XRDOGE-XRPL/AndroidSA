# Release Checklist

## 0) Vollständiges Setup und Durchführung `run test`

- [ ] Befehle werden im Repository-Root ausgeführt
- [ ] Gradle Wrapper ist ausführbar (`chmod +x ./gradlew`)
- [ ] Toolchain ist installiert (JDK 17, Android SDK 34, NDK `27.3.13750724`, CMake 3.22.1+)
- [ ] Optionaler Google-Maven-Proxy bei blockierten Netzwerken gestartet (`python3 tools/google_maven_proxy.py --port 38473`)
- [ ] Gradle-Warmup erfolgreich (`./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies`)
- [ ] JVM-Tests erfolgreich (`./gradlew --no-daemon :app:testDebugUnitTest --stacktrace`)
- [ ] Native Host-Tests erfolgreich (`cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON && cmake --build build/native-tests --target client_state_test && ctest --test-dir build/native-tests --output-on-failure`)
- [ ] Vollständige Validierung erfolgreich (`./gradlew --no-daemon check build --stacktrace`)

## 1) Inhalt und Dokumentation

- [ ] `CHANGELOG.md` unter `Unreleased` vollständig aktualisiert
- [ ] `README.md`, `app/README.md` und `app/src/main/cpp/README.md` spiegeln den aktuellen Stand wider
- [ ] `Projectvorstellungs.md` wurde bei relevanten Produktänderungen mitgepflegt
- [ ] `ToDo.md` wurde geprüft und offene Doku-/Technikpunkte sind eingeplant
- [ ] Bekannte Risiken, offene Punkte und Einschränkungen sind dokumentiert

## 2) Technische Validierung

- [ ] Gradle Wrapper ist ausführbar (`chmod +x ./gradlew`)
- [ ] Gesamtbuild erfolgreich: `./gradlew build`
- [ ] Checks erfolgreich: `./gradlew check build`
- [ ] App-Unit-Tests erfolgreich: `./gradlew :app:testDebugUnitTest`
- [ ] Native Host-Tests erfolgreich (`client_state_test` via CMake/CTest, siehe `app/src/main/cpp/README.md`)
- [ ] CI-Workflow `android-background-build.yml` ist grün
- [ ] CI-Artefakte und Testreports wurden geprüft

## 3) Funktions- und Qualitätsprüfung

- [ ] Summary-Format zwischen Kotlin und C++ ist konsistent
- [ ] Command-Regeln (`transport`, `connect`, `player`, `latency`, `diagnostics`, `simulate`, `fail`) bleiben synchron
- [ ] UI zeigt Status, Diagnostics, Server-/Playerprofil und Runtime-Metriken korrekt an
- [ ] Keine unerwünschten Änderungen außerhalb des Release-Scopes enthalten

## 4) Sicherheitsprüfung

- [ ] Secret-Scan auf allen geänderten Dateien durchgeführt
- [ ] Keine Secrets oder Zugangsdaten im Repository enthalten
- [ ] Dokumentierte Commands und Beispiele enthalten keine sensiblen Produktionsdaten

## 5) Veröffentlichung

- [ ] Release Notes aus `CHANGELOG.md` erstellt
- [ ] Versionskennung und Tag gesetzt
- [ ] Release im Zielsystem veröffentlicht
- [ ] Post-Release-Sanity-Checks durchgeführt

## 6) Nachverfolgung nach Release

- [ ] Offene Punkte in `ToDo.md` nach dem Release aktualisiert/priorisiert
- [ ] Nicht abgeschlossene Release-Aufgaben in Issues oder Folge-PRs überführt
