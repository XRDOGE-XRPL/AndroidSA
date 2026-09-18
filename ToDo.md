# ToDo

## Dokumentation & Projektstruktur
- [x] README/Architektur-Dokumentation auf aktuellem Stand prüfen und mit Naht-B-Scope und Host-/Runtime-Guardrails ergänzen.
- [x] `app/README.md` und `app/src/main/cpp/README.md` mit erweiterten Beispielen für Command- und UDP-Workflow verfeinern.
- [x] Setup-/Troubleshooting-Leitfaden für lokale Android- und NDK-Umgebungen ergänzen.
- [x] Das gesamte Markdown-Set auf die reale Projektpositionierung und technische Grenzen ausrichten.

## Naht B – konkrete Reihenfolge
- [x] Detect + Launch: Pkg-Check, Version, Launch-Intent, fehlende App-Meldung ohne Fake-Stream.
- [x] Lokaler Stream: State-Basis, Live-/Capture-Status, `stream:*`-Commands und Source-Auswahl.
- [ ] Diagnose/Dashboard: Event-Kategorien und Stream-Overlay mit sichtbaren Host-Metriken weiter festigen.
- [ ] Query/Muster: Open:MP-/Ping-Observability als Nebenkanal ohne Join-/Sync-Client.
- [ ] MP später: echte Spiel-/Gameplay-Schritte erst nach stabiler Stream-/Runtime-Sichtbarkeit.

## Qualität & Tests
- [ ] Android JVM-Unit-Testpfad mit lokalem Maven-Proxy und Repeatable setup gegen Google Maven absichern.
- [ ] Native Host-Tests um weitere Randfälle für Packet-/RPC-Parsing erweitern.
- [ ] Kotlin/Compose-UI-Regressionstests für Standard-Commands und Fehlerzustände ergänzen.

## Release & CI-Resilience
- [x] CI-Workflow auf den finalen Non-Emulator-Release-Pfad mit nativen Tests + JVM-Tests + Assemble festigen.
- [x] Proxy- und Retry-Strategien dokumentieren, falls Google Maven oder Android-Dependencies transient fehlschlagen.
- [x] Release-Hinweise und Changelog mit realen Verlauf/Protokoll-Dateien konsolidieren.

## Dokumentation-Guardrails
- [x] Beim nächsten größeren Feature- oder Merge-Schritt immer den gesamten Markdown-Satz prüfen und auf echte Produktgrenzen abstimmen.
- [x] Keine Dokumentationsänderung ohne Synchronisierung zwischen root README, app README, native README und Release-/Ready-Checklisten.
- [x] Produktive Aussagen immer an den realen technischen Stack koppeln: AndroidSA ist Diagnostik/Launcher/Probe, keine fertige Gameplay-Integration.
