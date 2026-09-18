# AndroidSA

AndroidSA ist ein Android-Prototyp für eine SA:MP-/Open:MP-orientierte Diagnose-, Probe- und Launcher-Schicht. Das Repository verbindet eine moderne Jetpack-Compose-Oberfläche mit einer Kotlin/JNI-Brücke und einem nativen C++20-Kern, um Verbindungsstatus, Serverprofile, Diagnosemeldungen, Laufzeitstatistiken, Event-Historie und echte UDP-Probe-Flows in einer klaren, testbaren Architektur zu modellieren.

„AndroidSA startet und beobachtet die lokal installierte GTA-SA-Mobile-Laufzeit. Online kommt später in diese Laufzeit, nicht ins Compose-Dashboard.“

## Realistischer Projektumfang

AndroidSA ist bewusst keine fertige GTA-SA-Mobile-Gameplay-Integration und kein behaupteter Open:MP-Android-Client. Der Fokus liegt auf:

- Serverliste, Ping, Query-/Probe-Status und Verbindungsdiagnostik
- UDP-/Network-Analyse, Paketzähler, RPC-/Wrapper-Erkennung und Laufzeitlogs
- Launcher-/Status-UI als echte Diagnose- und Betriebsoberfläche
- dokumentierte Trennung zwischen Netzwerk-/RakNet-Signal-Erkennung und tatsächlicher Gameplay-Synchronisierung

Nicht im Scope sind:

- echtes Joinen in eine Spielwelt mit Synchronisierung
- RenderWare- oder GTA-SA-Mobile-Gameplay-Injektion
- Portierung von `omp-client.dll` oder PC-GTA-SA-Assets auf Android
- der Anspruch, Open:MP selbst sei ein fertiger Handy-Client

Open:MP bleibt ein Server-/Launcher- und PC-Ökosystem; AndroidSA modelliert die diagnostische Server-/Netzwerk-Seite, nicht ein vollständiges Spiel-Client-Backend. Der RakNet-/Open:MP-Teil beginnt bewusst mit der Erkennung und Klassifizierung von Packetsignalen, nicht mit Gameplay-Synchronisierung.

## Implementierungs-Reihenfolge und Scope-Guardrails

AndroidSA verfolgt eine klare Reihenfolge, damit die Projektgrenze nicht verwässert wird:

1. GTA APK / Stream als erste Implementierungsbasis
   - Der reale Datenfluss der GTA-Laufzeit muss erst identifiziert und stabilisiert werden.
   - AndroidSA darf den Stream-, Runtime- und Frame-Input erst dann als Grundlage für Diagnostik betrachten, wenn die Quelle verlässlich ist.

2. Native Bridge und Transport-Validierung
   - Kotlin/JNI-Brücke und native Laufzeit müssen echte Runtime-Events, UDP-/Socket-Proben und Paket-Signale sauber erfassen.
   - Der Fokus liegt auf Status, Diagnose und Signalverifikation, nicht auf vollständiger Spiel-Client-Logik.

3. Event-Diagnostik und Dashboard verstärken
   - Event-Kategorien, Historie, Filterung und Zustandsdarstellung werden auf Sichtbarkeit des Streams ausgerichtet.
   - Die Oberfläche ist ein Diagnose- und Betriebsdashboard, keine Spieloberfläche.

4. RakNet/Open:MP-Mapping nur als Observability
   - Pakete, Wrapper und Signale werden nur als beobachtbare Protokollmuster interpretiert.
   - Diese Interpretation dient der Analyse und Entdeckung, nicht als Startpunkt für einen echten Android-Client.

5. Erst dann echte Spiel-/Multiplayer-Schritte
   - Sobald der GTA-APK-Stream stabil läuft und die Laufzeit zuverlässig durchläuft, kann man SA:MP-/Open:MP-Interpretation und konkrete Spielzustands-/Netzwerk-Analyse starten.
   - Gameplay-relevante Logik ist erst dann legitim, wenn der Stream-/Runtime-Input als gesichert gilt.

6. Kein SA:MP-Client vor einem echten Stream-Fundament
   - AndroidSA bleibt bewusst ein Diagnostics-/Probe-Layer.
   - Es gibt keine Gameplay-Synchronisation, kein direkter GTA-SA-Client und keine SA:MP-Join-Logik vor einem realen APK-Stream.

## Dokumentations-Map

Das Repository enthält die zentralen Markdown-Dateien:

- `README.md` – Projekt- und Scope-Dokumentation
- `Projectvorstellungs.md` – technische Projektvorstellung und Produktlage
- `HowtoSetup.md` – lokale Setup-/Build-/Test-Anleitung
- `CHANGELOG.md` – Release- und Funktionsfortschritt
- `ToDo.md` – offene Aufgaben und Prioritäten
- `app/README.md` – Modul-Dokumentation für die Android-App
- `app/src/main/cpp/README.md` – Native-Layer-Dokumentation
- `docs/ANDROID_DEVICE_CI_READY_CHECKLIST.md` – optionaler Geräte-/Emulator-Check
- `docs/RELEASE_CHECKLIST.md` – finale Release-Gate-Checkliste
- `docs/GTA_SA_MOBILE_SYSTEMANALYSE.md` – APK-/Dateisystem-/Runtime-Analyse der echten `com.rockstargames.gtasager`-App-Struktur

