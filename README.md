# Urfa Druck-Agent (Android)

Kleine Android-App, die auf dem Laden-Tablet läuft, neue Bestellungen vom Server
abholt und sie **direkt** auf den Epson-Bondrucker (ESC/POS über Netzwerk) druckt —
mit lautem Ton und Bestellübersicht. Lokale Brücke zwischen Cloud-Webseite und Drucker.

## So bekommst du die fertige APK (ohne Android Studio)

1. **GitHub-Account** erstellen (kostenlos) auf https://github.com
2. Neues Repository anlegen (z. B. `urfa-druck-app`), **Public** oder Private.
3. Den Inhalt dieses Ordners hochladen (per Web-Upload „Add file → Upload files",
   oder mit `git push`).
4. GitHub baut die App automatisch. Unter **Actions** → den letzten Lauf öffnen →
   ganz unten unter **Artifacts** liegt **`UrfaDruck-APK`** zum Download.
5. ZIP entpacken → `app-debug.apk` auf das Tablet kopieren und installieren
   (Installation aus „unbekannten Quellen" einmal erlauben).

## Einrichtung in der App
- **Server-Adresse:** `https://myurfa.de`
- **Token:** aus dem Admin (derselbe wie für den Druck-Endpunkt)
- **Drucker-IP:** `192.168.178.49`  ·  **Port:** `9100`
- **Start** tippen. Die App läuft dann im Hintergrund weiter.

## Voraussetzung
Tablet und Drucker müssen im **selben WLAN** sein. Der Drucker muss Rohdruck
auf Port 9100 annehmen (Epson-Netzwerkschnittstelle Standard).

## Pro Restaurant
Einfach Server-Adresse + Token + Drucker-IP anpassen — dieselbe App für alle Standorte.
