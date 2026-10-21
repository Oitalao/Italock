# Como compilar o Ítalock

Os instaladores prontos estão nos [lançamentos](https://github.com/Oitalao/Italock/releases). Este guia é só para quem quer gerar os arquivos a partir do código.

## Aplicativo do celular (Android)

Precisa do JDK 17 e do Android SDK (plataforma 36).

```bash
cd android
./gradlew assembleRelease          # app do celular: app/build/outputs/apk/release
./gradlew :wear:assembleRelease    # app do relógio: wear/build/outputs/apk/release
./gradlew testReleaseUnitTest lint # testes e verificação
```

Sem a chave oficial (`android/italock.keystore`, que não fica no repositório), o APK sai assinado com a chave de depuração do Android. Ele funciona normalmente, mas não instala por cima do APK oficial: desinstale o oficial antes.

## Programa do computador (Windows)

Precisa de:

- Visual Studio 2022 Build Tools, com "Desenvolvimento para desktop com C++"
- [Qt 6.10.2](https://www.qt.io/download-qt-installer) para MSVC 2022 64 bits
- [vcpkg](https://github.com/microsoft/vcpkg) (o CMake baixa o OpenSSL por ele)
- Git Bash
- [Inno Setup 6](https://jrsoftware.org/isinfo.php), para gerar o instalador

```bash
QT=C:/Qt/6.10.2 VCPKG_ROOT=C:/vcpkg bash scripts/build-windows.sh
```

Os arquivos saem em `dist/windows`. Para o instalador:

```bat
ISCC.exe /DOrigem=dist\windows /DSaida=dist /DIcone=desktop\desktop\res\icons\icon.ico desktop\pkg\win\italock.iss
```

## Programa do computador (Linux)

O build roda dentro de um container Ubuntu 22.04, para o instalador funcionar em sistemas com glibc 2.34 ou mais nova. Precisa do Docker e do Qt 6.10.2 para `gcc_64` (por exemplo, instalado com o [aqt](https://github.com/miurahr/aqtinstall)).

```bash
QT=$HOME/Qt/6.10.2 DIST=$PWD/dist/linux bash scripts/build-linux.sh
QT=$HOME/Qt/6.10.2 DIST=$PWD/dist/linux bash desktop/pkg/linux/empacotar.sh dist
```

O resultado é `dist/ItalockLinux.run`. Para compilar sem container, use `NATIVO=1` no primeiro comando.

## Protocolo

A conversa entre celular e computador está descrita em [`protocolo-pcbu.md`](protocolo-pcbu.md). Ela segue o protocolo do PC Bio Unlock, com as extensões do Ítalock documentadas no topo de `desktop/natives/italock-agent/src/main.cpp`.
