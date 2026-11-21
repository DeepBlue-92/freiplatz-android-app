# SVN Tennis – Android App (Freiplatz)

Offizielle, schlanke Android-App für die Tennis-Platzreservierung des **SV Neuhausen e.V.** (`https://buchen.sv-neuhausen.de`).

## Highlights & Features

- **Nativer WebView-Wrapper:** Hohe Performance, kein unnötiger Overhead, 100 % Fokus auf Stabilität.
- **Nahtlose Vollbild-Optik:** Nutzt die volle Bildschirmhöhe (Edge-to-Edge), transparente Statusleiste über dem dunkelgrünen Website-Header (`#16422E`).
- **Firebase Auth & PWA Optimierung:** Vollständiger Cookie- und Session-Support (inkl. Third-Party-Cookies und DOM Storage), damit Vereinsmitglieder eingeloggt bleiben.
- **Dezenter Fortschrittsbalken:** Horizontale Ladeanzeige am oberen Rand bei Seitenwechseln.
- **Pull-to-Refresh:** Aktualisieren per Wischgeste (`SwipeRefreshLayout`) im Vereins-Dunkelgrün.
- **Offline- & Fehlerschutz:** Sauberer, minimalistischer Offline-Screen mit Vereinswappen und „ERNEUT VERSUCHEN“-Button.
- **Smarte Link- & Download-Behandlung:** Externe Links öffnen im System-Browser; PDF-, Excel- und CSV-Downloads (z. B. Buchungslisten und Statistiken) werden nativ vom Android DownloadManager verarbeitet.
- **Android Adaptive Icon:** Ausgelegt auf die Safe Zone mit weißem Hintergrund (`#FFFFFF`) und Android 12+ SplashScreen API.

## Projektstruktur

- **Package:** `de.svneuhausen.tennis`
- **Ziel-URL:** `https://buchen.sv-neuhausen.de` (konfigurierbar in `MainActivity.kt`)
- **Min SDK:** 24 (Android 7.0+)
- **Target SDK:** 34 (Android 14 / 15 ready)

## Kompilieren & Ausführen

### Mit Android Studio
1. Android Studio öffnen.
2. *Open Project* wählen und diesen Projektordner auswählen.
3. Gradle-Sync abwarten und auf einem Emulator oder echten Gerät starten.

### Per Terminal
```bash
./gradlew assembleDebug
```
Die fertige APK liegt anschließend unter:
`app/build/outputs/apk/debug/app-debug.apk`

---
© SV Neuhausen e.V. – Tennisabteilung
