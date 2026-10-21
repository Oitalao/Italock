#!/usr/bin/env bash
# Gera um instalador de arquivo único (ItalockLinux.run) com o Qt embutido, a partir de dist/linux.
# Uso (depois do build-container.sh ou build-linux.sh):  bash pkg/linux/empacotar.sh <pasta de saída>
# Variáveis: QT (Qt 6.10.2 do aqt, com gcc_64) e DIST (saída do build).
set -euo pipefail
SAIDA="${1:?pasta de saída}"
QT="${QT:-$HOME/Italock/tools/Qt/6.10.2}/gcc_64"
DIST="${DIST:-$HOME/Italock/dist/linux}"
SRC="$(cd "$(dirname "$0")/../.." && pwd)"
[ -x "$DIST/app/italock" ] || { echo "Build não encontrado em $DIST"; exit 1; }
[ -d "$QT/lib" ] || { echo "Qt não encontrado em $QT"; exit 1; }
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
P=$TMP/italock
mkdir -p "$P/app/lib"
cp "$DIST/app/italock" "$DIST/app/icon.png" "$P/app/"
cp -a "$QT/plugins" "$QT/qml" "$P/app/"
rm -rf "$P/app/plugins/sqldrivers"  # bancos de dados: o Ítalock não usa
# Bibliotecas do Qt que o app, os plugins e os módulos QML realmente usam
: > "$TMP/libs"
while IFS= read -r so; do LD_LIBRARY_PATH="$QT/lib" ldd "$so" 2>/dev/null | awk '{print $3}' | grep "^$QT/lib/" >> "$TMP/libs" || true; done \
  < <(printf '%s\n' "$P/app/italock"; find "$P/app/plugins" "$P/app/qml" -name '*.so')
sort -u "$TMP/libs" | while read -r lib; do cp -L "$lib" "$P/app/lib/"; done
# libicu etc. que vêm junto com o Qt do aqt
for extra in "$QT"/lib/libicu*.so.*; do [ -e "$extra" ] && cp -L "$extra" "$P/app/lib/"; done
# glibc mínima: a mais nova exigida pelos nossos binários e pelas libs do Qt empacotadas
GLIBC_MIN=$(find "$DIST/app/italock" "$DIST/italock-agent" "$DIST/italock-ssh" "$DIST/pam_pcbiounlock.so" "$DIST/pcbu_auth" "$P/app/lib" "$P/app/plugins" -type f | while read -r f; do
  objdump -T "$f" 2>/dev/null | grep -o "GLIBC_[0-9.]*"; done | sort -uV | tail -1 | sed "s/GLIBC_//")
