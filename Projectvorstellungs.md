# Projektvorstellung: AndroidSA

## Kurzbeschreibung

AndroidSA ist ein Android-Prototyp für einen SA:MP- / Open:MP-orientierten Client. Das Projekt verbindet eine moderne Jetpack-Compose-Oberfläche mit einer Kotlin/JNI-Brücke und einem nativen C++20-Kern.

## Was das Projekt zeigt

AndroidSA demonstriert, wie ein nativer Multiplayer-nahe Laufzeitkern sauber in eine Android-App eingebunden werden kann. Die App macht interne Zustände sichtbar, erlaubt geführte und manuelle Commands und bildet Netzwerkverhalten über reproduzierbare UDP-Probes ab.

## Zentrale Bestandteile

### Android-Oberfläche

Die Compose-UI zeigt:

- aktuellen Verbindungsstatus
- Transportprofil
- Serveradresse und Spielername
- Latenz und Paketstatistiken
- letzte Commands und native Ereignisse

Zusätzlich bietet sie einen kleinen Server-Browser mit Standard- und benutzerdefinierten Profilen.

### Kotlin/JNI-Bridge

Die Bridge validiert Commands, lädt die Native-Library, liest Laufzeitdaten aus und normalisiert Fehlerzustände für die UI.

### Nativer Kern in C++20

Der C++-Layer verwaltet den Clientzustand thread-sicher, verarbeitet Commands und simuliert Netzwerkaktivität über Loopback-UDP. Empfangene Probe-Pakete werden als lesbare Events aufbereitet.

## Typische Einsatzfelder im aktuellen Stand

- technisches Grundgerüst für spätere Multiplayer-Clientlogik
- Demo- und Testprojekt für JNI-Kommunikation zwischen Kotlin und C++
- Basis für Diagnose-, Status- und Verbindungs-Workflows auf Android
- Übungs- und Referenzprojekt für Compose + NDK + CMake in einem Repository

## Technische Highlights

- Jetpack Compose als UI-Schicht
- Kotlin mit JVM-Target 17 und Java 17
- Android SDK 34 und minSdk 26
- C++20 mit CMake
- native Host-Tests plus JVM-Unit-Tests
- GitHub Actions Workflow für Build, Tests und Artefakte

## Aktueller Mehrwert des Repositories

Das Repository ist bereits so strukturiert, dass UI, Bridge und Native-Layer unabhängig weiterentwickelt werden können. Gleichzeitig bleibt das Verhalten durch vorhandene Tests und dokumentierte Command-Regeln nachvollziehbar.

## Für wen das Projekt interessant ist

- Android-Entwickler mit Interesse an NDK-Integration
- Entwickler, die Kotlin- und C++-Interop evaluieren möchten
- Teams, die einen kontrollierten Multiplayer-nahen Clientzustand visualisieren wollen
- Mitwirkende, die ein klar dokumentiertes Compose/NDK-Beispiel suchen

## Vollständiges Setup und Durchführung `run test`

### Setup

1. Repository lokal öffnen und in das Repository-Root wechseln.
2. Gradle Wrapper freischalten:

   ```bash
   chmod +x ./gradlew
   ```

3. Notwendige Toolchain:
   - JDK 17
   - Android SDK 34
   - Android NDK `27.3.13750724`
   - CMake 3.22.1+

### Testdurchführung

Für den vollständigen projektweiten `run test` mit allen Schritten (Gradle-Warmup, JVM-Tests, native Host-Tests, abschließendes `check build`) siehe `/README.md`.
