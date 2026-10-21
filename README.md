<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Ícone do Ítalock">

# Ítalock

**Destrave o computador com a digital do celular.**

Em vez de digitar a senha toda vez, o computador pede ao seu celular Android e você confirma com a digital ou o rosto. Pelo celular, você também trava o computador com um toque, de perto ou de longe.

[![Versão](https://img.shields.io/github/v/release/Oitalao/Italock?label=vers%C3%A3o&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![Licença GPL 3.0](https://img.shields.io/badge/licen%C3%A7a-GPL%203.0-003399)](LICENSE)
![Windows, Linux e Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-gratuito-555)

</div>

## Baixar

Você precisa de **duas coisas**: o programa no computador e o aplicativo no celular.

| Onde | Arquivo | Funciona em |
| --- | --- | --- |
| 💻 **Computador com Windows** | [**Baixar ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 e 11 (64 bits) |
| 📱 **Celular Android** | [**Baixar Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 ou mais novo |
| 🐧 Computador com Linux | [Baixar ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 ou mais novos |

Tudo é gratuito, sem propaganda, sem cadastro e sem internet obrigatória. As versões anteriores ficam na página de [lançamentos](https://github.com/Oitalao/Italock/releases).

## O que ele faz

- 🔓 **Destrava o computador pela digital ou pelo rosto** do celular, na tela de bloqueio e na tela de entrada do Windows.
- 🔒 **Trava o computador pelo celular**: pelo aplicativo, pelo widget na tela inicial, pelo atalho da barra de notificações ou encostando o celular numa etiqueta NFC.
- 🛜 **Funciona por Wi-Fi, cabo USB ou Bluetooth.** De longe, também funciona pelo [Tailscale](https://tailscale.com/), uma rede privada gratuita.
- 🖥️ **Vários computadores no mesmo celular**, e vários celulares no mesmo computador, sem limite.
- 🔑 **Sua senha continua valendo.** O Ítalock é só um jeito mais rápido de entrar. Se o celular estiver sem bateria ou longe, é só digitar a senha como sempre.
- 🇧🇷 **Tudo em português.** Tema escuro de fábrica, com opção de tema claro.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Tela inicial do Ítalock no Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Configurações do Ítalock no Windows">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="Aplicativo do celular mostrando um computador destravado">
</p>

## Como instalar

São três passos: instalar no computador, instalar no celular e ligar os dois uma única vez.

### 1. No computador (Windows)

1. Baixe o [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) e abra o arquivo.
2. O Windows pode mostrar a mensagem **"O Windows protegeu o computador"**. Isso aparece com programas que não pagam pela assinatura digital da Microsoft. Clique em **Mais informações** e depois em **Executar assim mesmo**.
3. Clique em **Sim** quando o Windows pedir permissão e siga o instalador até o fim.
4. O Ítalock abre sozinho e fica com um ícone perto do relógio.

> [!IMPORTANT]
> **Usa conta Microsoft (aquela com e-mail) para entrar no Windows?** Então o Windows precisa aceitar senha, e não só PIN ou rosto. Abra **Configurações › Contas › Opções de entrada** e **desligue** a opção *"Para maior segurança, permitir apenas a entrada do Windows Hello para contas Microsoft neste dispositivo"*. Se você nunca criou uma senha para a conta, crie em [account.microsoft.com](https://account.microsoft.com/security).

### 2. No celular (Android)

1. No celular, baixe o [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) e toque no arquivo baixado.
2. Se o Android avisar que não pode instalar apps desta fonte, toque em **Configurações**, ligue **Permitir desta fonte** e volte.
3. Se aparecer um aviso do Google Play Protect, toque em **Mais detalhes** e em **Instalar mesmo assim**. O aviso aparece para qualquer app que não veio da Play Store.
4. Abra o Ítalock e aceite as notificações. Em **Configurações**, toque em **Liberar** em cada item da seção *Falta permitir*, para o Android não fechar o app.

### 3. Ligar o celular ao computador (só uma vez)

1. Deixe o computador e o celular **no mesmo Wi-Fi** (ou ligados pelo cabo USB).
2. No computador, abra o Ítalock e clique em **Parear celular**. Aparece um QR code.
3. No celular, abra o Ítalock, toque no **+** e em **Ler QR code**. Aponte a câmera para a tela do computador.
4. Pronto. O computador aparece na tela do celular.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Tela de parear computador no celular">
</p>

## Como usar no dia a dia

**Para destravar:** com o computador travado, aperte qualquer tecla. Chega uma notificação no celular; toque nela e confirme com a digital ou o rosto. O computador destrava sozinho.

Também dá para destravar **pelo próprio celular**, sem chegar perto do computador: abra o Ítalock e toque em **Destravar** no cartão do computador.

**Para travar:** toque em **Travar** no aplicativo. Se preferir, adicione o widget do Ítalock na tela inicial ou o atalho na barra de notificações (puxe a barra, toque no lápis e arraste o Ítalock).

## Perguntas frequentes

<details>
<summary><b>É seguro?</b></summary>

Sim. Celular e computador conversam direto, sem passar por nenhum servidor na internet, e tudo vai criptografado (AES 256). Cada pedido de destravar precisa da sua digital, do seu rosto ou do bloqueio de tela do celular, e só vale por alguns segundos. A senha do Windows fica cifrada no computador e a chave que abre essa cifra fica só no celular: um sem o outro não serve para nada. E o código é aberto: qualquer pessoa pode conferir o que o programa faz.

</details>

<details>
<summary><b>E se eu perder o celular ou ele ficar sem bateria?</b></summary>

Digite a senha normalmente. O Ítalock nunca tira a senha, o PIN ou o Windows Hello da tela de entrada: ele só acrescenta mais uma opção. Se perder o celular, abra o Ítalock no computador e remova esse celular da lista.

</details>

<details>
<summary><b>A notificação não chega no celular.</b></summary>

- Confira se os dois estão no mesmo Wi-Fi, ou se o Bluetooth está ligado nos dois.
- No celular, abra o Ítalock › **Configurações** e libere tudo que estiver em *Falta permitir*, principalmente a **economia de bateria**. Alguns celulares (Xiaomi, Samsung, Motorola) fecham apps em segundo plano; procure por "Ítalock" nas configurações de bateria do aparelho e escolha **Sem restrições**.
- Se tiver antivírus ou firewall no computador, libere o Ítalock nele.

</details>

<details>
<summary><b>Funciona de longe, fora de casa?</b></summary>

Sim, com o [Tailscale](https://tailscale.com/download), que é gratuito. Instale o Tailscale no computador e no celular e entre com a mesma conta nos dois. Depois disso, o Ítalock encontra o computador de qualquer lugar.

</details>

<details>
<summary><b>Funciona no iPhone?</b></summary>

Não. Por enquanto, só Android.

</details>

<details>
<summary><b>Como desinstalar?</b></summary>

- **Windows:** Configurações › Apps › Ítalock › Desinstalar. Ele pergunta se você quer apagar também os celulares pareados.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** como qualquer outro aplicativo.

</details>

## Linux

O instalador do Linux é um arquivo único. No terminal, dentro da pasta onde ele foi baixado:

```bash
sudo sh ItalockLinux.run
```

No Linux, o Ítalock protege a **proteção de tela** (a tela que aparece quando o computador trava). O login, o `sudo` e o SSH continuam só com a senha.

| Ambiente | Destravar pelo celular | Travar pelo celular |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Recursos experimentais

Ficam desligados de fábrica, em **Configurações › Experimentais** no celular. São para quem gosta de testar:

- **Destravar sozinho quando o celular estiver perto**, por Bluetooth ou pela mesma rede.
- **Destravar pela notificação**, sem abrir o aplicativo.
- **Relógio Wear OS** (Galaxy Watch, Pixel Watch e outros): o pedido aparece no relógio. Precisa instalar o [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk) no relógio.

## Para quem programa

| Pasta | O que tem |
| --- | --- |
| [`desktop/`](desktop) | Programa do computador (C++ 20 e Qt 6), o módulo da tela de bloqueio do Windows, o módulo PAM do Linux e o agente que recebe os pedidos do celular |
| [`android/`](android) | Aplicativo do celular (Kotlin e Jetpack Compose) e o app do relógio Wear OS |
| [`scripts/`](scripts) | Scripts de compilação para Windows e Linux |
| [`docs/`](docs) | Protocolo entre celular e computador, ícone e imagens |

Para compilar, veja [`docs/COMPILAR.md`](docs/COMPILAR.md).

## Créditos e licença

O programa do computador é um fork do [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop), da MeisApps, com recursos novos: vários pareamentos, destravar de longe pelo Tailscale, travar pelo celular, interface nova e tudo em português. O aplicativo do celular foi escrito do zero e conversa com o mesmo protocolo.

Também usa [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), a fonte [Inter](https://rsms.me/inter/) e os [ícones Material](https://fonts.google.com/icons) do Google. As licenças de cada um ficam na tela **Sobre** dos aplicativos.

Distribuído sob a licença [GPL 3.0](LICENSE): pode usar, estudar, modificar e redistribuir, desde que mantenha o código aberto.
