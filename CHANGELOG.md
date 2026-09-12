# Changelog

Alle relevanten Änderungen dieses Projekts werden in dieser Datei dokumentiert.

## [Unreleased]

### Added

- Neue native Commands:
  - `status` für Diagnose-Snapshot ohne Zustandswechsel
  - `diagnostics:<text>` für manuelle Diagnostikmeldungen
- Quick-Action-Buttons in der Compose-UI für häufige Commands (`ping`, `connect`, `status`, `disconnect`, `reset`, `diagnostics:ok`)
- CI-Verbesserung: expliziter Unit-Test-Schritt (`:app:testDebugUnitTest`) vor Gesamtbuild
- CI-Artefakt-Upload für Unit-Test-Reports
- Release-Vorbereitungsdokument unter `docs/RELEASE_CHECKLIST.md`

### Changed

- Command-Validierung in Kotlin erweitert auf `diagnostics:<value>` mit denselben Trennzeichenregeln wie bei `transport`.
- Native Command-Verarbeitung in C++ erweitert um `status` und `diagnostics:<value>`.
- Dokumentation für Command-Spezifikation, CI und Release-Ablauf erweitert.
