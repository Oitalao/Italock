[Português (Brasil)](README.md) · [English](README.en.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · **Polski** · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Ikona Ítalock">

# Ítalock

**Odblokuj komputer odciskiem palca z telefonu.**

Zamiast za każdym razem wpisywać hasło, komputer wysyła prośbę do Twojego telefonu z Androidem, a Ty potwierdzasz odciskiem palca lub twarzą. Z telefonu możesz też zablokować komputer jednym dotknięciem, z bliska lub z daleka.

[![Wersja](https://img.shields.io/github/v/release/Oitalao/Italock?label=wersja&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![Licencja GPL 3.0](https://img.shields.io/badge/licencja-GPL%203.0-003399)](LICENSE)
![Windows, Linux i Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-za%20darmo-555)

</div>

## Pobierz

Potrzebujesz **dwóch rzeczy**: programu na komputerze i aplikacji na telefonie.

| Gdzie | Plik | Działa na |
| --- | --- | --- |
| 💻 **Komputer z Windows** | [**Pobierz ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 i 11 (64 bity) |
| 📱 **Telefon z Androidem** | [**Pobierz Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 lub nowszy |
| 🐧 Komputer z Linuksem | [Pobierz ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 lub nowsze |

Wszystko jest darmowe, bez reklam, bez rejestracji i bez konieczności połączenia z internetem. Starsze wersje znajdziesz na stronie [wydań](https://github.com/Oitalao/Italock/releases).

## Co potrafi

- 🔓 **Odblokowuje komputer odciskiem palca lub twarzą** z telefonu, na ekranie blokady i na ekranie logowania Windows.
- 🔒 **Blokuje komputer z telefonu**: z aplikacji, z widżetu na ekranie głównym, ze skrótu w panelu powiadomień albo przez przyłożenie telefonu do tagu NFC.
- 🛜 **Działa przez Wi-Fi, kabel USB lub Bluetooth.** Z daleka działa też przez [Tailscale](https://tailscale.com/), darmową sieć prywatną.
- 🖥️ **Wiele komputerów na jednym telefonie** i wiele telefonów na jednym komputerze, bez limitu.
- 🔑 **Twoje hasło nadal działa.** Ítalock to po prostu szybszy sposób logowania. Jeśli telefon się rozładował albo jest daleko, wpisz hasło jak zwykle.
- 🌐 **Dostępny w 13 językach.** Program uruchamia się po portugalsku; aby to zmienić, otwórz **Configurações** (Ustawienia) i wybierz **Idioma** (Język), na komputerze i na telefonie. Domyślnie ciemny motyw, z opcją jasnego.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Ekran główny Ítalock w Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Ustawienia Ítalock w Windows">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="Aplikacja na telefonie z dwoma sparowanymi komputerami">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="Aplikacja na tablecie pokazująca odblokowany komputer">
</p>

## Jak zainstalować

To trzy kroki: instalacja na komputerze, instalacja na telefonie i jednorazowe połączenie obu urządzeń.

### 1. Na komputerze (Windows)

1. Pobierz [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) i otwórz plik.
2. Windows może pokazać komunikat **„System Windows ochronił ten komputer”**. Pojawia się on przy programach, które nie płacą za podpis cyfrowy Microsoftu. Kliknij **Więcej informacji**, a potem **Uruchom mimo to**.
3. Kliknij **Tak**, gdy Windows poprosi o zgodę, i przejdź instalator do końca.
4. Ítalock otworzy się sam i pokaże ikonę obok zegara.

> [!IMPORTANT]
> **Logujesz się do Windows kontem Microsoft (tym z adresem e-mail)?** W takim razie Windows musi akceptować hasło, a nie tylko PIN lub twarz. Otwórz **Ustawienia › Konta › Opcje logowania** i **wyłącz** opcję *„Aby zwiększyć bezpieczeństwo, zezwalaj tylko na logowanie za pomocą funkcji Windows Hello dla kont Microsoft na tym urządzeniu”*. Jeśli nigdy nie utworzono hasła do konta, utwórz je na [account.microsoft.com](https://account.microsoft.com/security).

### 2. Na telefonie (Android)

1. Na telefonie pobierz [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) i dotknij pobranego pliku.
2. Jeśli Android ostrzeże, że nie może instalować aplikacji z tego źródła, dotknij **Ustawienia**, włącz **Zezwalaj z tego źródła** i wróć.
3. Jeśli pojawi się ostrzeżenie Google Play Protect, dotknij **Więcej szczegółów** i **Zainstaluj mimo to**. Ostrzeżenie pojawia się przy każdej aplikacji spoza Sklepu Play.
4. Otwórz Ítalock i zezwól na powiadomienia. W **Ustawieniach** dotknij **Zezwól** / **Wyklucz** przy każdej pozycji w sekcji *Brakuje zgody*, aby Android nie zamykał aplikacji.

### 3. Połącz telefon z komputerem (tylko raz)

1. Podłącz komputer i telefon **do tej samej sieci Wi-Fi** (albo połącz je kablem USB).
2. Na komputerze otwórz Ítalock i kliknij **Sparuj telefon**. Pojawi się kod QR.
3. Na telefonie otwórz Ítalock, dotknij **+**, a potem **Skanuj kod QR**. Skieruj aparat na ekran komputera.
4. Gotowe. Komputer pojawi się na ekranie telefonu.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Ekran parowania komputera na telefonie">
</p>

## Na co dzień

**Aby odblokować:** gdy komputer jest zablokowany, naciśnij dowolny klawisz. Na telefon przyjdzie powiadomienie; dotknij go i potwierdź odciskiem palca lub twarzą. Komputer odblokuje się sam.

Możesz też odblokować **prosto z telefonu**, bez podchodzenia do komputera: otwórz Ítalock i dotknij **Odblokuj** na karcie komputera.

**Aby zablokować:** dotknij **Zablokuj** w aplikacji. Jeśli wolisz, dodaj widżet Ítalock na ekran główny albo skrót do panelu powiadomień (rozwiń panel, dotknij ołówka i przeciągnij Ítalock).

## Najczęstsze pytania

<details>
<summary><b>Czy to bezpieczne?</b></summary>

Tak. Telefon i komputer komunikują się bezpośrednio, bez żadnego serwera w internecie, a wszystko jest szyfrowane (AES 256). Każda prośba o odblokowanie wymaga Twojego odcisku palca, twarzy albo blokady ekranu telefonu i jest ważna tylko przez kilka sekund. Hasło do Windows jest przechowywane w postaci zaszyfrowanej na komputerze, a klucz do niego jest tylko na telefonie: jedno bez drugiego jest bezużyteczne. Do tego kod jest otwarty: każdy może sprawdzić, co robi program.

</details>

<details>
<summary><b>A jeśli zgubię telefon albo się rozładuje?</b></summary>

Wpisz hasło normalnie. Ítalock nigdy nie usuwa hasła, PIN-u ani Windows Hello z ekranu logowania: dodaje tylko jeszcze jedną możliwość. Jeśli zgubisz telefon, otwórz Ítalock na komputerze i usuń ten telefon z listy.

</details>

<details>
<summary><b>Powiadomienie nie dociera na telefon.</b></summary>

- Sprawdź, czy oba urządzenia są w tej samej sieci Wi-Fi albo czy Bluetooth jest włączony w obu.
- Na telefonie otwórz Ítalock › **Ustawienia** i zezwól na wszystko w sekcji *Brakuje zgody*, zwłaszcza na **oszczędzanie baterii**. Niektóre telefony (Xiaomi, Samsung, Motorola) zamykają aplikacje działające w tle; wyszukaj „Ítalock” w ustawieniach baterii urządzenia i wybierz **Bez ograniczeń**.
- Jeśli masz na komputerze antywirus lub zaporę sieciową, dodaj w nich wyjątek dla Ítalock.

</details>

<details>
<summary><b>Czy działa z daleka, poza domem?</b></summary>

Tak, z [Tailscale](https://tailscale.com/download), który jest darmowy. Zainstaluj Tailscale na komputerze i na telefonie i zaloguj się na obu tym samym kontem. Od tej pory Ítalock znajdzie komputer z dowolnego miejsca.

</details>

<details>
<summary><b>Czy działa na iPhonie?</b></summary>

Nie. Na razie tylko Android.

</details>

<details>
<summary><b>Jak odinstalować?</b></summary>

- **Windows:** Ustawienia › Aplikacje › Ítalock › Odinstaluj. Program zapyta, czy usunąć też sparowane telefony.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** jak każdą inną aplikację.

</details>

## Linux

Instalator dla Linuksa to jeden plik. W terminalu, w folderze, do którego został pobrany:

```bash
sudo sh ItalockLinux.run
```

W Linuksie Ítalock obsługuje **wygaszacz ekranu** (ekran, który pojawia się po zablokowaniu komputera). Logowanie, `sudo` i SSH nadal działają tylko z hasłem.

| Środowisko | Odblokowanie z telefonu | Blokowanie z telefonu |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Funkcje eksperymentalne

Domyślnie są wyłączone i znajdują się w **Ustawienia › Funkcje eksperymentalne** na telefonie. Są dla tych, którzy lubią testować:

- **Automatyczne odblokowanie w pobliżu**, przez Bluetooth lub w tej samej sieci.
- **Odblokowanie z powiadomienia**, bez otwierania aplikacji.
- **Zegarek z Wear OS** (Galaxy Watch, Pixel Watch i inne): prośba pojawia się na zegarku. Trzeba zainstalować na nim [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk).

## Dla programistów

| Folder | Zawartość |
| --- | --- |
| [`desktop/`](desktop) | Program na komputer (C++ 20 i Qt 6), moduł ekranu blokady Windows, moduł PAM dla Linuksa i agent, który odbiera prośby z telefonu |
| [`android/`](android) | Aplikacja na telefon (Kotlin i Jetpack Compose) oraz aplikacja na zegarki z Wear OS |
| [`scripts/`](scripts) | Skrypty kompilacji dla Windows i Linuksa |
| [`docs/`](docs) | Protokół między telefonem a komputerem, ikona i obrazy |

Jak skompilować, opisuje [`docs/COMPILAR.md`](docs/COMPILAR.md) (po portugalsku).

## Podziękowania i licencja

Program na komputer jest forkiem [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop) od MeisApps, z nowymi funkcjami: wiele parowań, odblokowanie z daleka przez Tailscale, blokowanie z telefonu, nowy interfejs i portugalski jako główny język. Aplikacja na telefon została napisana od zera i korzysta z tego samego protokołu.

Korzysta też z [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), czcionki [Inter](https://rsms.me/inter/) i [ikon Material](https://fonts.google.com/icons) od Google. Licencja każdego z nich jest na ekranie **O aplikacji** w aplikacjach.

Rozpowszechniany na licencji [GPL 3.0](LICENSE): możesz go używać, badać, zmieniać i rozpowszechniać, pod warunkiem że kod pozostanie otwarty.
