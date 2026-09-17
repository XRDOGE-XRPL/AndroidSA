# Systemanalyse: APK-Struktur com.rockstargames.gtasager

## App-Metadaten und Versionsparameter

- App-Name: GTA: SA
- Package-ID: `com.rockstargames.gtasager`
- Version: `2.11.277 (Build 3782367)`
- API-Spezifikation: Ziel-SDK API `34` (Android 14.0), Minimal-SDK API `28` (Android 9.0)
- Installationsquelle: Google Play Store
- Installationsdatum: `13. Mai 2026`

## Speicherplatzzuweisung

- App-Größe (Binary/Code): `1,51 GB`
- App-Daten (User-Data/Assets): `1,17 GB`
- Cache-Speicher: `98,30 KB`
- Gesamtvolumen: `2,67 GB`

## Deklarierte Manifest-Komponenten und Services

### Hauptaktivitäten (Activities)

- `com.rockstargames.gtasa.DownloaderActivity`
- `com.rockstargames.gtasa.GameActivity`
- `rockstarmobile.ui.FlutterScreenHost`
- `rockstarmobile.ui.BrowserScreen`

### Hintergrunddienste (Services)

- `rockstarmobile.integrations.GCFirebaseMessagingService`
- `com.google.android.play.core.assetpacks.AssetPackExtractionService`
- `com.google.android.play.core.assetpacks.ExtractionForegroundService`
- `com.google.firebase.messaging.FirebaseMessagingService`

### Content Provider & Error Tracking

- `rockstarmobile.Provider`
- `io.sentry.android.core.SentryInitProvider`
- `io.sentry.android.core.SentryPerformanceProvider`

### Hardware-Merkmale

- `android.hardware.touchscreen`
- `android.hardware.touchscreen.multitouch`
- `android.software.leanback`

## Android-Standardpfade für die App-Paketdaten von com.rockstargames.gtasager

- APK-Installationsverzeichnis: `/data/app/~~[Hash]==/com.rockstargames.gtasager-[Hash2]==/base.apk`
- Interne App-Daten (App-Speicher): `/data/user/0/com.rockstargames.gtasager/`
- Interne Dateien (Files): `/data/user/0/com.rockstargames.gtasager/files/`
- Interner Cache: `/data/user/0/com.rockstargames.gtasager/cache/`
- Externe App-Daten (Scoped Storage / Android/data): `/storage/emulated/0/Android/data/com.rockstargames.gtasager/`
- Spielstand- und Obb-/Asset-Verzeichnis (falls extern abgelegt): `/storage/emulated/0/Android/data/com.rockstargames.gtasager/files/` bzw. `/storage/emulated/0/Android/obb/com.rockstargames.gtasager/`
- Interne SharedPreferences: `/data/user/0/com.rockstargames.gtasager/shared_prefs/`
- Interne Datenbanken: `/data/user/0/com.rockstargames.gtasager/databases/`
- Externe OBB-Dateien (Installations-Asset-Packs): `/storage/emulated/0/Android/obb/com.rockstargames.gtasager/`
- Externer Mediencache: `/storage/emulated/0/Android/data/com.rockstargames.gtasager/cache/`
- Native Bibliotheken (JNI libs): `/data/app/~~[Hash]==/com.rockstargames.gtasager-[Hash2]==/lib/`
- Sentry Crash-Reporting und Log-Dateien: `/data/user/0/com.rockstargames.gtasager/files/sentry/`
- Flutter Engine Assets und Bytecode: `/data/user/0/com.rockstargames.gtasager/app_flutter/`
- Firebase Instance ID und Token-Speicher: `/data/user/0/com.rockstargames.gtasager/no_backup/`
- WebView Cache und Cookies: `/data/user/0/com.rockstargames.gtasager/app_webview/`
- Code Cache (ART JIT/AOD compilierter Code): `/data/user/0/com.rockstargames.gtasager/code_cache/`

## Kurzfazit

Die Paketstruktur zeigt, dass `com.rockstargames.gtasager` ein vollständiges Android-Spiel-Ökosystem mit App-Assets, OBB-Downloads, Flutter-/WebView-Teilen, Firebase/Play-Asset-Pack-Integration und Crash-Reporting ist. Die Daten- und Speicherpfade bestätigen die realistische Trennung zwischen App-Binary, Benutzer-/Asset-Daten und Laufzeit-Subsystemen.
