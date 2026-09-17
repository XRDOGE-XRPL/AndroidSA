# ToDo

## Dokumentation & Projektstruktur
- [ ] README/Architektur-Dokumentation auf aktuellem Stand prüfen und bei Bedarf mit Release-/CI-Checklisten ergänzen.
- [ ] `app/README.md` und `app/src/main/cpp/README.md` mit erweiterten Beispielen für Command- und UDP-Workflow verfeinern.
- [ ] Setup-/Troubleshooting-Leitfaden für lokale Android- und NDK-Umgebungen ergänzen.

## Qualität & Tests
- [ ] Android JVM-Unit-Testpfad mit lokalem Maven-Proxy und Repeatable setup gegen Google Maven absichern.
- [ ] Native Host-Tests um weitere Randfälle für Packet-/RPC-Parsing erweitern.
- [ ] Kotlin/Compose-UI-Regressionstests für Standard-Commands und Fehlerzustände ergänzen.

## Release & CI-Resilience
- [ ] CI-Workflow auf den finalen Non-Emulator-Release-Pfad mit nativen Tests + JVM-Tests + Assemble festigen.
- [ ] Proxy- und Retry-Strategien dokumentieren, falls Google Maven oder Android-Dependencies transient fehlschlagen.
- [ ] Release-Hinweise und Changelog mit realen Verlauf/Protokoll-Dateien konsolidieren.

## Produktivitäts- und Feature-Prioritäten
- [ ] Serverprofile, Last-States und Nutzungsspuren für Wiederholungs- und Monitoring-Checks erweitern.
- [ ] Event-History, Status-Filter und Diagnose-Kategorien für bessere Debugging-Übersicht verbessern.
- [ ] Optionale Erweiterungen für echte SA:MP/Open:MP-Serverinteraktion in die Dokumentation aufnehmen.