## Projektstatus

Der Main-Branch ist nach dem erfolgreichen Merge von PR #30 in einem stabilen Release- und Betriebsstatus. Die Standard-Validierung des Repositories ist vollständig auf den nicht-emulatorgebundenen Produktivpfad ausgerichtet:

- native C++-Host-Tests über CMake/CTest (`client_state_test`)
- Gradle-JVM-Unit-Tests
- finaler Assemble-Schritt für App-Artefakte
- optionales Android-Gerät/Emulator nur für lokale APK- und Geräte-Verifikation

Der Emulator-UI-Pfad ist keine Required-Guardrail mehr und wird weder im Standard-CI-Workflow noch in den produktiven Setup-/Release-Anweisungen als Pflichtpfad geführt.

## Architektur

### 1. Jetpack Compose UI

Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/MainActivity.kt`

Die UI zeigt:

- Verbindungsstatus, Transport, Serveradresse, Player, Diagnostics
- Latenz, Paketzähler, TX/RX-Ratio, Reconnect-Versuche und letzter Command
- Server-Browser mit Standard- und benutzerdefinierten Profilen
- Kontrollaktionen für Connect, Ping, Reconnect, Disconnect, Reset, Status, Latency, Simulate, Transport und Player
- Event-Historie mit kompakten, lesbaren Einträgen
- Validierungsfeedback bei fehlerhaften Native-Commands

### 2. Kotlin/JNI-Brücke

Datei: `app/src/main/java/com/xrdoge/xrpl/androidsa/NativeBridge.kt`

Die Java/Kotlin-Brücke übernimmt:

- Laden der nativen Bibliothek `androidsa`
- Validierung und Normalisierung eingehender Commands
- Parsing des elfteiligen nativen Summary-Strings
- Event-Parsing, Begrenzung der Historie und Normalisierung von Fehlerzuständen
- Rückgabe eines konsistenten `NativeClientSnapshot` mit Overview + RecentEvents

Die native Laufzeit liefert ein 11-Felder Summary mit dem Format:

```text
AndroidSA|<transport>|<state>|<diagnostics>|<server>|<player>|<latencyMs>|<packetsSent>|<packetsReceived>|<connectionAttempts>|<lastCommand>
```

### 3. C++20-Kern

Dateien:

- `app/src/main/cpp/native-lib.cpp`
- `app/src/main/cpp/native/network/ClientState.h`
- `app/src/main/cpp/native/network/ClientState.cpp`
- `app/src/main/cpp/native/logging/Logger.h`
- `app/src/main/cpp/native/logging/Logger.cpp`
- `app/src/main/cpp/native/network/ClientStateTest.cpp`

Der native Kern verwaltet den Laufzeitzustand, validiert Commands, erzeugt die Summary-Zeichenkette, verwaltet Paket- und Event-Zähler und führt UDP-Probes aus. Der Zustand wird unter `std::mutex` geschützt; Dispatches, Event-Log und Paketzähler laufen somit deterministisch und thread-sicher.

## Paketsignale und Protokollanalyse

AndroidSA arbeitet bewusst nur mit einer diagnostischen Signal- und Paketschicht. Die wichtigsten Marker sind:

- `0x00` → RakNet connected ping
- `0x1c` → RakNet open connection request
- `0x1d` → RakNet open connection reply
- `0x7d` → Open:MP/SA:MP RPC wrapper

Diese Signale dienen als Diagnose- und Health-Feedback; sie sind kein Nachweis für ein spielbares Mobile-Client-Backend.

## Befehls- und Datenmodell

Die JVM- und native Validierung akzeptieren diese Befehle mit genauer Syntax:

- `ping`
- `connect`
- `connect:<server>`
- `reconnect`
- `disconnect`
- `reset`
- `status`
- `transport:<name>`
- `player:<name>`
- `latency:<ms>`
- `diagnostics:<text>`
- `stream:start`
- `stream:stop`
- `stream:info`
- `simulate:rx`
- `simulate:tx`
- `fail:<reason>`

Wichtige Regeln:

- keine Steuerzeichen
- kein `|` im Command
- max. 64 Zeichen
- Wertbefehle müssen exakt `keyword:<value>` verwenden
- `latency:<ms>` nur nicht-negative Integer
- `simulate:<value>` nur `rx` oder `tx`
- `transport:<value>` case-insensitive, aber genau mit `:` und ohne Leerzeichen davor

## UDP-Netzwerkarchitektur

Der native Layer unterstützt zwei Betriebsarten für UDP-Probe- und Status-Flows.

### Loopback-Modus

Der Loopback-Pfad nutzt `socket(AF_INET, SOCK_DGRAM, 0)`, bindet auf `127.0.0.1` oder `INADDR_LOOPBACK`, arbeitet non-blocking und lässt native Host-Tests deterministisch und reproduzierbar laufen. Dabei werden echte `sendto`/`recvfrom`-Aufrufe verwendet, aber das Ziel bleibt lokal. Damit bleibt der Testpfad stabil, ohne ein externes Remote-Target zu benötigen.

Typische Hostnamen bzw. Adressen im Loopback-Set:

- `localhost`
- `127.0.0.1`
- `::1`
- `loopback`
- `0.0.0.0`

### Real UDP / Remote UDP

Für echte Remote-Endpunkte wird bei einem Host, der nicht loopback-ähnlich ist, das Ziel mit `getaddrinfo()` aufgelöst und als `sockaddr_in` gesetzt. Anschließend werden echte UDP-Pakete über `sendto()` und `recvfrom()` an den entfernten Host gesendet. Das Verhalten ist bewusst getrennt vom Test-Loopback-Pfad; erstellt wird nur dann ein Remote-UDP-Socket, wenn der Adresskontext ein echtes Remote-Target ist.

Die Logik prüft dabei explizit:

- Hostname/Adress-Parsing via `host:port`
- Portvalidierung (1..65535)
- `isLoopbackLikeHost()`-Unterscheidung
- `getaddrinfo()` für echte Remote-Targets
- `bind()` auf localhost/loopback bei lokalen Tests
- `sendto`/`recvfrom` mit Event-Logging und Paketzählung

Gerade im Remote-Pfad werden Paket-IDs und Open:MP/RakNet-Wrapper-Details dekodiert und in lesbare Eventstrings wie `RX Open:MP/SA:MP RPC wrapper` umgesetzt. Dadurch ist der Zustand für UI, Logs und Debugging sofort nutzbar.

## Event-Historie und Mutex-Sicherheit

Die Event-Historie ist eine kompakte, deterministische Folge von Events aus dem nativen Layer. Sie enthält unter anderem:

- `Ping acknowledged by native runtime`
- `Connected to ... (udp tx=..., rx=...)`
- `Reconnect flow completed ...`
- `RX RakNet connected ping`
- `RX Open:MP/SA:MP RPC wrapper ...`
- `Session reset to initial state`

Zentrale Synchronisation:

- `std::mutex mutex_` schützt Laufzeitstatus, Paketzähler, Event-Historie und Summaries
- `std::lock_guard` umschließt `summary()`, `recentEvents()` und `dispatchCommand()`
- UI-Seite verwendet ebenfalls einen `Mutex` für die Command-Dispatch-Serialisierung

Dadurch bleiben Paketzähler, Statusübergänge und Event-Log konsistent, auch bei parallelen Anfragen oder schnellen UI-Aktionen.

## Repository-Struktur

```text
AndroidSA/
├── .github/
│   └── workflows/
│       ├── android-background-build.yml
│       └── copilot-setup-steps.yml
├── app/
│   ├── README.md
│   ├── build.gradle.kts
│   └── src/
│       ├── androidTest/java/com/xrdoge/xrpl/androidsa/MainActivityUiTest.kt
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt
│       │   │   ├── README.md
│       │   │   ├── native-lib.cpp
│       │   │   └── native/
│       │   │       ├── logging/
│       │   │       └── network/
│       │   ├── java/com/xrdoge/xrpl/androidsa/
│       │   │   ├── MainActivity.kt
│       │   │   └── NativeBridge.kt
│       │   └── res/values/strings.xml
│       └── test/java/com/xrdoge/xrpl/androidsa/NativeBridgeTest.kt
├── docs/
│   ├── ANDROID_DEVICE_CI_READY_CHECKLIST.md
│   ├── RELEASE_CHECKLIST.md
│   └── ...
├── tools/
│   └── google_maven_proxy.py
├── .gitignore
├── CHANGELOG.md
├── HowtoSetup.md
├── Projectvorstellungs.md
├── README.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── settings.gradle.kts
└── ToDo.md
```

## Build-, Test- und CI-Validierung

### Voraussetzungen

- JDK 17
- Android SDK 34
- Android NDK `27.3.13750724`
- CMake 3.22.1+
- minSdk 26
- optional: lokaler Maven-Proxy für blockierte Google-Maven-Umgebungen

### Lokaler Maven-Proxy

Falls `dl.google.com` oder Google Maven nicht erreichbar ist, kann die lokale Spiegel-Umgebung gestartet werden:

```bash
python3 tools/google_maven_proxy.py --port 38473
```

Danach Gradle mit dem lokalen Proxy konfigurieren:

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
```

### Native Host-Tests

```bash
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
```

### Gradle JVM-Unit-Tests

```bash
chmod +x ./gradlew
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

Mit lokalem Maven-Proxy:

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

### Vollständige Check-Kette

```bash
./gradlew --no-daemon check build --stacktrace
```

## Abschluss

AndroidSA ist ein dokumentiertes, begrenztes und technisch ehrlich modelliertes Projekt: ein Diagnose- und Server-Status-Tool, keine Fake-Gameplay-Client-Schicht und kein behaupteter Open:MP-Android-Slot. Die richtige technische Positionierung ist: Netzwerk-/Server-Analyse auf Android mit klarer Trennung von Spielwelt, Engine und legaler Multi-Player-Implementierung.