cat > "$P/app/run-italock.sh" <<'EOT'
#!/bin/sh
D=/opt/italock
export LD_LIBRARY_PATH=$D/lib QT_PLUGIN_PATH=$D/plugins QML2_IMPORT_PATH=$D/qml QML_IMPORT_PATH=$D/qml
exec $D/italock "$@"
EOT
chmod +x "$P/app/run-italock.sh"
cp "$DIST/italock-agent" "$DIST/pam_pcbiounlock.so" "$DIST/pcbu_auth" "$P/"
[ -f "$DIST/italock-ssh" ] && cp "$DIST/italock-ssh" "$P/"
cp "$SRC/pkg/linux/instalar-italock.sh" "$SRC/pkg/linux/desinstalar-italock.sh" "$P/"
mkdir -p "$P/icons" && cp -r "$SRC/desktop/res/icons/hicolor" "$P/icons/"
chmod +x "$P"/*.sh
# Dono root no pacote (quem empacota é um usuário comum; sem isto o tar gravaria o uid dele)
tar -C "$TMP" --owner=0 --group=0 --numeric-owner -czf "$TMP/italock.tar.gz" italock
mkdir -p "$SAIDA"
OUT="$SAIDA/ItalockLinux.run"
rm -f "$SAIDA/Italock-Linux-Instalador.run"  # nome antigo
sed "s/@GLIBC_MIN@/$GLIBC_MIN/" > "$OUT" <<'EOT'
#!/bin/sh
# Instalador do Ítalock para Linux. Uso:  sudo sh ItalockLinux.run
#   sudo sh ItalockLinux.run --desinstalar [--manter-pareamentos]   (remove)
set -e
[ "$(id -u)" = 0 ] || { echo "Rode com sudo: sudo sh $0"; exit 1; }
if [ "${1:-}" != "--desinstalar" ]; then
  # Confere o que o Ítalock precisa ANTES de mexer em qualquer coisa
  FALTA=""
  ARQ=$(uname -m)
  if [ "$ARQ" != x86_64 ] && [ "$ARQ" != amd64 ]; then
    echo "Este instalador é para computadores Intel ou AMD de 64 bits (x86_64); este é $ARQ. Nada foi instalado."
    exit 1
  fi
  GLIBC_MIN=@GLIBC_MIN@
  GLIBC=$(ldd --version 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+$' || true)
  if [ -z "$GLIBC" ] || [ "$(printf '%s\n%s\n' "$GLIBC_MIN" "$GLIBC" | sort -V | head -1)" != "$GLIBC_MIN" ]; then
    echo "Este Linux tem glibc ${GLIBC:-desconhecida}; o Ítalock precisa de $GLIBC_MIN ou mais nova"
    echo "(Ubuntu 22.04, Linux Mint 21, Debian 12, Fedora 36 ou mais novos). Nada foi instalado."
    exit 1
  fi
  [ -d /run/systemd/system ] && command -v systemctl >/dev/null 2>&1 || FALTA="$FALTA\n  • systemd (o Ítalock precisa dele para o serviço de travar a tela)"
  command -v loginctl >/dev/null 2>&1 || FALTA="$FALTA\n  • loginctl (pacote systemd)"
  command -v setpriv >/dev/null 2>&1 || FALTA="$FALTA\n  • setpriv (apt: util-linux | dnf: util-linux-core)"
  command -v gdbus >/dev/null 2>&1 || FALTA="$FALTA\n  • gdbus (apt: libglib2.0-bin | dnf: glib2)"
  LIBS=$(/sbin/ldconfig -p 2>/dev/null || ldconfig -p 2>/dev/null || true)
  tem() { echo "$LIBS" | grep -qE "^[[:space:]]*$1 "; }
  STDCXX=$(echo "$LIBS" | grep -m1 -F "libstdc++.so.6 (" | sed 's/.*=> //')
  [ -n "$STDCXX" ] && grep -q GLIBCXX_3.4.30 "$STDCXX" 2>/dev/null || FALTA="$FALTA\n  • libstdc++ do GCC 12 ou mais novo (apt: libstdc++6 | dnf: libstdc++)"
  tem libcrypto.so.3 && tem libssl.so.3 || FALTA="$FALTA\n  • OpenSSL 3 (apt: libssl3 | dnf: openssl-libs)"
  tem libbluetooth.so.3 || FALTA="$FALTA\n  • libbluetooth.so.3 (apt: libbluetooth3 | dnf: bluez-libs)"
  tem libcrypt.so.1 || FALTA="$FALTA\n  • libcrypt.so.1 (apt: libcrypt1 | dnf: libxcrypt-compat)"
  tem libpam.so.0 || FALTA="$FALTA\n  • libpam.so.0 (apt: libpam0g | dnf: pam)"
  tem libxcb-cursor.so.0 || FALTA="$FALTA\n  • libxcb-cursor.so.0, para abrir a janela (apt: libxcb-cursor0 | dnf: xcb-util-cursor)"
  tem libxkbcommon-x11.so.0 || FALTA="$FALTA\n  • libxkbcommon-x11.so.0, para abrir a janela (apt: libxkbcommon-x11-0 | dnf: libxkbcommon-x11)"
  tem libEGL.so.1 && tem libGL.so.1 && tem libOpenGL.so.0 || FALTA="$FALTA\n  • OpenGL/EGL, para abrir a janela (apt: libegl1 libgl1 libopengl0 | dnf: mesa-libEGL mesa-libGL libglvnd-opengl)"
  tem libfontconfig.so.1 || FALTA="$FALTA\n  • fontconfig (apt: libfontconfig1 | dnf: fontconfig)"
  if [ -n "$FALTA" ]; then
    printf "Falta instalar antes:%b\n\nNada foi instalado. Instale o que falta e rode de novo.\n" "$FALTA"
    exit 1
  fi
  # Opcionais: sem eles o Ítalock funciona, só perde um recurso
  OPC=""
  command -v xdotool >/dev/null 2>&1 || OPC="$OPC\n  • xdotool: o botão Destravar do celular abre a caixa de senha sozinho (apt: xdotool | dnf: xdotool)"
  command -v l2ping >/dev/null 2>&1 || OPC="$OPC\n  • l2ping: recurso experimental de travar quando o celular se afasta (apt: bluez | dnf: bluez)"
  [ -z "$OPC" ] || printf "Opcional, pode instalar depois:%b\n\n" "$OPC"
fi
umask 022
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
LINHA=$(awk '/^__PACOTE__$/{print NR+1; exit}' "$0")
# --no-same-owner: como root o tar manteria o dono gravado no pacote (em versões antigas, o uid de quem empacotou)
tail -n +"$LINHA" "$0" | tar -xz --no-same-owner --no-same-permissions -C "$T"
if [ "${1:-}" = "--desinstalar" ]; then bash "$T/italock/desinstalar-italock.sh" "${2:-}"; else bash "$T/italock/instalar-italock.sh"; fi
exit 0
__PACOTE__
EOT
cat "$TMP/italock.tar.gz" >> "$OUT"
chmod +x "$OUT"
echo "glibc mínima: $GLIBC_MIN"
ls -la "$OUT"
