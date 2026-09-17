# AndroidSA

AndroidSA ist ein Android-Prototyp für einen SA:MP-/Open:MP-orientierten Client. Das Repository verbindet eine moderne Jetpack-Compose-Oberfläche mit einer Kotlin/JNI-Brücke und einem nativen C++20-Kern, um Verbindungsstatus, Serverprofile, Diagnosemeldungen, Laufzeitstatistiken, Event-Historie und echte UDP-Probe-Flows in einer klaren, testbaren Architektur zu modellieren.

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

Alternativ ist im Root-Projekt ein dedizierter Release-Validierungs-Task verfügbar:

```bash
./gradlew --no-daemon releaseValidation --stacktrace
```

Dieser Task kapselt die produktive CI-Reihenfolge mit nativen Host-Tests, JVM-Unit-Tests und finalem Assemble-Pfad. Für die reine Native-Prüfung ist ebenfalls ein Root-Task verfügbar:

```bash
./gradlew --no-daemon nativeHostCheck --stacktrace
```

Oder in der finalen, stabilen CI-ähnlichen Reihenfolge:

```bash
./gradlew --no-daemon help --stacktrace --refresh-dependencies
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:assemble --stacktrace
```

Für eine einmalige lokale Ausführung der gleichen Sequenz steht auch ein Wrapper-Skript bereit. Es prüft automatisch, ob Google Maven erreichbar ist, startet bei Bedarf den lokalen Proxy und setzt `ANDROIDSA_GOOGLE_MAVEN_URL` für den Gradle-Lauf:

```bash
bash tools/run_release_validation.sh
```

## Optionales Android-Gerät / Emulator-Setup

Für lokale APK-Verifikation auf Android-Gerät oder Emulator gilt nur noch ein optionaler, nicht required Pfad:

- Entwickleroptionen aktivieren
- USB-Debugging oder Emulator-Shell aktiv
- `minSdk = 26`, `targetSdk = 34`
- Android SDK und NDK korrekt installiert
- `adb devices` zeigt das Geräte-/Emulator-Target an

Optionaler Build-/Verifikationspfad für lokale Geräte-Validierung:

```bash
./gradlew --no-daemon :app:assembleDebug --stacktrace
```

Dieser Pfad dient ausschließlich der lokalen APK-/Geräte-Prüfung und ist kein Required-Check für den stabilen Main-Branch oder den Release-Status.

## Troubleshooting

- Command wird abgelehnt: exakte Syntax prüfen (`transport:<value>`, `connect:<host:port>`, `latency:<ms>`, `simulate:rx|tx`)
- Keine Native-Events sichtbar: Verbindung erneut aufbauen oder `simulate:rx`/`simulate:tx` auslösen
- Gradle-Resolver blockiert: Proxy starten und `ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/` setzen
- Native Host-Tests fehlschlagen: `build/native-tests` bereinigen und CTest erneut laufen lassen
- Verfügbare Android-Targets prüfen: `adb devices` und Android SDK/NDK-Versionen validieren

## Abschluss

AndroidSA ist für den finalen Release- und Betriebsstatus konsistent dokumentiert: Standard-Validierung läuft über native Host-Tests, Gradle-JVM-Unit-Tests und finalen Assemble-Schritt; Emulator-UI-Checks sind optional und nicht mehr Teil des produktiven CI-/Release-Pfads. Damit ist das Repository in einem stabilen, reproduzierbaren und dokumentierten Betriebszustand für Main-Branch, lokale Entwicklung und Release-Validierung.
