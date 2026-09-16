# Howto Setup: AndroidSA

Dieses Handbuch beschreibt die vollständige Einrichtung für zwei zentrale Zielumgebungen:

1. Localhost / Entwicklungsumgebung mit Host-Tests
2. Android-Gerät oder Emulator mit instrumentierten UI-Tests

Es deckt Architektur, Voraussetzungen, lokale Proxy-Konfiguration, Testschritte, Remote-UDP-Deployment und Debugging vollständig ab.

## 1. Zielumgebungen

### a) Localhost / Entwicklungsumgebung

Ziel: lokale Entwicklung, native C++-Host-Tests, JVM-Unit-Tests und Gradle-Validierung ohne echtes Android-Gerät.

### b) Android Mobile / Emulator

Ziel: APK-Build, Installation auf Gerät/Emulator und instrumentierte UI-Tests für die Compose-Oberfläche.

## 2. Systemvoraussetzungen

Für beide Umgebungen gelten diese Mindestanforderungen:

- JDK 17
- Android SDK 34
- Android NDK `27.3.13750724`
- CMake 3.22.1+
- `adb` und `emulator` aus dem Android SDK
- Git
- Python 3

Optional, aber sinnvoll im restriktiven Netzwerk:

- lokaler Google-Maven-Proxy

### Umgebung prüfen

```bash
java -version
javac -version
sdkmanager --list | head
adb version
cmake --version
```

Wenn Android SDK nicht in `PATH` liegt, kann die Umgebungsvariable gesetzt werden:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"
```

## 3. Repository klonen

```bash
git clone https://github.com/XRDOGE-XRPL/AndroidSA.git
cd AndroidSA
chmod +x ./gradlew
```

## 4. Lokaler Maven-Proxy für blockierte Umgebungen

Falls Google Maven oder `dl.google.com` in der aktuellen Umgebung nicht erreichbar sind, startet das Repository einen lokalen Spiegel-Proxy:

```bash
python3 tools/google_maven_proxy.py --port 38473
```

Der Fokus liegt auf einer reproduzierbaren Local-Resolver-Strategie, damit Gradle-Plugin- und Android-Abhängigkeiten auch in restriktiven Netzwerken aufgelöst werden können.

### Gradle mit lokaler Proxy konfigurieren

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
```

Wird der Proxy per Umgebungsvariable gesetzt, bleibt die Infrastruktur für CI und lokale Entwicklung consistent.

## 5. Native Host-Tests (Localhost)

Der native C++20-Kern besitzt einen Host-Testpuffer für `ClientState`.

### CMake konfigurieren und bauen

```bash
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
```

### CTest ausführen

```bash
ctest --test-dir build/native-tests --output-on-failure
```

Die Validierung prüft unter anderem:

- Statuswechsel `connected` / `error`
- Paketzähler für `sendto` / `recvfrom`
- Reconnect- und Reset-Flow
- Event-Historie und letzte Commands
- Remote-/Loopback-Differenzierung im UDP-Pfad

## 6. Gradle JVM-Unit-Tests

```bash
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

Wenn ein lokaler Proxy aktiv ist:

```bash
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

## 7. Vollständige lokale Build-/Check-Kette

```bash
./gradlew --no-daemon check build --stacktrace
```

Oder in der üblichen Reihenfolge für CI-ähnliche Validierung:

```bash
./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
./gradlew --no-daemon check build --stacktrace
```

## 8. Android-Gerät / Emulator vorbereiten

### Gerät

- Android-Gerät mit USB-Debugging aktivieren
- `adb devices` prüfen
- wenn nötig: `adb kill-server && adb start-server`

### Emulator

- Android Studio oder Android SDK-Tools verwenden
- ein Emulator-Image mit API 34 installieren
- Emulator starten
- `adb devices` verifizieren

Systemvoraussetzungen für die App:

- `minSdk = 26`
- `targetSdk = 34`
- Android SDK 34 installiert

## 9. APK bauen

```bash
./gradlew --no-daemon :app:assembleDebug --stacktrace
```

Für instrumentierte UI-Tests zusätzlich:

```bash
./gradlew --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

## 10. Instrumentierte UI-Tests ausführen

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Die Testklasse `MainActivityUiTest.kt` deckt ab:

- Server-Browser-Flow (Profil hinzufügen, auswählen, Connect/Ping)
- Busy-State / Mutex-Serialisierung
- Runtime-Statisktiken wie Latenz, Packets sent/received, TX/RX ratio
- UI-Metadaten wie Last command, Connection state und active operation

## 11. Remote-UDP-Konfiguration und Live-Debugging

### Remote-Endpunkte

In der App können Serverprofile mit Host/IP und Port konfiguriert werden, z. B.:

- `prod.example.org:7777`
- `demo.sa-mp.local:7777`
- `127.0.0.1:7777` für lokale Tests

### Wichtiges Verhalten

- Loopback-Hostnamen bleiben im stabilen lokalen Testpfad
- echte Remote-Endpunkte werden mit `getaddrinfo()` aufgelöst
- `sendto()`/`recvfrom()` werden in realen UDP-Probes verwendet
- die native Event-Historie zeigt die empfangenen Paketarten und RPC-Wrapper-Details an

### Live-Debugging

- App starten und `Recent events` beobachten
- `Status`, `simulate:rx`, `simulate:tx` oder `connect:<server>` verwenden
- Paketzähler, Latenz und TX/RX-Ratio verifizieren
- Remote-Server-Infos mit `serverAddress`, `playerName` und `diagnostics` überprüfen

## 12. Troubleshooting

### Gradle-Fehler / Resolver-Blockade

```bash
python3 tools/google_maven_proxy.py --port 38473
ANDROIDSA_GOOGLE_MAVEN_URL=http://127.0.0.1:38473/ ./gradlew --no-daemon help :app:testDebugUnitTest :app:assemble --stacktrace --refresh-dependencies
```

### Native Tests fehlgeschlagen

```bash
cmake -S app/src/main/cpp -B build/native-tests -DANDROIDSA_ENABLE_NATIVE_TESTS=ON
cmake --build build/native-tests --target client_state_test
ctest --test-dir build/native-tests --output-on-failure
```

### Gerät nicht sichtbar

```bash
adb devices -l
adb kill-server
adb start-server
```

### UI-Tests nicht sichtbar / nicht startbar

- Emulator oder Gerät online prüfen
- `adb shell getprop sys.boot_completed` prüfen
- `./gradlew :app:assembleDebugAndroidTest` erneut ausführen
- im Android-Emulator logcat nach Fehlern oder ANR-Bedingungen prüfen

## 13. Abschluss

Wenn die oben genannten Schritte in der jeweiligen Zielumgebung erfolgreich laufen, ist AndroidSA in einem vollständigen, dokumentierten und reproduzierbaren Zustand für lokale Entwicklung, native Host-Tests, Android-Gerät-/Emulator-Validierung und finale Release-Prüfung.
