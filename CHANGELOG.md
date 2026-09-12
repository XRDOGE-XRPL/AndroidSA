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
- Native Paket- und Verbindungsstatistiken werden für zentrale Flows jetzt über reale UDP-`sendto`/`recvfrom`-Operationen statt reinem Zähler-Mocking erhoben.
- Native Event-Historie enthält dekodierte RakNet/Open:MP-orientierte Paketereignisse aus dem Bytestream-Pfad (inkl. RPC-Wrapper-Erkennung).
- NativeBridge normalisiert Snapshot-Daten robuster und propagiert native Fehlerdiagnostik deterministisch in die JVM/UI, wenn Dispatches scheitern.
- Compose-UI nutzt stärkere asynchrone Sperrlogik via Mutex für Command-Dispatch und aktualisiert Laufzeitmetriken automatisch bei aktiver Verbindung.
- Compose-UI enthält nun einen dynamischen Server-Browser mit Profilverwaltung, Direkt-Connect/Ping-Aktionen und Server-spezifischer Metrikverfolgung.
- CI-Workflow nutzt nun explizites Gradle-Warmup und Retry-Strategien für Plugin-/Dependency-Auflösung, um transiente Auflösungsfehler robuster abzufangen.
