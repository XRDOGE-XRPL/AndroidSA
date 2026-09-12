# Release Checklist

## 1) Versions- und Inhaltsprüfung

- [ ] `CHANGELOG.md` (`Unreleased`) enthält alle relevanten Änderungen
- [ ] Release-Umfang (Features/Fixes/Doku) ist mit dem Team abgestimmt
- [ ] Bekannte Risiken/Offene Punkte sind dokumentiert

## 2) Technische Validierung

- [ ] Lokaler Build erfolgreich: `./gradlew build`
- [ ] Lokale Checks erfolgreich: `./gradlew check build`
- [ ] Unit-Tests erfolgreich: `./gradlew :app:testDebugUnitTest`
- [ ] CI-Workflow `android-background-build.yml` ist grün
- [ ] CI-Artefakte (Testreports) wurden geprüft

## 3) Sicherheits- und Qualitätsprüfung

- [ ] Secret-Scan auf geänderten Dateien durchgeführt
- [ ] Command-Validierungsregeln (`transport:<value>`, `diagnostics:<value>`) bleiben eingehalten
- [ ] Keine unerwünschten Änderungen außerhalb des Release-Scopes

## 4) Veröffentlichung

- [ ] Release Notes aus `CHANGELOG.md` erstellt
- [ ] Versionskennung/Tag gesetzt
- [ ] Release in Zielsystem veröffentlicht
- [ ] Post-Release-Sanity-Checks durchgeführt
