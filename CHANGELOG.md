# Changelog

Alle relevanten Änderungen dieses Projekts werden in dieser Datei dokumentiert.

## [Unreleased]

### Added

- Erweiterter nativer Laufzeitstatus mit Serverprofil, Spielerprofil, Latenz, Paket-Zählern, Verbindungsversuchen, letztem Command und Event-Historie.
- Neue native Commands:
  - `connect:<server>` für direkten Verbindungsaufbau mit Serverprofil
  - `reconnect` für erneuten Verbindungsaufbau
  - `player:<name>` für Spielerprofil-Wechsel
  - `latency:<ms>` für Latenz-Simulation
  - `simulate:rx` und `simulate:tx` für Traffic-Simulation
  - `fail:<reason>` für reproduzierbare Fehlerzustände
- Geführte Compose-Steuerung für Server, Spieler, Transport, Latenz, Diagnostics und Event-Ansicht.
- JNI-Event-Log-Bridge über `nativeGetRecentEvents()`.

### Changed

- Native Summary von 4 auf 11 Felder erweitert, damit die UI einen vollständigen Laufzeit-Snapshot anzeigen kann.
- Command-Validierung in Kotlin erweitert auf `connect:<server>`, `player:<name>`, `latency:<ms>`, `simulate:<value>` und `fail:<reason>`.
- Native Command-Verarbeitung in C++ erweitert um reichere Zustandsübergänge, Statistiken und Event-Aufzeichnung.
- Kotlin- und Native-Tests decken jetzt Parsing, Event-Logik und neue Commands ab.
- Dokumentation für Architektur, Commands und Laufzeitverhalten erweitert.
