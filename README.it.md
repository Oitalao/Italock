[Português (Brasil)](README.md) · [English](README.en.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · **Italiano** · [日本語](README.ja.md) · [한국어](README.ko.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Icona di Ítalock">

# Ítalock

**Sblocca il computer con l'impronta del telefono.**

Invece di digitare la password ogni volta, il computer la chiede al tuo telefono Android e tu confermi con l'impronta o con il volto. Dal telefono puoi anche bloccare il computer con un tocco, da vicino o da lontano.

[![Versione](https://img.shields.io/github/v/release/Oitalao/Italock?label=versione&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![Licenza GPL 3.0](https://img.shields.io/badge/licenza-GPL%203.0-003399)](LICENSE)
![Windows, Linux e Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-gratuito-555)

</div>

## Scarica

Ti servono **due cose**: il programma sul computer e l'app sul telefono.

| Dove | File | Funziona su |
| --- | --- | --- |
| 💻 **Computer con Windows** | [**Scarica ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 e 11 (64 bit) |
| 📱 **Telefono Android** | [**Scarica Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 o successivo |
| 🐧 Computer con Linux | [Scarica ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 o successivi |

È tutto gratuito, senza pubblicità, senza registrazione e senza bisogno di internet. Le versioni precedenti si trovano nella pagina delle [release](https://github.com/Oitalao/Italock/releases).

## Cosa fa

- 🔓 **Sblocca il computer con l'impronta o il volto** del telefono, sulla schermata di blocco e sulla schermata di accesso di Windows.
- 🔒 **Blocca il computer dal telefono**: dall'app, dal widget nella schermata Home, dalla scorciatoia nel pannello delle notifiche o avvicinando il telefono a un tag NFC.
- 🛜 **Funziona via Wi-Fi, cavo USB o Bluetooth.** Da lontano funziona anche tramite [Tailscale](https://tailscale.com/), una rete privata gratuita.
- 🖥️ **Più computer sullo stesso telefono**, e più telefoni sullo stesso computer, senza limiti.
- 🔑 **La tua password resta valida.** Ítalock è solo un modo più rapido per entrare. Se il telefono è scarico o lontano, digita la password come sempre.
- 🌐 **Disponibile in 13 lingue.** Il programma si apre in portoghese; per cambiare lingua, apri **Configurações** (Impostazioni) e scegli **Idioma** (Lingua), sia sul computer sia sul telefono. Tema scuro di serie, con opzione per il tema chiaro.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Schermata principale di Ítalock su Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Impostazioni di Ítalock su Windows">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="App sul telefono con due computer associati">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="App su un tablet che mostra un computer sbloccato">
</p>

## Come installare

Sono tre passaggi: installare sul computer, installare sul telefono e collegare i due una sola volta.

### 1. Sul computer (Windows)

1. Scarica [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) e apri il file.
2. Windows potrebbe mostrare il messaggio **"PC protetto da Windows"**. Compare con i programmi che non pagano la firma digitale di Microsoft. Fai clic su **Ulteriori informazioni** e poi su **Esegui comunque**.
3. Fai clic su **Sì** quando Windows chiede l'autorizzazione e segui l'installazione fino alla fine.
4. Ítalock si apre da solo e lascia un'icona vicino all'orologio.

> [!IMPORTANT]
> **Accedi a Windows con un account Microsoft (quello con l'indirizzo email)?** Allora Windows deve accettare la password, e non solo il PIN o il volto. Apri **Impostazioni › Account › Opzioni di accesso** e **disattiva** l'opzione *"Per una maggiore sicurezza, consenti solo l'accesso con Windows Hello per gli account Microsoft su questo dispositivo"*. Se non hai mai creato una password per l'account, creala su [account.microsoft.com](https://account.microsoft.com/security).

### 2. Sul telefono (Android)

1. Sul telefono, scarica [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) e tocca il file scaricato.
2. Se Android avvisa che non può installare app da questa fonte, tocca **Impostazioni**, attiva **Consenti da questa fonte** e torna indietro.
3. Se compare un avviso di Google Play Protect, tocca **Altri dettagli** e poi **Installa comunque**. L'avviso compare per qualsiasi app che non provenga dal Play Store.
4. Apri Ítalock e accetta le notifiche. In **Impostazioni**, tocca **Consenti** / **Escludi** su ogni voce della sezione *Permessi mancanti*, così Android non chiude l'app.

### 3. Collegare il telefono al computer (una sola volta)

1. Tieni il computer e il telefono **sulla stessa rete Wi-Fi** (o collegati con il cavo USB).
2. Sul computer, apri Ítalock e fai clic su **Associa telefono**. Compare un codice QR.
3. Sul telefono, apri Ítalock, tocca **+** e poi **Scansiona QR code**. Inquadra lo schermo del computer con la fotocamera.
4. Fatto. Il computer compare sullo schermo del telefono.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Schermata per associare un computer sul telefono">
</p>

## Uso quotidiano

**Per sbloccare:** con il computer bloccato, premi un tasto qualsiasi. Sul telefono arriva una notifica; toccala e conferma con l'impronta o il volto. Il computer si sblocca da solo.

Puoi anche sbloccare **direttamente dal telefono**, senza avvicinarti al computer: apri Ítalock e tocca **Sblocca** nella scheda del computer.

**Per bloccare:** tocca **Blocca** nell'app. Se preferisci, aggiungi il widget di Ítalock alla schermata Home o la scorciatoia al pannello delle notifiche (abbassa il pannello, tocca la matita e trascina Ítalock).

## Domande frequenti

<details>
<summary><b>È sicuro?</b></summary>

Sì. Il telefono e il computer comunicano direttamente, senza passare da nessun server su internet, e tutto viaggia cifrato (AES 256). Ogni richiesta di sblocco richiede la tua impronta, il tuo volto o il blocco schermo del telefono, e vale solo per pochi secondi. La password di Windows resta cifrata sul computer e la chiave per decifrarla resta solo sul telefono: l'una senza l'altra non serve a nulla. E il codice è aperto: chiunque può verificare cosa fa il programma.

</details>

<details>
<summary><b>E se perdo il telefono o si scarica?</b></summary>

Digita la password normalmente. Ítalock non toglie mai la password, il PIN o Windows Hello dalla schermata di accesso: aggiunge soltanto un'opzione in più. Se perdi il telefono, apri Ítalock sul computer e rimuovi quel telefono dall'elenco.

</details>

<details>
<summary><b>La notifica non arriva sul telefono.</b></summary>

- Controlla che entrambi siano sulla stessa rete Wi-Fi, oppure che il Bluetooth sia attivo su tutti e due.
- Sul telefono, apri Ítalock › **Impostazioni** e consenti tutto ciò che compare in *Permessi mancanti*, soprattutto il **risparmio energetico**. Alcuni telefoni (Xiaomi, Samsung, Motorola) chiudono le app in background; cerca "Ítalock" nelle impostazioni della batteria del dispositivo e scegli **Senza restrizioni**.
- Se hai un antivirus o un firewall sul computer, consenti Ítalock al suo interno.

</details>

<details>
<summary><b>Funziona da lontano, fuori casa?</b></summary>

Sì, con [Tailscale](https://tailscale.com/download), che è gratuito. Installa Tailscale sul computer e sul telefono e accedi con lo stesso account su entrambi. Da quel momento Ítalock trova il computer ovunque tu sia.

</details>

<details>
<summary><b>Funziona su iPhone?</b></summary>

No. Per ora, solo Android.

</details>

<details>
<summary><b>Come si disinstalla?</b></summary>

- **Windows:** Impostazioni › App › Ítalock › Disinstalla. Ti chiede se vuoi cancellare anche i telefoni associati.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** come qualsiasi altra app.

</details>

## Linux

L'installer per Linux è un unico file. Nel terminale, dentro la cartella in cui è stato scaricato:

```bash
sudo sh ItalockLinux.run
```

Su Linux, Ítalock gestisce il **salvaschermo** (la schermata che appare quando il computer si blocca). L'accesso, `sudo` e SSH continuano a funzionare solo con la password.

| Ambiente | Sbloccare dal telefono | Bloccare dal telefono |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Funzioni sperimentali

Sono disattivate di serie, in **Impostazioni › Funzioni sperimentali** sul telefono. Sono per chi ama provare:

- **Sblocco automatico da vicino**, via Bluetooth o sulla stessa rete.
- **Sblocca dalla notifica**, senza aprire l'app.
- **Orologio Wear OS** (Galaxy Watch, Pixel Watch e altri): la richiesta compare sull'orologio. Bisogna installare [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk) sull'orologio.

## Per chi programma

| Cartella | Contenuto |
| --- | --- |
| [`desktop/`](desktop) | Programma per il computer (C++ 20 e Qt 6), il modulo della schermata di blocco di Windows, il modulo PAM di Linux e l'agente che riceve le richieste dal telefono |
| [`android/`](android) | App per il telefono (Kotlin e Jetpack Compose) e l'app per orologi Wear OS |
| [`scripts/`](scripts) | Script di compilazione per Windows e Linux |
| [`docs/`](docs) | Protocollo tra telefono e computer, icona e immagini |

Per compilare, consulta [`docs/COMPILAR.md`](docs/COMPILAR.md) (in portoghese).

## Riconoscimenti e licenza

Il programma per il computer è un fork di [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop), di MeisApps, con nuove funzioni: più associazioni, sblocco da lontano tramite Tailscale, blocco dal telefono, una nuova interfaccia e il portoghese come lingua principale. L'app per il telefono è stata scritta da zero e usa lo stesso protocollo.

Usa anche [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), il carattere [Inter](https://rsms.me/inter/) e le [icone Material](https://fonts.google.com/icons) di Google. Le licenze di ciascuno si trovano nella schermata **Informazioni** delle app.

Distribuito con licenza [GPL 3.0](LICENSE): puoi usarlo, studiarlo, modificarlo e ridistribuirlo, purché il codice resti aperto.
