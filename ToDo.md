# ToDo

## Dokumentation & Projektstruktur
- [x] README/Architektur-Dokumentation auf aktuellem Stand prüfen und mit Release-/CI-Checklisten ergänzen.
- [x] `app/README.md` und `app/src/main/cpp/README.md` mit erweiterten Beispielen für Command- und UDP-Workflow verfeinern.
- [x] Setup-/Troubleshooting-Leitfaden für lokale Android- und NDK-Umgebungen ergänzen.
- [x] Das gesamte Markdown-Set auf die reale Projektpositionierung und technische Grenzen ausrichten.

## Qualität & Tests
- [ ] Android JVM-Unit-Testpfad mit lokalem Maven-Proxy und Repeatable setup gegen Google Maven absichern.
- [ ] Native Host-Tests um weitere Randfälle für Packet-/RPC-Parsing erweitern.
- [ ] Kotlin/Compose-UI-Regressionstests für Standard-Commands und Fehlerzustände ergänzen.

## Release & CI-Resilience
- [x] CI-Workflow auf den finalen Non-Emulator-Release-Pfad mit nativen Tests + JVM-Tests + Assemble festigen.
- [x] Proxy- und Retry-Strategien dokumentieren, falls Google Maven oder Android-Dependencies transient fehlschlagen.
- [x] Release-Hinweise und Changelog mit realen Verlauf/Protokoll-Dateien konsolidieren.

## Produktivitäts- und Feature-Prioritäten
- [ ] Serverprofile, Last-States und Nutzungsspuren für Wiederholungs- und Monitoring-Checks erweitern.
- [ ] Event-History, Status-Filter und Diagnose-Kategorien für bessere Debugging-Übersicht verbessern.
- [ ] Optionale Erweiterungen für echte SA:MP/Open:MP-Serverinteraktion in die Dokumentation aufnehmen.

## Dokumentation-Guardrails
- [ ] Beim nächsten größeren Feature- oder Merge-Schritt immer den gesamten Markdown-Satz prüfen und auf echte Produktgrenzen abstimmen.
- [ ] Keine Dokumentationsänderung ohne Synchronisierung zwischen root README, app README, native README und Release-/Ready-Checklisten.
- [ ] Produktive Aussagen immer an den realen technischen Stack koppeln: AndroidSA ist Diagnostik/Launcher/Probe, keine fertige Gameplay-Integration.
