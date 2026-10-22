[Português (Brasil)](README.md) · **English** · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Ítalock icon">

# Ítalock

**Unlock your computer with your phone's fingerprint.**

Instead of typing your password every time, the computer asks your Android phone and you confirm with your fingerprint or face. From the phone, you can also lock the computer with one tap, whether you are nearby or far away.

[![Version](https://img.shields.io/github/v/release/Oitalao/Italock?label=version&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![GPL 3.0 license](https://img.shields.io/badge/license-GPL%203.0-003399)](LICENSE)
![Windows, Linux and Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-free-555)

</div>

## Download

You need **two things**: the program on the computer and the app on the phone.

| Where | File | Works on |
| --- | --- | --- |
| 💻 **Windows computer** | [**Download ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 and 11 (64 bit) |
| 📱 **Android phone** | [**Download Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 or newer |
| 🐧 Linux computer | [Download ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 or newer |

Everything is free, with no ads, no sign up and no internet required. Older versions are on the [releases](https://github.com/Oitalao/Italock/releases) page.

## What it does

- 🔓 **Unlocks the computer with your phone's fingerprint or face**, on the lock screen and on the Windows sign in screen.
- 🔒 **Locks the computer from your phone**: from the app, from the home screen widget, from the shortcut in the notification panel or by tapping the phone on an NFC tag.
- 🛜 **Works over Wi-Fi, USB cable or Bluetooth.** From far away, it also works through [Tailscale](https://tailscale.com/), a free private network.
- 🖥️ **Several computers on the same phone**, and several phones on the same computer, with no limit.
- 🔑 **Your password still works.** Ítalock is just a faster way to get in. If the phone is out of battery or far away, just type your password as usual.
- 🌐 **Available in 13 languages.** The program opens in Portuguese by default; to change it, open **Configurações** (Settings) and pick **Idioma** (Language), on both the computer and the phone. Dark theme by default, with a light theme option.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Ítalock home screen on Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Ítalock settings on Windows">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="Phone app with two paired computers">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="App on a tablet showing an unlocked computer">
</p>

## How to install

There are three steps: install on the computer, install on the phone and connect the two, just once.

### 1. On the computer (Windows)

1. Download [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) and open the file.
2. Windows may show the message **"Windows protected your PC"**. This shows up for programs that do not pay for Microsoft's digital signature. Click **More info** and then **Run anyway**.
3. Click **Yes** when Windows asks for permission and follow the installer to the end.
4. Ítalock opens on its own and keeps an icon near the clock.

> [!IMPORTANT]
> **Do you sign in to Windows with a Microsoft account (the one with an email address)?** Then Windows needs to accept a password, not only a PIN or your face. Open **Settings › Accounts › Sign-in options** and **turn off** the option *"For improved security, only allow Windows Hello sign-in for Microsoft accounts on this device"*. If you never created a password for the account, create one at [account.microsoft.com](https://account.microsoft.com/security).

### 2. On the phone (Android)

1. On the phone, download [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) and tap the downloaded file.
2. If Android says it cannot install apps from this source, tap **Settings**, turn on **Allow from this source** and go back.
3. If a Google Play Protect warning appears, tap **More details** and **Install anyway**. The warning appears for any app that did not come from the Play Store.
4. Open Ítalock and allow notifications. In **Settings**, tap **Allow** on each item in the *Needs permission* section, so Android does not close the app.

### 3. Connect the phone to the computer (just once)

1. Keep the computer and the phone **on the same Wi-Fi** (or connected by USB cable).
2. On the computer, open Ítalock and click **Pair phone**. A QR code appears.
3. On the phone, open Ítalock, tap **+** and then **Scan QR code**. Point the camera at the computer screen.
4. Done. The computer shows up on the phone screen.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Pair computer screen on the phone">
</p>

## Everyday use

**To unlock:** with the computer locked, press any key. A notification arrives on the phone; tap it and confirm with your fingerprint or face. The computer unlocks on its own.

You can also unlock **from the phone itself**, without going near the computer: open Ítalock and tap **Unlock** on the computer's card.

**To lock:** tap **Lock** in the app. If you prefer, add the Ítalock widget to the home screen or the shortcut to the notification panel (pull down the panel, tap the pencil and drag Ítalock).

## Frequently asked questions

<details>
<summary><b>Is it safe?</b></summary>

Yes. The phone and the computer talk directly, without going through any server on the internet, and everything is encrypted (AES 256). Every unlock request needs your fingerprint, your face or your phone's screen lock, and it is only valid for a few seconds. The Windows password is stored encrypted on the computer, and the key that opens it is kept only on the phone: one without the other is useless. And the code is open: anyone can check what the program does.

</details>

<details>
<summary><b>What if I lose my phone or its battery runs out?</b></summary>

Type your password as usual. Ítalock never removes the password, the PIN or Windows Hello from the sign in screen: it only adds one more option. If you lose the phone, open Ítalock on the computer and remove that phone from the list.

</details>

<details>
<summary><b>The notification does not reach the phone.</b></summary>

- Check that both are on the same Wi-Fi, or that Bluetooth is on in both.
- On the phone, open Ítalock › **Settings** and allow everything listed under *Needs permission*, especially **battery saving**. Some phones (Xiaomi, Samsung, Motorola) close apps running in the background; search for "Ítalock" in the phone's battery settings and choose **Unrestricted**.
- If you have an antivirus or firewall on the computer, allow Ítalock in it.

</details>

<details>
<summary><b>Does it work from far away, outside home?</b></summary>

Yes, with [Tailscale](https://tailscale.com/download), which is free. Install Tailscale on the computer and on the phone and sign in with the same account on both. After that, Ítalock finds the computer from anywhere.

</details>

<details>
<summary><b>Does it work on iPhone?</b></summary>

No. For now, Android only.

</details>

<details>
<summary><b>How do I uninstall it?</b></summary>

- **Windows:** Settings › Apps › Ítalock › Uninstall. It asks whether you also want to delete the paired phones.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** like any other app.

</details>

## Linux

The Linux installer is a single file. In the terminal, inside the folder where it was downloaded:

```bash
sudo sh ItalockLinux.run
```

On Linux, Ítalock handles the **screen saver** (the screen that appears when the computer locks). Login, `sudo` and SSH still use only the password.

| Desktop | Unlock from the phone | Lock from the phone |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Experimental features

They are off by default, in **Settings › Experimental features** on the phone. They are for people who like to try things out:

- **Unlock automatically when nearby**, over Bluetooth or the same network.
- **Unlock from the notification**, without opening the app.
- **Wear OS watch** (Galaxy Watch, Pixel Watch and others): the request shows up on the watch. You need to install [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk) on the watch.

## For developers

| Folder | What is inside |
| --- | --- |
| [`desktop/`](desktop) | Computer program (C++ 20 and Qt 6), the Windows lock screen module, the Linux PAM module and the agent that receives requests from the phone |
| [`android/`](android) | Phone app (Kotlin and Jetpack Compose) and the Wear OS watch app |
| [`scripts/`](scripts) | Build scripts for Windows and Linux |
| [`docs/`](docs) | Protocol between phone and computer, icon and images |

To build it, see [`docs/COMPILAR.md`](docs/COMPILAR.md) (in Portuguese).

## Credits and license

The computer program is a fork of [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop), by MeisApps, with new features: multiple pairings, unlocking from far away through Tailscale, locking from the phone, a new interface and Portuguese as the main language. The phone app was written from scratch and speaks the same protocol.

It also uses [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), the [Inter](https://rsms.me/inter/) font and Google's [Material icons](https://fonts.google.com/icons). Each one's license is on the **About** screen of the apps.

Distributed under the [GPL 3.0](LICENSE) license: you can use, study, modify and redistribute it, as long as you keep the code open.
