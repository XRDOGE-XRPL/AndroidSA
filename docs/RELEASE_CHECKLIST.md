# Release Checklist

## 1) Inhalt und Dokumentation

- [ ] `CHANGELOG.md` unter `Unreleased` vollständig aktualisiert
- [ ] `README.md`, `app/README.md` und `app/src/main/cpp/README.md` spiegeln den aktuellen Stand wider
- [ ] `Projectvorstellungs.md` wurde bei relevanten Produktänderungen mitgepflegt
- [ ] Bekannte Risiken, offene Punkte und Einschränkungen sind dokumentiert

## 2) Technische Validierung

- [ ] Gradle Wrapper ist ausführbar (`chmod +x ./gradlew`)
- [ ] Gesamtbuild erfolgreich: `./gradlew build`
- [ ] Checks erfolgreich: `./gradlew check build`
- [ ] App-Unit-Tests erfolgreich: `./gradlew :app:testDebugUnitTest`
- [ ] Native Host-Tests erfolgreich (`client_state_test` via CMake/CTest)
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
