# Changelog

Alle relevanten Änderungen dieses Projekts werden in dieser Datei dokumentiert.

## [Unreleased]

### Added

- vollständige UI-Test-Abdeckung für Runtime-Workflows, Serverprofil-Interaktionen und Event-Historie
- C++-Socket-Erweiterungen für loopback- und real-udp-/remote-udp-basierte Probe-Flows
- Native Host-Test-Integration über CMake/CTest mit einem `client_state_test`-Ziel
- erweitertes JNI-/Summary-Schema mit 11 Laufzeitfeldern und deterministischer Fehlernormalisierung
- erweiterter Command-Satz inklusive `connect:<server>`, `transport:<value>`, `diagnostics:<text>`, `simulate:rx|tx` und `fail:<reason>`
- vollständige Dokumentation der Projektarchitektur, Build- und Testvalidierung sowie Release-Voraussetzungen

### Changed

- Architektur-Doku auf C++20-Kern + JNI-Bridge + Compose-UI aktualisiert und mit exakten Build-/Testbefehlen ergänzt
- Laufzeitstatus auf 11 Felder erweitert und in Kotlin/Nativschnittstelle konsistent synchronisiert
- UDP-Probe- und Event-Logik von reinem Zähler-Mocking auf reale `sendto`/`recvfrom`-Flüsse und dekodierte Paket-Events erweitert
- Kotlin- und C++-Validierung auf exakte Command-Regeln und robustere Fehlerdiagnostik erweitert
- CMake-Konfiguration und Host-Test-Setup stabilisiert, inklusive sauberer `build/native-tests`-Ausgabe und `ctest`-Integration
- UI- und Bridge-Flow auf Mutex-basierte Serialisierung und konsistente Latenz-/Paketaktualisierung angepasst

### Fixed

- CMake- und Host-Test-Sets für native Laufzeit- und Zustandsvalidierung korrigiert
- falsche oder unvollständige Summary-/Parser-Interpretationen in der JVM-Seite bereinigt
- Fälle mit fehlerhaften UDP-Loopback-/Remote-Resolving-Prozessen und fehlenden Event-Einträgen stabilisiert
- transiente Gradle-/Plugin-Auflösungsprobleme durch Warmup- und Retry-Strategie in den dokumentierten CI-Checks entschärft

### Documentation

- README, Projektvorstellung und Release-Checklist auf den aktuellen Projektstatus und das gültige Befehls- und Test-Set aktualisiert
- lokale Validierung als Pflichtvoraussetzung vor emulatorbasierten Android-CI-Läufen dokumentiert
- UDP-Netzwerkmodi (Loopback und real-udp / remote-udp) und die Mutex-/Event-Historie klar beschrieben
