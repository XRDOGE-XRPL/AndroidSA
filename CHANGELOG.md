# Changelog

Alle relevanten Änderungen dieses Projekts werden in dieser Datei dokumentiert.

## [Final / Prototype Completion]

### Added

- vollständige Jetpack-Compose-Oberfläche mit Server-Browser, Runtime-Stats, Event-Historie und Action-Controls
- vollständige Kotlin/JNI-Bridge mit `NativeBridge`, 11-Felder-Summary-Parsing und robusten Fallbacks
- C++20-Kern in `ClientState` mit Mutex-geschütztem Zustand, event-basierter Diagnose und live Paketzählung
- echte UDP-Probe-Flows mit `sendto`/`recvfrom` für Loopback- und Remote-Endpunkte
- Paket-/RPC-Erkennung für RakNet- und Open:MP/SA:MP-Wrapper-Events
- native Host-Tests über CMake/CTest mit `client_state_test`
- lokale Google-Maven-Proxy- und Warmup-Strategie für blockierte Build-Umgebungen
- finalen GitHub-Actions-Workflow mit stabiler Fokussierung auf native Host-Tests, JVM-Unit-Tests und Assemble
- nachvollziehbare Release-/Setup-Dokumentation mit finaler CI-Strategie und resolventem Proxy-Fallback

### Changed

- Laufzeitmodell von einer reinen Simulation auf echte zustandsbasierte, eventgetriebene Client-Statuslogik erweitert
- Summary-Format auf exakt 11 Felder konsolidiert und zwischen C++-Kern und Kotlin-Bridge synchronisiert
- UDP-Logik von reinem Loopback-Mocking auf echte Remote-Auflösung via `getaddrinfo` und echte socket-basierte Probe-Kommunikation erweitert
- Command-Syntax und Validierung auf exakte Regeln für `transport:<value>`, `connect:<server>`, `latency:<ms>`, `simulate:rx|tx`, `fail:<reason>` und `diagnostics:<text>` erweitert
- README, Setup-Doku und Changelog auf den finalen, stabilen CI-Stand mit nativen Host-Tests, JVM-Unit-Tests und Assemble angepasst
- instabile Emulator-UI-Tests aus der Standard-CI und den dokumentierten Hauptpfaden entfernt

### Fixed

- fehlerhafte Loopback/Remote-Unterscheidung in der UDP-Erkennung korrigiert
- fehlerhafte Parsing-Fallbacks für leere oder fehlerhafte Summary-Segmente bereinigt
- Zustandsinkonsistenzen im Event-Log durch mutexbasierte Serialisierung behoben
- Eintragungen für Paketzähler, Verbindungsversuche und letzte Commands in UI und native Summary konsistent synchronisiert
- Build-/Resolver-Probleme in blockierten Netzwerken durch lokalen Proxy und dokumentierte Warmup-Schritte entschränkt
- CI-Instabilität durch Austragung der Emulator-UI-Tests aus dem primären GitHub-Actions-Workflow behoben

### Documentation

- vollumfängliche Repository-Doku für Architektur, UDP-Modi, Setup, CI und Release-Checks
- Integration der vollständigen local-host / Android-mobile Setup-Anleitung in `HowtoSetup.md`
- klare Erläuterung von Loopback-Probing vs. real-udp / remote-udp, Mutex-Sicherheit und Event-Historie
- finale Release-Checkliste für lokale Validierung vor Android-/Emulator-CI

## [Unreleased]

### Added

- C++-Socket-Erweiterungen für Loopback- und Remote-UDP-Probe-Flows
- Host-Test-Ausführung für `ClientState` über CMake/CTest
- erweiterte `NativeBridge`-Validierung und Fallback-Regeln
- Dokumentation der Android-/Gradle-Resolver-Resilienz für blockierte Google-Maven-Umgebungen

### Changed

- Architektur-Doku auf C++20-Kern + JNI-Bridge + Compose-UI aktualisiert
- Laufzeitstatus auf 11 Felder erweitert
- UDP-Probe- und Event-Logik von reinem Zähler-Mocking auf reale `sendto`/`recvfrom`-Flows erweitert
- UI- und Bridge-Flow auf Mutex-basierte Serialisierung und konsistente Statusaktualisierung angepasst

### Fixed

- falsche Summary-/Parser-Interpretationen in JVM- und native State-Schicht bereinigt
- abgebrochene oder unvollständige Event-Einträge und UDP-Loopback-Fehler stabilisiert
- transiente Gradle-/Plugin-Auflösungsprobleme durch Warmup-Strategie dokumentiert

### Documentation

- Projekt-Doku und Release-Checklist auf den aktuellen Stand gebracht
- UDP-Netzwerkmodi und Event-Historie dokumentiert
