#!/usr/bin/env bash
# Compila o Ítalock para Linux. Por padrão dentro de um container Ubuntu 22.04 (glibc 2.35), para o instalador
# servir em outras máquinas; NATIVO=1 compila direto no sistema (os binários passam a exigir a glibc daqui).
# Depois: bash $SRC/pkg/linux/empacotar.sh <pasta de saída>
set -e
QT=${QT:-$HOME/Italock/tools/Qt/6.10.2}
SRC=${SRC:-$(cd "$(dirname "$0")/../desktop" && pwd)}
DIST=${DIST:-$HOME/Italock/dist/linux}
if [ "${NATIVO:-0}" != 1 ] && command -v docker >/dev/null 2>&1; then
  QT="$QT" SRC="$SRC" DIST="$DIST" exec bash "$SRC/pkg/linux/build-container.sh"
fi
cd "$SRC"
echo "== nativos (PAM, auxiliar, agente)"
cmake -S . -B build-linux -G Ninja -DCMAKE_BUILD_TYPE=Release -DTARGET_ARCH=x64 -DQT_BASE_DIR="$QT" > build-linux-configure.log 2>&1 || { tail -30 build-linux-configure.log; exit 1; }
cmake --build build-linux --target pcbu_auth pam_pcbiounlock italock-agent italock-ssh -j3 > build-linux-natives.log 2>&1 || { grep -E "error" build-linux-natives.log | head -30; exit 1; }
mkdir -p desktop/res/natives/linux/x64
cp "$(find build-linux -name pam_pcbiounlock.so | head -1)" desktop/res/natives/linux/x64/
cp "$(find build-linux -name pcbu_auth -type f -executable | head -1)" desktop/res/natives/linux/x64/
echo "== aplicativo"
cmake --build build-linux --target pcbu_desktop -j3 > build-linux-desktop.log 2>&1 || { grep -E "error" build-linux-desktop.log | head -30; exit 1; }
echo "== pacote"
rm -rf "$DIST"; mkdir -p "$DIST/app"
cp "$(find build-linux -name pcbu_desktop -type f -executable | head -1)" "$DIST/app/italock"
cp "$(find build-linux -name italock-agent -type f -executable | head -1)" "$DIST/italock-agent"
cp "$(find build-linux -name italock-ssh -type f -executable | head -1)" "$DIST/italock-ssh"
cp desktop/res/icons/icon.png "$DIST/app/icon.png"
cat > "$DIST/app/run-italock.sh" <<EOT
#!/bin/sh
Q=$QT/gcc_64
export LD_LIBRARY_PATH=\$Q/lib QT_PLUGIN_PATH=\$Q/plugins QML2_IMPORT_PATH=\$Q/qml QML_IMPORT_PATH=\$Q/qml
exec /opt/italock/italock "\$@"
EOT
chmod +x "$DIST/app/run-italock.sh"
cp desktop/res/natives/linux/x64/pam_pcbiounlock.so desktop/res/natives/linux/x64/pcbu_auth "$DIST/"
cp pkg/linux/instalar-italock.sh pkg/linux/desinstalar-italock.sh "$DIST/"; chmod +x "$DIST"/*.sh
for f in "$DIST/app/italock" "$DIST/italock-agent" "$DIST/pam_pcbiounlock.so" "$DIST/pcbu_auth"; do
  objdump -T "$f" | grep -o "GLIBC_[0-9.]*"; done | sort -uV | tail -1 | sed "s/GLIBC_//" > "$DIST/GLIBC_MINIMA"
echo "glibc mínima: $(cat "$DIST/GLIBC_MINIMA")"
echo LINUX-COMPILACAO-OK; ls -la "$DIST" "$DIST/app"
