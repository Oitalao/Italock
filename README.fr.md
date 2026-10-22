[Português (Brasil)](README.md) · [English](README.en.md) · [Español](README.es.md) · **Français** · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Icône d'Ítalock">

# Ítalock

**Déverrouillez l'ordinateur avec l'empreinte de votre téléphone.**

Au lieu de taper votre mot de passe à chaque fois, l'ordinateur le demande à votre téléphone Android et vous confirmez avec votre empreinte ou votre visage. Depuis le téléphone, vous pouvez aussi verrouiller l'ordinateur d'un simple toucher, de près comme de loin.

[![Version](https://img.shields.io/github/v/release/Oitalao/Italock?label=version&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![Licence GPL 3.0](https://img.shields.io/badge/licence-GPL%203.0-003399)](LICENSE)
![Windows, Linux et Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-gratuit-555)

</div>

## Télécharger

Il vous faut **deux choses** : le programme sur l'ordinateur et l'application sur le téléphone.

| Où | Fichier | Compatible avec |
| --- | --- | --- |
| 💻 **Ordinateur sous Windows** | [**Télécharger ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 et 11 (64 bits) |
| 📱 **Téléphone Android** | [**Télécharger Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 ou plus récent |
| 🐧 Ordinateur sous Linux | [Télécharger ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 ou plus récents |

Tout est gratuit, sans publicité, sans inscription et sans connexion internet obligatoire. Les versions précédentes se trouvent sur la page des [versions](https://github.com/Oitalao/Italock/releases).

## Ce qu'il fait

- 🔓 **Déverrouille l'ordinateur avec l'empreinte ou le visage** du téléphone, sur l'écran de verrouillage et sur l'écran de connexion de Windows.
- 🔒 **Verrouille l'ordinateur depuis le téléphone** : depuis l'application, depuis le widget de l'écran d'accueil, depuis le raccourci du volet de notifications ou en approchant le téléphone d'une étiquette NFC.
- 🛜 **Fonctionne en Wi-Fi, par câble USB ou en Bluetooth.** À distance, il fonctionne aussi avec [Tailscale](https://tailscale.com/), un réseau privé gratuit.
- 🖥️ **Plusieurs ordinateurs sur le même téléphone**, et plusieurs téléphones sur le même ordinateur, sans limite.
- 🔑 **Votre mot de passe reste valable.** Ítalock est simplement un moyen plus rapide de vous connecter. Si le téléphone n'a plus de batterie ou est loin, tapez votre mot de passe comme d'habitude.
- 🌐 **Disponible en 13 langues.** Le programme s'ouvre en portugais ; pour changer, ouvrez **Configurações** (Paramètres) et choisissez **Idioma** (Langue), sur l'ordinateur comme sur le téléphone. Thème sombre par défaut, avec option de thème clair.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Écran d'accueil d'Ítalock sous Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Paramètres d'Ítalock sous Windows">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="Application sur le téléphone avec deux ordinateurs associés">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="Application sur une tablette montrant un ordinateur déverrouillé">
</p>

## Installation

Il y a trois étapes : installer sur l'ordinateur, installer sur le téléphone et relier les deux une seule fois.

### 1. Sur l'ordinateur (Windows)

1. Téléchargez [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) et ouvrez le fichier.
2. Windows peut afficher le message **« Windows a protégé votre ordinateur »**. Il apparaît pour les programmes qui ne paient pas la signature numérique de Microsoft. Cliquez sur **Informations complémentaires**, puis sur **Exécuter quand même**.
3. Cliquez sur **Oui** quand Windows demande l'autorisation et suivez l'installateur jusqu'au bout.
4. Ítalock s'ouvre tout seul et place une icône près de l'horloge.

> [!IMPORTANT]
> **Vous vous connectez à Windows avec un compte Microsoft (celui avec une adresse e-mail) ?** Alors Windows doit accepter un mot de passe, et pas seulement un code PIN ou le visage. Ouvrez **Paramètres › Comptes › Options de connexion** et **désactivez** l'option *« Pour une sécurité renforcée, autorisez uniquement la connexion Windows Hello pour les comptes Microsoft sur cet appareil »*. Si vous n'avez jamais créé de mot de passe pour ce compte, créez-en un sur [account.microsoft.com](https://account.microsoft.com/security).

### 2. Sur le téléphone (Android)

1. Sur le téléphone, téléchargez [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) et touchez le fichier téléchargé.
2. Si Android indique qu'il ne peut pas installer d'applications de cette source, touchez **Paramètres**, activez **Autoriser cette source** et revenez en arrière.
3. Si un avertissement de Google Play Protect apparaît, touchez **Plus de détails**, puis **Installer quand même**. Cet avertissement apparaît pour toute application qui ne vient pas du Play Store.
4. Ouvrez Ítalock et acceptez les notifications. Dans **Paramètres**, touchez **Autoriser** / **Exempter** sur chaque élément de la section *Autorisations manquantes*, pour qu'Android ne ferme pas l'application.

### 3. Relier le téléphone à l'ordinateur (une seule fois)

1. Placez l'ordinateur et le téléphone **sur le même Wi-Fi** (ou reliez-les par câble USB).
2. Sur l'ordinateur, ouvrez Ítalock et cliquez sur **Associer un téléphone**. Un QR code s'affiche.
3. Sur le téléphone, ouvrez Ítalock, touchez **+** puis **Scanner le QR code**. Pointez l'appareil photo vers l'écran de l'ordinateur.
4. C'est fait. L'ordinateur apparaît sur l'écran du téléphone.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Écran d'association d'un ordinateur sur le téléphone">
</p>

## Au quotidien

**Pour déverrouiller :** quand l'ordinateur est verrouillé, appuyez sur n'importe quelle touche. Une notification arrive sur le téléphone ; touchez-la et confirmez avec votre empreinte ou votre visage. L'ordinateur se déverrouille tout seul.

Vous pouvez aussi déverrouiller **depuis le téléphone lui-même**, sans vous approcher de l'ordinateur : ouvrez Ítalock et touchez **Déverrouiller** sur la carte de l'ordinateur.

**Pour verrouiller :** touchez **Verrouiller** dans l'application. Si vous préférez, ajoutez le widget d'Ítalock à l'écran d'accueil ou le raccourci au volet de notifications (faites glisser le volet vers le bas, touchez le crayon et faites glisser Ítalock).

## Questions fréquentes

<details>
<summary><b>Est-ce sûr ?</b></summary>

Oui. Le téléphone et l'ordinateur communiquent directement, sans passer par aucun serveur sur internet, et tout est chiffré (AES 256). Chaque demande de déverrouillage nécessite votre empreinte, votre visage ou le verrouillage d'écran du téléphone, et n'est valable que quelques secondes. Le mot de passe Windows est conservé chiffré sur l'ordinateur et la clé qui le déchiffre reste uniquement sur le téléphone : l'un sans l'autre ne sert à rien. Et le code est ouvert : tout le monde peut vérifier ce que fait le programme.

</details>

<details>
<summary><b>Et si je perds mon téléphone ou s'il n'a plus de batterie ?</b></summary>

Tapez votre mot de passe normalement. Ítalock ne retire jamais le mot de passe, le code PIN ni Windows Hello de l'écran de connexion : il ajoute seulement une option de plus. Si vous perdez le téléphone, ouvrez Ítalock sur l'ordinateur et retirez ce téléphone de la liste.

</details>

<details>
<summary><b>La notification n'arrive pas sur le téléphone.</b></summary>

- Vérifiez que les deux sont sur le même Wi-Fi, ou que le Bluetooth est activé sur les deux.
- Sur le téléphone, ouvrez Ítalock › **Paramètres** et autorisez tout ce qui figure dans *Autorisations manquantes*, surtout l'**économie de batterie**. Certains téléphones (Xiaomi, Samsung, Motorola) ferment les applications en arrière-plan ; cherchez « Ítalock » dans les paramètres de batterie de l'appareil et choisissez **Non restreinte**.
- Si vous avez un antivirus ou un pare-feu sur l'ordinateur, autorisez Ítalock dedans.

</details>

<details>
<summary><b>Est-ce que ça marche à distance, hors de la maison ?</b></summary>

Oui, avec [Tailscale](https://tailscale.com/download), qui est gratuit. Installez Tailscale sur l'ordinateur et sur le téléphone et connectez-vous avec le même compte sur les deux. Ensuite, Ítalock trouve l'ordinateur où que vous soyez.

</details>

<details>
<summary><b>Est-ce que ça marche sur iPhone ?</b></summary>

Non. Pour l'instant, uniquement sur Android.

</details>

<details>
<summary><b>Comment le désinstaller ?</b></summary>

- **Windows :** Paramètres › Applications › Ítalock › Désinstaller. Il vous demande si vous voulez aussi effacer les téléphones associés.
- **Linux :** `sudo sh ItalockLinux.run --desinstalar`
- **Android :** comme n'importe quelle autre application.

</details>

## Linux

L'installateur pour Linux est un fichier unique. Dans le terminal, à l'intérieur du dossier où il a été téléchargé :

```bash
sudo sh ItalockLinux.run
```

Sous Linux, Ítalock gère l'**économiseur d'écran** (l'écran qui apparaît quand l'ordinateur se verrouille). La connexion, `sudo` et SSH continuent de fonctionner uniquement avec le mot de passe.

| Environnement | Déverrouiller depuis le téléphone | Verrouiller depuis le téléphone |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Fonctions expérimentales

Elles sont désactivées par défaut, dans **Paramètres › Fonctions expérimentales** sur le téléphone. Elles s'adressent à ceux qui aiment tester :

- **Déverrouillage automatique à proximité**, en Bluetooth ou sur le même réseau.
- **Déverrouiller depuis la notification**, sans ouvrir l'application.
- **Montre Wear OS** (Galaxy Watch, Pixel Watch et autres) : la demande s'affiche sur la montre. Il faut installer [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk) sur la montre.

## Pour les développeurs

| Dossier | Contenu |
| --- | --- |
| [`desktop/`](desktop) | Programme pour l'ordinateur (C++ 20 et Qt 6), le module de l'écran de verrouillage de Windows, le module PAM de Linux et l'agent qui reçoit les demandes du téléphone |
| [`android/`](android) | Application pour le téléphone (Kotlin et Jetpack Compose) et l'application pour montre Wear OS |
| [`scripts/`](scripts) | Scripts de compilation pour Windows et Linux |
| [`docs/`](docs) | Protocole entre le téléphone et l'ordinateur, icône et images |

Pour compiler, consultez [`docs/COMPILAR.md`](docs/COMPILAR.md) (en portugais).

## Crédits et licence

Le programme pour l'ordinateur est un fork de [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop), de MeisApps, avec de nouvelles fonctions : plusieurs associations, déverrouillage à distance via Tailscale, verrouillage depuis le téléphone, une nouvelle interface et le portugais comme langue principale. L'application pour le téléphone a été écrite de zéro et utilise le même protocole.

Il utilise aussi [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), la police [Inter](https://rsms.me/inter/) et les [icônes Material](https://fonts.google.com/icons) de Google. La licence de chacun se trouve sur l'écran **À propos** des applications.

Distribué sous licence [GPL 3.0](LICENSE) : vous pouvez l'utiliser, l'étudier, le modifier et le redistribuer, à condition de garder le code ouvert.
