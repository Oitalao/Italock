[Português (Brasil)](README.md) · [English](README.en.md) · **Español** · [Français](README.fr.md) · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Icono de Ítalock">

# Ítalock

**Desbloquea la computadora con la huella del celular.**

En lugar de escribir la contraseña cada vez, la computadora se lo pide a tu celular Android y tú confirmas con la huella o el rostro. Desde el celular también puedes bloquear la computadora con un toque, de cerca o de lejos.

[![Versión](https://img.shields.io/github/v/release/Oitalao/Italock?label=versi%C3%B3n&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![Licencia GPL 3.0](https://img.shields.io/badge/licencia-GPL%203.0-003399)](LICENSE)
![Windows, Linux y Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-gratis-555)

</div>

## Descargar

Necesitas **dos cosas**: el programa en la computadora y la aplicación en el celular.

| Dónde | Archivo | Funciona en |
| --- | --- | --- |
| 💻 **Computadora con Windows** | [**Descargar ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 y 11 (64 bits) |
| 📱 **Celular Android** | [**Descargar Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 o más reciente |
| 🐧 Computadora con Linux | [Descargar ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 o más recientes |

Todo es gratis, sin publicidad, sin registro y sin necesidad de internet. Las versiones anteriores están en la página de [lanzamientos](https://github.com/Oitalao/Italock/releases).

## Qué hace

- 🔓 **Desbloquea la computadora con la huella o el rostro** del celular, en la pantalla de bloqueo y en la pantalla de inicio de sesión de Windows.
- 🔒 **Bloquea la computadora desde el celular**: desde la aplicación, desde el widget de la pantalla principal, desde el acceso directo del panel de notificaciones o acercando el celular a una etiqueta NFC.
- 🛜 **Funciona por Wi-Fi, cable USB o Bluetooth.** De lejos, también funciona con [Tailscale](https://tailscale.com/), una red privada gratuita.
- 🖥️ **Varias computadoras en el mismo celular**, y varios celulares en la misma computadora, sin límite.
- 🔑 **Tu contraseña sigue funcionando.** Ítalock es solo una forma más rápida de entrar. Si el celular no tiene batería o está lejos, escribe la contraseña como siempre.
- 🌐 **Disponible en 13 idiomas.** El programa se abre en portugués; para cambiarlo, entra en **Configurações** (Configuración) y elige **Idioma**, en la computadora y en el celular. Tema oscuro de fábrica, con opción de tema claro.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Pantalla principal de Ítalock en Windows">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Configuración de Ítalock en Windows">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="Aplicación en el celular con dos computadoras vinculadas">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="Aplicación en una tableta mostrando una computadora desbloqueada">
</p>

## Cómo instalar

Son tres pasos: instalar en la computadora, instalar en el celular y conectar los dos una sola vez.

### 1. En la computadora (Windows)

1. Descarga [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) y abre el archivo.
2. Windows puede mostrar el mensaje **"Windows protegió su PC"**. Aparece con los programas que no pagan la firma digital de Microsoft. Haz clic en **Más información** y luego en **Ejecutar de todas formas**.
3. Haz clic en **Sí** cuando Windows pida permiso y sigue el instalador hasta el final.
4. Ítalock se abre solo y deja un icono junto al reloj.

> [!IMPORTANT]
> **¿Entras en Windows con una cuenta Microsoft (la que tiene correo electrónico)?** Entonces Windows tiene que aceptar contraseña, y no solo PIN o rostro. Abre **Configuración › Cuentas › Opciones de inicio de sesión** y **desactiva** la opción *"Para mejorar la seguridad, permitir solo el inicio de sesión de Windows Hello para las cuentas Microsoft en este dispositivo"*. Si nunca creaste una contraseña para la cuenta, créala en [account.microsoft.com](https://account.microsoft.com/security).

### 2. En el celular (Android)

1. En el celular, descarga [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) y toca el archivo descargado.
2. Si Android avisa que no puede instalar aplicaciones de esta fuente, toca **Configuración**, activa **Permitir de esta fuente** y vuelve.
3. Si aparece un aviso de Google Play Protect, toca **Más detalles** y luego **Instalar de todas formas**. El aviso aparece con cualquier aplicación que no venga de Play Store.
4. Abre Ítalock y acepta las notificaciones. En **Configuración**, toca **Permitir** en cada elemento de la sección *Falta permitir*, para que Android no cierre la aplicación.

### 3. Conectar el celular a la computadora (solo una vez)

1. Deja la computadora y el celular **en el mismo Wi-Fi** (o conectados por cable USB).
2. En la computadora, abre Ítalock y haz clic en **Emparejar teléfono**. Aparece un código QR.
3. En el celular, abre Ítalock, toca **+** y luego **Escanear código QR**. Apunta la cámara a la pantalla de la computadora.
4. Listo. La computadora aparece en la pantalla del celular.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="Pantalla para vincular una computadora en el celular">
</p>

## Uso diario

**Para desbloquear:** con la computadora bloqueada, presiona cualquier tecla. Llega una notificación al celular; tócala y confirma con la huella o el rostro. La computadora se desbloquea sola.

También puedes desbloquear **desde el propio celular**, sin acercarte a la computadora: abre Ítalock y toca **Desbloquear** en la tarjeta de la computadora.

**Para bloquear:** toca **Bloquear** en la aplicación. Si prefieres, agrega el widget de Ítalock a la pantalla principal o el acceso directo al panel de notificaciones (desliza el panel, toca el lápiz y arrastra Ítalock).

## Preguntas frecuentes

<details>
<summary><b>¿Es seguro?</b></summary>

Sí. El celular y la computadora se comunican directamente, sin pasar por ningún servidor en internet, y todo va cifrado (AES 256). Cada pedido de desbloqueo necesita tu huella, tu rostro o el bloqueo de pantalla del celular, y solo vale por unos segundos. La contraseña de Windows queda cifrada en la computadora y la clave que abre ese cifrado queda solo en el celular: uno sin el otro no sirve para nada. Además, el código es abierto: cualquier persona puede revisar lo que hace el programa.

</details>

<details>
<summary><b>¿Y si pierdo el celular o se queda sin batería?</b></summary>

Escribe la contraseña normalmente. Ítalock nunca quita la contraseña, el PIN ni Windows Hello de la pantalla de inicio de sesión: solo agrega una opción más. Si pierdes el celular, abre Ítalock en la computadora y quita ese celular de la lista.

</details>

<details>
<summary><b>La notificación no llega al celular.</b></summary>

- Comprueba que los dos estén en el mismo Wi-Fi, o que el Bluetooth esté activado en ambos.
- En el celular, abre Ítalock › **Configuración** y permite todo lo que aparezca en *Falta permitir*, sobre todo el **ahorro de batería**. Algunos celulares (Xiaomi, Samsung, Motorola) cierran las aplicaciones en segundo plano; busca "Ítalock" en la configuración de batería del aparato y elige **Sin restricciones**.
- Si tienes antivirus o firewall en la computadora, permite Ítalock en él.

</details>

<details>
<summary><b>¿Funciona de lejos, fuera de casa?</b></summary>

Sí, con [Tailscale](https://tailscale.com/download), que es gratuito. Instala Tailscale en la computadora y en el celular e inicia sesión con la misma cuenta en los dos. Después, Ítalock encuentra la computadora desde cualquier lugar.

</details>

<details>
<summary><b>¿Funciona en iPhone?</b></summary>

No. Por ahora, solo Android.

</details>

<details>
<summary><b>¿Cómo desinstalarlo?</b></summary>

- **Windows:** Configuración › Aplicaciones › Ítalock › Desinstalar. Te pregunta si también quieres borrar los celulares vinculados.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** como cualquier otra aplicación.

</details>

## Linux

El instalador de Linux es un solo archivo. En la terminal, dentro de la carpeta donde se descargó:

```bash
sudo sh ItalockLinux.run
```

En Linux, Ítalock se encarga del **protector de pantalla** (la pantalla que aparece cuando la computadora se bloquea). El inicio de sesión, `sudo` y SSH siguen funcionando solo con la contraseña.

| Entorno | Desbloquear desde el celular | Bloquear desde el celular |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## Funciones experimentales

Vienen desactivadas de fábrica, en **Configuración › Funciones experimentales** en el celular. Son para quien disfruta probando cosas:

- **Desbloqueo automático por cercanía**, por Bluetooth o por la misma red.
- **Desbloquear desde la notificación**, sin abrir la aplicación.
- **Reloj Wear OS** (Galaxy Watch, Pixel Watch y otros): el pedido aparece en el reloj. Hay que instalar [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk) en el reloj.

## Para programadores

| Carpeta | Qué contiene |
| --- | --- |
| [`desktop/`](desktop) | Programa de la computadora (C++ 20 y Qt 6), el módulo de la pantalla de bloqueo de Windows, el módulo PAM de Linux y el agente que recibe los pedidos del celular |
| [`android/`](android) | Aplicación del celular (Kotlin y Jetpack Compose) y la aplicación del reloj Wear OS |
| [`scripts/`](scripts) | Scripts de compilación para Windows y Linux |
| [`docs/`](docs) | Protocolo entre celular y computadora, icono e imágenes |

Para compilar, consulta [`docs/COMPILAR.md`](docs/COMPILAR.md) (en portugués).

## Créditos y licencia

El programa de la computadora es un fork de [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop), de MeisApps, con funciones nuevas: varias vinculaciones, desbloqueo de lejos con Tailscale, bloqueo desde el celular, una interfaz nueva y el portugués como idioma principal. La aplicación del celular se escribió desde cero y usa el mismo protocolo.

También usa [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), la fuente [Inter](https://rsms.me/inter/) y los [iconos Material](https://fonts.google.com/icons) de Google. La licencia de cada uno está en la pantalla **Acerca de** de las aplicaciones.

Distribuido bajo la licencia [GPL 3.0](LICENSE): puedes usarlo, estudiarlo, modificarlo y redistribuirlo, siempre que mantengas el código abierto.
