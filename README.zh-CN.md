[Português (Brasil)](README.md) · [English](README.en.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · **简体中文** · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Ítalock 图标">

# Ítalock

**用手机指纹解锁电脑。**

不用每次都输入密码：电脑会向你的 Android 手机发出请求，你用指纹或面容确认即可。你还可以在手机上一键锁定电脑，无论离得近还是远。

[![版本](https://img.shields.io/github/v/release/Oitalao/Italock?label=%E7%89%88%E6%9C%AC&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![许可证 GPL 3.0](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-GPL%203.0-003399)](LICENSE)
![Windows、Linux 和 Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-%E5%85%8D%E8%B4%B9-555)

</div>

## 下载

你需要 **两样东西**：电脑上的程序和手机上的应用。

| 位置 | 文件 | 支持的系统 |
| --- | --- | --- |
| 💻 **Windows 电脑** | [**下载 ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10 和 11（64 位） |
| 📱 **Android 手机** | [**下载 Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 或更高版本 |
| 🐧 Linux 电脑 | [下载 ItalockLinux.run](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04、Linux Mint 21、Debian 12 或更高版本 |

全部免费，没有广告，无需注册，也不必联网。旧版本可以在 [发布](https://github.com/Oitalao/Italock/releases) 页面找到。

## 功能

- 🔓 **用手机的指纹或面容解锁电脑**，在锁屏界面和 Windows 登录界面都可以。
- 🔒 **用手机锁定电脑**：可以在应用里、主屏幕小部件上、通知栏快捷开关上操作，也可以把手机贴近 NFC 标签。
- 🛜 **支持 Wi-Fi、USB 数据线或蓝牙。** 不在身边时，也可以通过免费的私人网络 [Tailscale](https://tailscale.com/) 使用。
- 🖥️ **一部手机可以连接多台电脑**，一台电脑也可以连接多部手机，没有数量限制。
- 🔑 **你的密码照样有效。** Ítalock 只是让你更快登录的一种方式。手机没电或不在身边时，照常输入密码就行。
- 🌐 **支持 13 种语言。** 程序默认以葡萄牙语打开；如需更改，请在电脑和手机上打开 **Configurações**（设置），选择 **Idioma**（语言）。默认深色主题，也可以切换为浅色主题。

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Windows 上的 Ítalock 主界面">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Windows 上的 Ítalock 设置界面">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="已配对两台电脑的手机应用">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="平板上的应用，显示一台已解锁的电脑">
</p>

## 如何安装

共三步：在电脑上安装，在手机上安装，然后把两者连接一次。

### 1. 在电脑上（Windows）

1. 下载 [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) 并打开它。
2. Windows 可能会显示 **“Windows 已保护你的电脑”**。没有购买 Microsoft 数字签名的程序都会出现这个提示。点击 **更多信息**，再点击 **仍要运行**。
3. 当 Windows 请求权限时点击 **是**，然后按照安装程序完成安装。
4. Ítalock 会自动打开，并在时钟旁边显示一个图标。

> [!IMPORTANT]
> **你用 Microsoft 帐户（带电子邮件地址的那种）登录 Windows 吗？** 那么 Windows 必须能接受密码，而不只是 PIN 或面容。打开 **设置 › 帐户 › 登录选项**，**关闭** *“为了提高安全性，仅允许对此设备上的 Microsoft 帐户使用 Windows Hello 登录”* 这一选项。如果你从未给这个帐户设置过密码，请在 [account.microsoft.com](https://account.microsoft.com/security) 上创建一个。

### 2. 在手机上（Android）

1. 在手机上下载 [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk)，然后点按下载好的文件。
2. 如果 Android 提示无法安装来自此来源的应用，请点按 **设置**，开启 **允许来自此来源的应用**，然后返回。
3. 如果出现 Google Play 保护机制的警告，请点按 **更多详情**，再点按 **仍然安装**。所有不是来自 Play 商店的应用都会出现这个警告。
4. 打开 Ítalock 并允许通知。在 **设置** 中，对 *尚需允许* 部分的每一项点按 **允许** / **解除限制**，以免 Android 关闭这个应用。

### 3. 连接手机和电脑（只需一次）

1. 让电脑和手机 **连接同一个 Wi-Fi**（或用 USB 数据线连接）。
2. 在电脑上打开 Ítalock，点击 **配对手机**，屏幕上会出现一个二维码。
3. 在手机上打开 Ítalock，点按 **+**，再点按 **扫描二维码**，把摄像头对准电脑屏幕。
4. 完成。电脑会出现在手机屏幕上。

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="手机上的配对电脑界面">
</p>

## 日常使用

**解锁：** 电脑锁定时，按任意键。手机会收到一条通知，点按它，再用指纹或面容确认，电脑就会自动解锁。

你也可以 **直接在手机上** 解锁，不用走到电脑旁：打开 Ítalock，在电脑的卡片上点按 **解锁**。

**锁定：** 在应用中点按 **锁定**。你也可以把 Ítalock 小部件添加到主屏幕，或把快捷开关添加到通知栏（下拉通知栏，点按铅笔图标，然后拖入 Ítalock）。

## 常见问题

<details>
<summary><b>安全吗？</b></summary>

安全。手机和电脑直接通信，不经过互联网上的任何服务器，所有内容都经过加密（AES 256）。每次解锁请求都需要你的指纹、面容或手机的屏幕锁，并且只在几秒钟内有效。Windows 密码以加密形式保存在电脑上，而解开它的密钥只保存在手机上：两者缺一不可。此外，代码是开源的：任何人都可以检查这个程序做了什么。

</details>

<details>
<summary><b>手机丢了或者没电了怎么办？</b></summary>

照常输入密码即可。Ítalock 绝不会从登录界面移除密码、PIN 或 Windows Hello，它只是多加了一个选项。如果手机丢了，在电脑上打开 Ítalock，把这部手机从列表中删除。

</details>

<details>
<summary><b>手机收不到通知。</b></summary>

- 检查两台设备是否连接同一个 Wi-Fi，或者是否都开启了蓝牙。
- 在手机上打开 Ítalock › **设置**，允许 *尚需允许* 中的所有项目，尤其是 **省电设置**。有些手机（小米、三星、摩托罗拉）会关闭后台应用；请在手机的电池设置中搜索 “Ítalock”，并选择 **无限制**。
- 如果电脑上装有杀毒软件或防火墙，请在其中允许 Ítalock。

</details>

<details>
<summary><b>在外面、离得很远也能用吗？</b></summary>

可以，使用免费的 [Tailscale](https://tailscale.com/download) 即可。在电脑和手机上都安装 Tailscale，并用同一个帐户登录。之后，Ítalock 在任何地方都能找到你的电脑。

</details>

<details>
<summary><b>iPhone 能用吗？</b></summary>

不能。目前只支持 Android。

</details>

<details>
<summary><b>如何卸载？</b></summary>

- **Windows：** 设置 › 应用 › Ítalock › 卸载。它会询问你是否同时删除已配对的手机。
- **Linux：** `sudo sh ItalockLinux.run --desinstalar`
- **Android：** 和卸载其他应用一样。

</details>

## Linux

Linux 安装程序只有一个文件。在终端中进入下载它的文件夹，然后运行：

```bash
sudo sh ItalockLinux.run
```

在 Linux 上，Ítalock 负责 **屏幕保护程序**（电脑锁定时出现的画面）。登录、`sudo` 和 SSH 仍然只使用密码。

| 桌面环境 | 用手机解锁 | 用手机锁定 |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## 实验性功能

这些功能默认关闭，位于手机上的 **设置 › 实验性功能**。适合喜欢尝鲜的人：

- **靠近时自动解锁**，通过蓝牙或同一网络。
- **从通知解锁**，无需打开应用。
- **Wear OS 手表**（Galaxy Watch、Pixel Watch 等）：请求会显示在手表上。需要在手表上安装 [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk)。

## 开发者信息

| 文件夹 | 内容 |
| --- | --- |
| [`desktop/`](desktop) | 电脑端程序（C++ 20 和 Qt 6）、Windows 锁屏模块、Linux PAM 模块，以及接收手机请求的代理程序 |
| [`android/`](android) | 手机应用（Kotlin 和 Jetpack Compose）以及 Wear OS 手表应用 |
| [`scripts/`](scripts) | Windows 和 Linux 的编译脚本 |
| [`docs/`](docs) | 手机与电脑之间的协议、图标和图片 |

编译方法请参阅 [`docs/COMPILAR.md`](docs/COMPILAR.md)（葡萄牙语）。

## 致谢与许可证

电脑端程序是 MeisApps 的 [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop) 的分支，并增加了新功能：多设备配对、通过 Tailscale 远程解锁、用手机锁定、全新的界面，以及以葡萄牙语为主要语言。手机应用是从零开始编写的，使用相同的协议通信。

它还使用了 [Qt](https://www.qt.io/)、[OpenSSL](https://www.openssl.org/)、[Inter](https://rsms.me/inter/) 字体和 Google 的 [Material 图标](https://fonts.google.com/icons)。各自的许可证可以在应用的 **关于** 界面中查看。

以 [GPL 3.0](LICENSE) 许可证发布：你可以使用、研究、修改和再分发它，只要保持代码开源。
