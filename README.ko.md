[Português (Brasil)](README.md) · [English](README.en.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · **한국어** · [简体中文](README.zh-CN.md) · [Русский](README.ru.md) · [Polski](README.pl.md) · [हिन्दी](README.hi.md) · [العربية](README.ar.md)

<div align="center">

<img src="desktop/desktop/res/icons/hicolor/256x256/apps/italock.png" width="112" alt="Ítalock 아이콘">

# Ítalock

**휴대폰 지문으로 컴퓨터 잠금을 해제하세요.**

매번 비밀번호를 입력하는 대신, 컴퓨터가 Android 휴대폰에 요청을 보내고 지문이나 얼굴로 확인하면 됩니다. 휴대폰에서 한 번만 탭하면 컴퓨터를 잠글 수도 있습니다. 가까이 있든 멀리 있든 상관없습니다.

[![버전](https://img.shields.io/github/v/release/Oitalao/Italock?label=%EB%B2%84%EC%A0%84&color=EC7000)](https://github.com/Oitalao/Italock/releases/latest)
[![라이선스 GPL 3.0](https://img.shields.io/badge/%EB%9D%BC%EC%9D%B4%EC%84%A0%EC%8A%A4-GPL%203.0-003399)](LICENSE)
![Windows, Linux, Android](https://img.shields.io/badge/Windows%20%7C%20Linux%20%7C%20Android-%EB%AC%B4%EB%A3%8C-555)

</div>

## 다운로드

**두 가지**가 필요합니다. 컴퓨터용 프로그램과 휴대폰용 앱입니다.

| 설치할 곳 | 파일 | 지원 환경 |
| --- | --- | --- |
| 💻 **Windows 컴퓨터** | [**ItalockWindows.exe 다운로드**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe) | Windows 10, 11 (64비트) |
| 📱 **Android 휴대폰** | [**Italock.apk 다운로드**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk) | Android 6 이상 |
| 🐧 Linux 컴퓨터 | [ItalockLinux.run 다운로드](https://github.com/Oitalao/Italock/releases/latest/download/ItalockLinux.run) | Ubuntu 22.04, Linux Mint 21, Debian 12 이상 |

모두 무료이며, 광고도 회원가입도 없고 인터넷 연결도 꼭 필요하지 않습니다. 이전 버전은 [릴리스](https://github.com/Oitalao/Italock/releases) 페이지에 있습니다.

## 주요 기능

- 🔓 **휴대폰의 지문이나 얼굴로 컴퓨터 잠금 해제.** 잠금 화면과 Windows 로그인 화면에서 모두 됩니다.
- 🔒 **휴대폰으로 컴퓨터 잠그기.** 앱에서, 홈 화면 위젯에서, 알림창 바로가기에서, 또는 휴대폰을 NFC 태그에 대서 잠글 수 있습니다.
- 🛜 **Wi-Fi, USB 케이블, Bluetooth로 작동합니다.** 멀리 떨어져 있을 때는 무료 사설 네트워크인 [Tailscale](https://tailscale.com/)로도 작동합니다.
- 🖥️ **휴대폰 한 대에 여러 컴퓨터**, 컴퓨터 한 대에 여러 휴대폰을 제한 없이 연결할 수 있습니다.
- 🔑 **비밀번호는 그대로 쓸 수 있습니다.** Ítalock은 더 빨리 로그인하는 방법일 뿐입니다. 휴대폰 배터리가 없거나 멀리 있으면 평소처럼 비밀번호를 입력하면 됩니다.
- 🌐 **13개 언어 지원.** 프로그램은 처음에 포르투갈어로 열립니다. 언어를 바꾸려면 컴퓨터와 휴대폰에서 **Configurações**(설정)를 열고 **Idioma**(언어)를 선택하세요. 기본은 어두운 테마이며 밝은 테마로 바꿀 수 있습니다.

<p align="center">
  <img src="docs/prints/windows-inicio.png" width="49%" alt="Windows용 Ítalock 시작 화면">
  <img src="docs/prints/windows-configuracoes.png" width="49%" alt="Windows용 Ítalock 설정 화면">
</p>
<p align="center">
  <img src="docs/prints/celular-inicio.png" width="45%" alt="컴퓨터 두 대가 페어링된 휴대폰 앱">
</p>
<p align="center">
  <img src="docs/prints/android-inicio.png" width="98%" alt="잠금 해제된 컴퓨터를 보여 주는 태블릿 앱">
</p>

## 설치 방법

세 단계입니다. 컴퓨터에 설치하고, 휴대폰에 설치한 다음, 둘을 한 번만 연결하면 됩니다.

### 1. 컴퓨터 (Windows)

1. [**ItalockWindows.exe**](https://github.com/Oitalao/Italock/releases/latest/download/ItalockWindows.exe)를 다운로드하고 파일을 엽니다.
2. **"Windows의 PC 보호"** 메시지가 나타날 수 있습니다. Microsoft 디지털 서명 비용을 내지 않은 프로그램에서 나타나는 메시지입니다. **추가 정보**를 클릭한 다음 **실행**을 클릭하세요.
3. Windows가 권한을 요청하면 **예**를 클릭하고 설치 프로그램을 끝까지 진행합니다.
4. Ítalock이 자동으로 열리고 시계 옆에 아이콘이 표시됩니다.

> [!IMPORTANT]
> **Windows에 Microsoft 계정(이메일 주소로 된 계정)으로 로그인하나요?** 그렇다면 Windows가 PIN이나 얼굴만이 아니라 비밀번호도 받아들이도록 해야 합니다. **설정 › 계정 › 로그인 옵션**을 열고 *"보안 향상을 위해 이 장치의 Microsoft 계정에 대해 Windows Hello 로그인만 허용"* 옵션을 **끄세요**. 계정 비밀번호를 만든 적이 없다면 [account.microsoft.com](https://account.microsoft.com/security)에서 만드세요.

### 2. 휴대폰 (Android)

1. 휴대폰에서 [**Italock.apk**](https://github.com/Oitalao/Italock/releases/latest/download/Italock.apk)를 다운로드하고 받은 파일을 탭합니다.
2. Android가 이 출처의 앱은 설치할 수 없다고 알리면 **설정**을 탭하고 **이 출처 허용**을 켠 뒤 돌아옵니다.
3. Google Play 프로텍트 경고가 나타나면 **세부정보 더보기**를 탭하고 **무시하고 설치**를 탭합니다. 이 경고는 Play 스토어에서 받지 않은 모든 앱에 나타납니다.
4. Ítalock을 열고 알림을 허용합니다. Android가 앱을 종료하지 않도록 **설정**의 *허용 필요* 항목마다 **허용** / **제외하기** 버튼을 탭하세요.

### 3. 휴대폰과 컴퓨터 연결하기 (한 번만)

1. 컴퓨터와 휴대폰을 **같은 Wi-Fi**에 연결합니다 (또는 USB 케이블로 연결합니다).
2. 컴퓨터에서 Ítalock을 열고 **휴대폰 페어링**을 클릭합니다. QR 코드가 나타납니다.
3. 휴대폰에서 Ítalock을 열고 **+** 버튼을 탭한 다음 **QR 코드 스캔**을 탭합니다. 카메라를 컴퓨터 화면에 비추세요.
4. 끝입니다. 휴대폰 화면에 컴퓨터가 나타납니다.

<p align="center">
  <img src="docs/prints/android-parear.png" width="98%" alt="휴대폰의 컴퓨터 페어링 화면">
</p>

## 일상에서 쓰는 법

**잠금 해제:** 컴퓨터가 잠겨 있을 때 아무 키나 누르세요. 휴대폰에 알림이 오면 탭하고 지문이나 얼굴로 확인합니다. 컴퓨터 잠금이 자동으로 해제됩니다.

컴퓨터 가까이 가지 않고 **휴대폰에서 바로** 잠금을 해제할 수도 있습니다. Ítalock을 열고 컴퓨터 카드의 **잠금 해제**를 탭하세요.

**잠그기:** 앱에서 **잠금**을 탭합니다. 원하면 Ítalock 위젯을 홈 화면에 추가하거나 바로가기를 알림창에 추가할 수 있습니다 (알림창을 내리고 연필 아이콘을 탭한 뒤 Ítalock을 끌어다 놓으세요).

## 자주 묻는 질문

<details>
<summary><b>안전한가요?</b></summary>

네. 휴대폰과 컴퓨터는 인터넷의 어떤 서버도 거치지 않고 직접 통신하며, 모든 내용은 암호화됩니다 (AES 256). 잠금 해제 요청마다 지문, 얼굴 또는 휴대폰 화면 잠금이 필요하고, 요청은 몇 초 동안만 유효합니다. Windows 비밀번호는 컴퓨터에 암호화되어 저장되고, 이를 여는 키는 휴대폰에만 있습니다. 둘 중 하나만으로는 아무 소용이 없습니다. 또한 코드가 공개되어 있어 누구나 프로그램이 하는 일을 확인할 수 있습니다.

</details>

<details>
<summary><b>휴대폰을 잃어버리거나 배터리가 다 되면요?</b></summary>

평소처럼 비밀번호를 입력하세요. Ítalock은 로그인 화면에서 비밀번호, PIN, Windows Hello를 절대 없애지 않습니다. 선택지를 하나 더 추가할 뿐입니다. 휴대폰을 잃어버렸다면 컴퓨터에서 Ítalock을 열고 목록에서 그 휴대폰을 삭제하세요.

</details>

<details>
<summary><b>휴대폰에 알림이 오지 않아요.</b></summary>

- 둘이 같은 Wi-Fi에 있는지, 또는 양쪽 모두 Bluetooth가 켜져 있는지 확인하세요.
- 휴대폰에서 Ítalock › **설정**을 열고 *허용 필요*에 있는 항목을 모두 허용하세요. 특히 **배터리 절약**이 중요합니다. 일부 휴대폰(Xiaomi, Samsung, Motorola)은 백그라운드 앱을 종료합니다. 기기의 배터리 설정에서 "Ítalock"을 찾아 **제한 없음**을 선택하세요.
- 컴퓨터에 백신 프로그램이나 방화벽이 있다면 그곳에서 Ítalock을 허용하세요.

</details>

<details>
<summary><b>집 밖처럼 멀리서도 되나요?</b></summary>

네, 무료인 [Tailscale](https://tailscale.com/download)을 쓰면 됩니다. 컴퓨터와 휴대폰에 Tailscale을 설치하고 두 기기에서 같은 계정으로 로그인하세요. 그러면 Ítalock이 어디서든 컴퓨터를 찾습니다.

</details>

<details>
<summary><b>iPhone에서도 되나요?</b></summary>

아니요. 지금은 Android만 지원합니다.

</details>

<details>
<summary><b>어떻게 삭제하나요?</b></summary>

- **Windows:** 설정 › 앱 › Ítalock › 제거. 페어링된 휴대폰도 함께 지울지 묻습니다.
- **Linux:** `sudo sh ItalockLinux.run --desinstalar`
- **Android:** 다른 앱과 똑같이 삭제합니다.

</details>

## Linux

Linux용 설치 프로그램은 파일 하나입니다. 터미널에서, 파일을 내려받은 폴더 안에서 실행하세요.

```bash
sudo sh ItalockLinux.run
```

Linux에서 Ítalock은 **화면 보호기**(컴퓨터가 잠길 때 나타나는 화면)를 담당합니다. 로그인, `sudo`, SSH는 계속 비밀번호로만 사용합니다.

| 데스크톱 환경 | 휴대폰으로 잠금 해제 | 휴대폰으로 잠그기 |
| --- | :---: | :---: |
| Cinnamon (Linux Mint), MATE, XFCE | ✅ | ✅ |
| GNOME (Ubuntu), KDE Plasma | ❌ | ✅ |

## 실험적 기능

기본으로 꺼져 있으며, 휴대폰의 **설정 › 실험적 기능**에 있습니다. 새로운 기능을 시험해 보고 싶은 분을 위한 것입니다.

- **가까이 있을 때 자동 잠금 해제.** Bluetooth나 같은 네트워크를 통해 작동합니다.
- **알림에서 잠금 해제.** 앱을 열 필요가 없습니다.
- **Wear OS 워치** (Galaxy Watch, Pixel Watch 등): 요청이 시계에 표시됩니다. 시계에 [ItalockRelogio.apk](https://github.com/Oitalao/Italock/releases/latest/download/ItalockRelogio.apk)를 설치해야 합니다.

## 개발자를 위한 정보

| 폴더 | 내용 |
| --- | --- |
| [`desktop/`](desktop) | 컴퓨터용 프로그램 (C++ 20, Qt 6), Windows 잠금 화면 모듈, Linux PAM 모듈, 휴대폰의 요청을 받는 에이전트 |
| [`android/`](android) | 휴대폰 앱 (Kotlin, Jetpack Compose)과 Wear OS 시계 앱 |
| [`scripts/`](scripts) | Windows와 Linux용 빌드 스크립트 |
| [`docs/`](docs) | 휴대폰과 컴퓨터 사이의 프로토콜, 아이콘, 이미지 |

빌드 방법은 [`docs/COMPILAR.md`](docs/COMPILAR.md)(포르투갈어)를 참고하세요.

## 크레딧과 라이선스

컴퓨터용 프로그램은 MeisApps의 [**PC Bio Unlock**](https://github.com/MeisApps/pcbu-desktop)을 포크한 것으로, 새로운 기능을 더했습니다. 여러 기기 페어링, Tailscale을 통한 원격 잠금 해제, 휴대폰으로 잠그기, 새 화면 디자인, 그리고 포르투갈어를 기본 언어로 한 것입니다. 휴대폰 앱은 처음부터 새로 작성했으며 같은 프로토콜로 통신합니다.

그 밖에 [Qt](https://www.qt.io/), [OpenSSL](https://www.openssl.org/), [Inter](https://rsms.me/inter/) 글꼴, Google의 [Material 아이콘](https://fonts.google.com/icons)을 사용합니다. 각각의 라이선스는 앱의 **정보** 화면에 있습니다.

[GPL 3.0](LICENSE) 라이선스로 배포됩니다. 코드를 공개로 유지하는 한 자유롭게 사용, 연구, 수정, 재배포할 수 있습니다.
