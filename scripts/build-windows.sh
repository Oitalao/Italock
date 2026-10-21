#!/usr/bin/env bash
# Compila o Ítalock para Windows (Git Bash + Visual Studio 2022 Build Tools).
# Variáveis: QT (Qt 6.10.2, com msvc2022_64), VCPKG_ROOT (vcpkg com openssl), CMAKE (opcional), OUT (opcional).
# Depois, o instalador: ISCC.exe /DOrigem=<OUT> /DSaida=<pasta> /DIcone=<repo>\desktop\desktop\res\icons\icon.ico desktop\pkg\win\italock.iss
set -e
export MSYS_NO_PATHCONV=1
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
: "${QT:?defina QT, por exemplo QT=C:/Qt/6.10.2}"
: "${VCPKG_ROOT:?defina VCPKG_ROOT, a pasta do vcpkg}"
export VCPKG_ROOT
CMAKE="${CMAKE:-cmake}"
SRC="$RAIZ/desktop"
cd "$SRC"
mkdir -p build-native build-desktop
echo "== modulo da tela de bloqueio"
"$CMAKE" -S . -B build-native -G "Visual Studio 17 2022" -A x64 -DCMAKE_BUILD_TYPE=Release -DTARGET_ARCH=x64 -DQT_BASE_DIR="$QT" -DMSVC_STATIC_LINK=1 > build-native/configure.log 2>&1 || { tail -30 build-native/configure.log; exit 1; }
"$CMAKE" --build build-native --target win-pcbiounlock italock-agent --config Release -- /maxcpucount:3 /v:minimal > build-native/build.log 2>&1 || { grep -E "error|erro" build-native/build.log | head -30; exit 1; }
DLL=$(find build-native -name "win-pcbiounlock.dll" -path "*Release*" | head -1); echo "dll: $DLL"
AGENT=$(find build-native -name "italock-agent.exe" -path "*Release*" | head -1); echo "agente: $AGENT"
mkdir -p desktop/res/natives/win/x64 && cp "$DLL" desktop/res/natives/win/x64/win-pcbiounlock.dll
echo "== aplicativo"
"$CMAKE" -S . -B build-desktop -G "Visual Studio 17 2022" -A x64 -DCMAKE_BUILD_TYPE=Release -DTARGET_ARCH=x64 -DQT_BASE_DIR="$QT" > build-desktop/configure.log 2>&1 || { tail -30 build-desktop/configure.log; exit 1; }
"$CMAKE" --build build-desktop --target pcbu_desktop --config Release -- /maxcpucount:3 /v:minimal > build-desktop/build.log 2>&1 || { grep -E "error|erro" build-desktop/build.log | head -30; exit 1; }
EXE=$(find build-desktop -name "pcbu_desktop.exe" -path "*Release*" | head -1); echo "exe: $EXE"
echo "== pacote"
OUT="${OUT:-$RAIZ/dist/windows}"; rm -rf "$OUT"; mkdir -p "$OUT"
cp "$EXE" "$OUT/Italock.exe"
cp "$AGENT" "$OUT/italock-agent.exe"
cp "$DLL" "$OUT/italock-cp.dll"
cp pkg/win/Instalar-Italock.ps1 pkg/win/Desinstalar-Italock.ps1 "$OUT/"
cp LICENSE "$OUT/LICENCA-GPL-3.0.txt"
# Sem manifesto "requireAdministrator": a bandeja roda sem administrador e a janela pede elevacao sozinha (--janela).
"$QT/msvc2022_64/bin/windeployqt.exe" --qmldir desktop/qml "$(cygpath -w "$OUT/Italock.exe")" > build-desktop/deploy.log 2>&1 || tail -5 build-desktop/deploy.log
echo "COMPILACAO-OK"; ls "$OUT" | head
