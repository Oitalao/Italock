[Português (Brasil)](README.md) · [English](README.en.md) · [Español](README.es.md) · [Français](README.fr.md) · **Deutsch** · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Ítalock-Symbol">

# Ítalock

**Entsperre den Computer mit dem Fingerabdruck deines Handys.**

Statt jedes Mal das Passwort einzutippen, fragt der Computer dein Android-Handy, und du bestätigst mit Fingerabdruck oder Gesicht. Über das Handy kannst du den Computer außerdem mit einem Tippen sperren, aus der Nähe oder aus der Ferne.

[![Version](https://img.shields.io/github/v/release/Oitalao/Italock?label=Version&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![Lizenz GPL 3.0](https://img.shields.io/badge/Lizenz-GPL%203.0-003399)](LICENSE)
![Windows, Linux und Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-kostenlos-555)

</div>

## Herunterladen

Du brauchst **zwei Dinge**: das Programm auf dem Computer und die App auf dem Handy.

| Wo | Datei | Läuft auf |
| --- | --- | --- |
| 💻 **Computer mit Windows** | [**ItalockWindows.exe herunterladen**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 und 11 (64 Bit) |
| 📱 **Android-Handy** | [**Italock.apk herunterladen**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 oder neuer |
| 🐧 Computer mit Linux | [ItalockLinux.run herunterladen](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 oder neuer |

Alles ist kostenlos, ohne Werbung, ohne Anmeldung und ohne Internetpflicht. Ältere Versionen findest du auf der Seite der [Veröffentlichungen](https://github.com/Oitalao/Italock/releases).

## Was es kann

- 🔓 **Entsperrt den Computer mit Fingerabdruck oder Gesicht** des Handys, auf dem Sperrbildschirm und auf dem Anmeldebildschirm von Windows.
- 🔒 **Sperrt den Computer über das Handy**: in der App, über das Widget auf dem Startbildschirm, über die Kachel in der Benachrichtigungsleiste oder indem du das Handy an einen NFC-Tag hältst.
- 🛜 **Funktioniert über WLAN, USB-Kabel oder Bluetooth.** Aus der Ferne funktioniert es auch über [Tailscale](https://tailscale.com/), ein kostenloses privates Netzwerk.
- 🖥️ **Mehrere Computer auf demselben Handy**, und mehrere Handys auf demselben Computer, ohne Begrenzung.
- 🔑 **Dein Passwort gilt weiterhin.** Ítalock ist nur ein schnellerer Weg hinein. Ist der Akku des Handys leer oder das Handy weit weg, tippst du einfach wie immer dein Passwort ein.
- 🌐 **In 13 Sprachen verfügbar.** Das Programm startet auf Portugiesisch; zum Umstellen öffnest du **Configurações** (Einstellungen) und wählst **Idioma** (Sprache), auf dem Computer und auf dem Handy. Ab Werk dunkles Design, mit Option für helles Design.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Startseite von Ítalock unter Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Einstellungen von Ítalock unter Windows">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="App auf dem Handy mit zwei gekoppelten Computern">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="App auf einem Tablet mit einem entsperrten Computer">
</p>

## Installation

Es sind drei Schritte: auf dem Computer installieren, auf dem Handy installieren und beide ein einziges Mal verbinden.

### 1. Auf dem Computer (Windows)

1. Lade [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) herunter und öffne die Datei.
2. Windows zeigt eventuell die Meldung **„Der Computer wurde durch Windows geschützt“**. Sie erscheint bei Programmen, die nicht für die digitale Signatur von Microsoft bezahlen. Klicke auf **Weitere Informationen** und dann auf **Trotzdem ausführen**.
3. Klicke auf **Ja**, wenn Windows um Erlaubnis fragt, und folge dem Installationsprogramm bis zum Ende.
4. Ítalock öffnet sich von selbst und zeigt ein Symbol neben der Uhr.

> [!IMPORTANT]
> **Meldest du dich bei Windows mit einem Microsoft-Konto an (dem mit E-Mail-Adresse)?** Dann muss Windows ein Passwort akzeptieren, nicht nur PIN oder Gesicht. Öffne **Einstellungen › Konten › Anmeldeoptionen** und **deaktiviere** die Option *„Für mehr Sicherheit nur Windows-Hello-Anmeldung für Microsoft-Konten auf diesem Gerät zulassen“*. Falls du für das Konto nie ein Passwort erstellt hast, erstelle eines auf [account.microsoft.com](https://account.microsoft.com/security).

### 2. Auf dem Handy (Android)

1. Lade auf dem Handy [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) herunter und tippe auf die heruntergeladene Datei.
2. Wenn Android meldet, dass es keine Apps aus dieser Quelle installieren darf, tippe auf **Einstellungen**, aktiviere **Dieser Quelle vertrauen** und gehe zurück.
3. Wenn eine Warnung von Google Play Protect erscheint, tippe auf **Weitere Details** und auf **Trotzdem installieren**. Die Warnung erscheint bei jeder App, die nicht aus dem Play Store stammt.
4. Öffne Ítalock und erlaube Benachrichtigungen. Tippe unter **Einstellungen** bei jedem Eintrag im Abschnitt *Noch zulassen* auf **Zulassen** / **Ausnehmen**, damit Android die App nicht beendet.

### 3. Handy mit dem Computer verbinden (nur einmal)

1. Computer und Handy müssen **im selben WLAN** sein (oder per USB-Kabel verbunden).
2. Öffne Ítalock auf dem Computer und klicke auf **Handy koppeln**. Ein QR-Code erscheint.
3. Öffne Ítalock auf dem Handy, tippe auf **+** und dann auf **QR-Code scannen**. Richte die Kamera auf den Bildschirm des Computers.
4. Fertig. Der Computer erscheint auf dem Bildschirm des Handys.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Bildschirm zum Koppeln eines Computers auf dem Handy">
</p>

## Im Alltag

**Zum Entsperren:** Drücke bei gesperrtem Computer eine beliebige Taste. Auf dem Handy kommt eine Benachrichtigung an; tippe darauf und bestätige mit Fingerabdruck oder Gesicht. Der Computer entsperrt sich von selbst.

Du kannst auch **direkt am Handy** entsperren, ohne zum Computer zu gehen: Öffne Ítalock und tippe auf der Karte des Computers auf **Entsperren**.

**Zum Sperren:** Tippe in der App auf **Sperren**. Wenn du magst, füge das Ítalock-Widget zum Startbildschirm hinzu oder die Kachel zur Benachrichtigungsleiste (Leiste herunterziehen, auf den Stift tippen und Ítalock hineinziehen).

## Häufige Fragen

<details>
<summary><b>Ist das sicher?</b></summary>

Ja. Handy und Computer sprechen direkt miteinander, ohne über einen Server im Internet zu gehen, und alles ist verschlüsselt (AES 256). Jede Entsperranfrage braucht deinen Fingerabdruck, dein Gesicht oder die Displaysperre des Handys und gilt nur wenige Sekunden. Das Windows-Passwort liegt verschlüsselt auf dem Computer, und der Schlüssel dazu liegt nur auf dem Handy: Das eine ohne das andere nützt nichts. Und der Code ist offen: Jeder kann nachprüfen, was das Programm tut.

</details>

<details>
<summary><b>Und wenn ich das Handy verliere oder der Akku leer ist?</b></summary>

Tippe dein Passwort ganz normal ein. Ítalock entfernt niemals Passwort, PIN oder Windows Hello vom Anmeldebildschirm: Es fügt nur eine weitere Möglichkeit hinzu. Wenn du das Handy verlierst, öffne Ítalock auf dem Computer und entferne dieses Handy aus der Liste.

</details>

<details>
<summary><b>Die Benachrichtigung kommt nicht auf dem Handy an.</b></summary>

- Prüfe, ob beide im selben WLAN sind oder ob Bluetooth auf beiden eingeschaltet ist.
- Öffne auf dem Handy Ítalock › **Einstellungen** und erlaube alles unter *Noch zulassen*, vor allem den **Energiesparmodus**. Manche Handys (Xiaomi, Samsung, Motorola) beenden Apps im Hintergrund; suche in den Akkueinstellungen des Geräts nach „Ítalock“ und wähle **Nicht eingeschränkt**.
- Wenn du auf dem Computer ein Antivirenprogramm oder eine Firewall hast, gib Ítalock dort frei.

</details>

<details>
<summary><b>Funktioniert es auch aus der Ferne, außer Haus?</b></summary>

Ja, mit [Tailscale](https://tailscale.com/download), das kostenlos ist. Installiere Tailscale auf dem Computer und auf dem Handy und melde dich auf beiden mit demselben Konto an. Danach findet Ítalock den Computer von überall.

</details>

<details>
<summary><b>Funktioniert es auf dem iPhone?</b></summary>

Nein. Vorerst nur Android.

</details>

<details>
<summary><b>Wie deinstalliere ich es?</b></summary>

- **Windows:** Einstellungen › Apps › Ítalock › Deinstallieren. Es fragt, ob du auch die gekoppelten Handys löschen möchtest.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** wie jede andere App.

</details>

## Linux

Das Installationsprogramm für Linux ist eine einzige Datei. Im Terminal, im Ordner, in den sie heruntergeladen wurde:

```bash
sudo sh ItalockLinux.run
```

Unter Linux übernimmt Ítalock den **Bildschirmschoner** (den Bildschirm, der erscheint, wenn der Computer gesperrt wird). Anmeldung, `sudo` und SSH funktionieren weiterhin nur mit dem Passwort.

| Desktop | Am Handy entsperren | Am Handy sperren |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Experimentelle Funktionen

Sie sind ab Werk ausgeschaltet und liegen auf dem Handy unter **Einstellungen › Experimentelle Funktionen**. Sie sind für alle, die gern ausprobieren:

- **Automatisch entsperren in der Nähe**, per Bluetooth oder im selben Netzwerk.
- **Über die Benachrichtigung entsperren**, ohne die App zu öffnen.
- **Wear OS-Uhr** (Galaxy Watch, Pixel Watch und andere): Die Anfrage erscheint auf der Uhr. Dafür muss [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk) auf der Uhr installiert werden.

## Für Entwickler

| Ordner | Inhalt |
| --- | --- |
| [`desktop/`](desktop) | Programm für den Computer (C++ 20 und Qt 6), das Modul für den Windows-Sperrbildschirm, das PAM-Modul für Linux und der Agent, der die Anfragen des Handys empfängt |
| [`android/`](android) | App für das Handy (Kotlin und Jetpack Compose) und die App für Wear-OS-Uhren |
| [`scripts/`](scripts) | Build-Skripte für Windows und Linux |
| [`docs/`](docs) | Protokoll zwischen Handy und Computer, Symbol und Bilder |

Zum Kompilieren siehe [`docs/COMPILAR.md`](docs/COMPILAR.md) (auf Portugiesisch).

## Danksagung und Lizenz

Das Programm für den Computer ist ein Fork von [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop) von MeisApps, mit neuen Funktionen: mehrere Kopplungen, Entsperren aus der Ferne über Tailscale, Sperren über das Handy, eine neue Oberfläche und Portugiesisch als Hauptsprache. Die App für das Handy wurde von Grund auf neu geschrieben und spricht dasselbe Protokoll.

Außerdem verwendet es [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), die Schrift [Inter](https://rsms.me/inter/) und die [Material-Symbole](https://fonts.google.com/icons) von Google. Die Lizenzen stehen jeweils auf dem Bildschirm **Über** der Apps.

Veröffentlicht unter der Lizenz [GPL 3.0](LICENSE): Du darfst es nutzen, untersuchen, verändern und weitergeben, solange der Code offen bleibt.
