#!/usr/bin/env bash
# Compila o Ítalock para Linux dentro de um container Ubuntu 22.04 (glibc 2.35, libstdc++ do gcc 12), para o
# instalador rodar também em Ubuntu 22.04+/Mint 21+/Debian 12+ e não só na distro de quem compila.
# Uso (no Linux com docker):
#   bash pkg/linux/build-container.sh
# Variáveis (com padrão):
#   SRC  código do italock-desktop        (padrão: a pasta deste script ../..)
#   QT   Qt 6.10.2 do aqt (com gcc_64)     (padrão: $HOME/Italock/tools/Qt/6.10.2)
#   DIST saída (igual ao build-linux.sh)   (padrão: $HOME/Italock/dist/linux)
#   CACHE downloads do CMake (boost etc.)  (padrão: $HOME/Italock/build-container)
#   JOBS núcleos (padrão 3)
set -euo pipefail
SRC="${SRC:-$(cd "$(dirname "$0")/../.." && pwd)}"
QT="${QT:-$HOME/Italock/tools/Qt/6.10.2}"
DIST="${DIST:-$HOME/Italock/dist/linux}"
CACHE="${CACHE:-$HOME/Italock/build-container}"
JOBS="${JOBS:-3}"
IMG=italock-build:jammy

[ -d "$QT/gcc_64" ] || { echo "Qt não encontrado em $QT/gcc_64"; exit 1; }
command -v docker >/dev/null || { echo "docker não encontrado"; exit 1; }
if ! docker image inspect "$IMG" >/dev/null 2>&1; then
  echo "== criando a imagem $IMG (uma vez só)"
  docker build -t "$IMG" -f "$SRC/pkg/linux/Dockerfile.build-jammy" "$SRC/pkg/linux"
fi
mkdir -p "$CACHE/build" "$CACHE/deps" "$DIST"

echo "== compilando no container (Ubuntu 22.04)"
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$SRC":/src -v "$QT":/qt:ro -v "$CACHE":/cache -v "$DIST":/dist \
  "$IMG" bash -euo pipefail -c '
    B=/cache/build
    cmake -S /src -B $B -G Ninja -DCMAKE_BUILD_TYPE=Release -DTARGET_ARCH=x64 -DQT_BASE_DIR=/qt \
      -DFETCHCONTENT_BASE_DIR=/cache/deps > /cache/configure.log 2>&1 || { tail -40 /cache/configure.log; exit 1; }
    cmake --build $B --target pcbu_auth pam_pcbiounlock italock-agent italock-ssh -j'"$JOBS"' > /cache/natives.log 2>&1 || { grep -E "error|Error" /cache/natives.log | head -40; exit 1; }
    mkdir -p /src/desktop/res/natives/linux/x64
    cp "$(find $B -name pam_pcbiounlock.so | head -1)" /src/desktop/res/natives/linux/x64/
    cp "$(find $B -name pcbu_auth -type f -executable | head -1)" /src/desktop/res/natives/linux/x64/
    cmake --build $B --target pcbu_desktop -j'"$JOBS"' > /cache/desktop.log 2>&1 || { grep -E "error|Error" /cache/desktop.log | head -40; exit 1; }
    rm -rf /dist/app /dist/*.so /dist/pcbu_auth /dist/italock-agent /dist/italock-ssh /dist/*.sh; mkdir -p /dist/app
    cp "$(find $B -name pcbu_desktop -type f -executable | head -1)" /dist/app/italock
    cp "$(find $B -name italock-agent -type f -executable | head -1)" /dist/italock-agent
    cp "$(find $B -name italock-ssh -type f -executable | head -1)" /dist/italock-ssh
    cp /src/desktop/res/icons/icon.png /dist/app/icon.png
    cp /src/desktop/res/natives/linux/x64/pam_pcbiounlock.so /src/desktop/res/natives/linux/x64/pcbu_auth /dist/
    cp /src/pkg/linux/instalar-italock.sh /src/pkg/linux/desinstalar-italock.sh /dist/; chmod +x /dist/*.sh
    # glibc mais nova exigida pelos binários (vai para o instalador conferir)
    for f in /dist/app/italock /dist/italock-agent /dist/italock-ssh /dist/pam_pcbiounlock.so /dist/pcbu_auth; do
      objdump -T "$f" | grep -o "GLIBC_[0-9.]*" ; done | sort -uV | tail -1 | sed "s/GLIBC_//" > /dist/GLIBC_MINIMA
    echo "glibc mínima: $(cat /dist/GLIBC_MINIMA)"
  '
# run-italock.sh de teste (sem instalar), apontando para o Qt local
cat > "$DIST/app/run-italock.sh" <<EOT
#!/bin/sh
Q=$QT/gcc_64
export LD_LIBRARY_PATH=\$Q/lib QT_PLUGIN_PATH=\$Q/plugins QML2_IMPORT_PATH=\$Q/qml QML_IMPORT_PATH=\$Q/qml
exec /opt/italock/italock "\$@"
EOT
chmod +x "$DIST/app/run-italock.sh"
echo LINUX-COMPILACAO-OK; ls -la "$DIST" "$DIST/app"
