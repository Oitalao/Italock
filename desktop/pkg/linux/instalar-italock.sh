#!/usr/bin/env bash
# Instala o Ítalock no Linux. Rodar com sudo, de dentro da pasta extraída (o .run faz isso sozinho).
#  • app em /opt/italock (Qt empacotado junto)
#  • agente de travar/destravar: serviço systemd italock-agent (porta 43301)
#  • scripts travar-tela / acordar-tela / estado-tela: acham a sessão gráfica NA HORA (usuário, DISPLAY, D-Bus);
#    o agente passa o usuário do pareamento em ITALOCK_USER; rodam como o usuário via setpriv (não abre sessão PAM =
#    não enche o journal)
#  • PAM: SÓ a proteção de tela suportada (Cinnamon, MATE, XFCE). Login, sudo, SSH e polkit ficam intocados.
#    Celular falhou/ausente = senha normal.
#  • firewall: portas do Ítalock só das redes locais reais (sem docker), da ancoragem do celular e do Tailscale
# SEM_PAM=1 pula a parte do PAM.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Rode com sudo."; exit 1; }
umask 022
ORIGEM="$(cd "$(dirname "$0")" && pwd)"
LIB=/usr/local/lib/italock

# ---- Restos da versão com "continuidade" (espelhar, enviar arquivos, webcam), que saiu ----
# scrcpy/adb que vieram com o Ítalock (os de outros programas ficam), ação "Enviar ao celular" do Nemo e do Nautilus,
# script de abrir janelas como o usuário, canal local e a configuração da câmera virtual do Ítalock. O módulo
# v4l2loopback em si NÃO é descarregado nem desinstalado (pode ser de outro programa).
pkill -f '^/opt/italock/scrcpy/' 2>/dev/null || true
rm -rf /opt/italock/scrcpy
rm -f /usr/share/nemo/actions/italock-enviar.nemo_action /home/*/.local/share/nautilus/scripts/"Enviar ao celular (Ítalock)"
rm -f /etc/modprobe.d/italock-camera.conf /etc/modules-load.d/italock-camera.conf
rm -f "$LIB/rodar-como-usuario"
rm -rf /run/italock

# ---- Pasta de módulos PAM (onde o pam_unix.so mora nesta distro) ----
PAMDIR=""
if command -v dpkg-architecture >/dev/null 2>&1; then
  d="/lib/$(dpkg-architecture -qDEB_HOST_MULTIARCH 2>/dev/null)/security"
  [ -f "$d/pam_unix.so" ] && PAMDIR="$d"
fi
if [ -z "$PAMDIR" ]; then
  for d in /lib/*/security /usr/lib/*/security /lib64/security /usr/lib64/security /usr/lib/security /lib/security; do
    [ -f "$d/pam_unix.so" ] && { PAMDIR="$d"; break; }
  done
fi
[ -n "$PAMDIR" ] || { echo "Não achei a pasta de módulos PAM (pam_unix.so). Nada foi instalado."; exit 1; }

# ---- Usuário da sessão gráfica (para a mensagem final e como reserva dos scripts) ----
USUARIO=""
for s in $(loginctl list-sessions --no-legend 2>/dev/null | awk '{print $1}'); do
  [ "$(loginctl show-session "$s" -p Class --value 2>/dev/null || true)" = user ] || continue
  t=$(loginctl show-session "$s" -p Type --value 2>/dev/null || true)
  a=$(loginctl show-session "$s" -p Active --value 2>/dev/null || true)
  if [ "$a" = yes ] && { [ "$t" = x11 ] || [ "$t" = wayland ]; }; then USUARIO=$(loginctl show-session "$s" -p Name --value); break; fi
done
USUARIO="${USUARIO:-${SUDO_USER:-}}"

# ---- Arquivos ----
install -d "$LIB"
# App: copia para uma pasta nova ao lado e troca de uma vez (cp por cima do executável aberto dá "Text file busy";
# o app aberto continua com os arquivos antigos até fechar).
rm -rf /opt/.italock-novo.* /opt/.italock-velho.* 2>/dev/null || true
NOVO=$(mktemp -d /opt/.italock-novo.XXXXXX)
cp -a "$ORIGEM/app/." "$NOVO/"
# O app roda como root (pkexec): tudo dele tem de ser do root e sem escrita para grupo/outros. Senão quem for dono
# (ou do grupo) troca o executável ou uma biblioteca e ganha root na próxima vez que alguém abrir o Ítalock.
chown -R root:root "$NOVO"
chmod -R go-w "$NOVO"
chmod 755 "$NOVO"
if [ -d /opt/italock ]; then
  VELHO=/opt/.italock-velho.$$
  mv /opt/italock "$VELHO" && mv "$NOVO" /opt/italock && rm -rf "$VELHO"
else
  mv "$NOVO" /opt/italock
fi

# Binários: grava num arquivo novo na mesma pasta e troca com mv (nunca trunca o que está em uso, como o módulo PAM
# carregado pela proteção de tela)
troca() { # troca <modo> <origem> <destino>
  local tmp; tmp=$(mktemp "$(dirname "$3")/.italock-XXXXXX")
  cp "$2" "$tmp" && chown root:root "$tmp" && chmod "$1" "$tmp" && mv -f "$tmp" "$3"
}
troca 755 "$ORIGEM/italock-agent" "$LIB/italock-agent"
troca 644 "$ORIGEM/pam_pcbiounlock.so" "$PAMDIR/pam_pcbiounlock.so"
# O app confere /lib/security (versões antigas do PAM de outras distros procuram lá também)
if [ "$PAMDIR" != /lib/security ]; then install -d /lib/security && troca 644 "$ORIGEM/pam_pcbiounlock.so" /lib/security/pam_pcbiounlock.so; fi
troca 4755 "$ORIGEM/pcbu_auth" /usr/local/sbin/pcbu_auth
# SSH (experimental; só age se ligado no app): entrega ao ssh a senha guardada de um servidor depois que o celular aprova
[ -f "$ORIGEM/italock-ssh" ] && troca 4755 "$ORIGEM/italock-ssh" /usr/local/sbin/italock-ssh
# Usuário detectado agora: reserva para os scripts quando o agente não disser qual é
printf '%s\n' "$USUARIO" > "$LIB/usuario"; chmod 644 "$LIB/usuario"

# Sessão gráfica: exporta SESSAO, UID_S, GID_S, USUARIO_S, DISPLAY_S, TIPO_S, BUS_S e XAUTH_S.
cat > "$LIB/sessao" <<'EOT'
#!/bin/sh
# Usado pelos outros scripts (". /usr/local/lib/italock/sessao"). Sai 3 se não houver sessão gráfica de usuário.
# Escolhe a sessão pelo logind, só da classe "user" (nunca a tela de login/greeter), do usuário do pareamento:
#   ITALOCK_USER (o agente passa o usuário do celular que pediu) > usuários com celular pareado > usuário da instalação.
# Entre as sessões desse usuário, a ativa.
valido() { echo "$1" | grep -qE '^[a-z_][a-z0-9_.-]*$'; }
QUEM=""
if [ -n "${ITALOCK_USER:-}" ] && valido "$ITALOCK_USER"; then
  QUEM=$ITALOCK_USER
else
  QUEM=$(grep -o '"userName":"[^"]*"' /etc/italock/paired_devices.json 2>/dev/null | sed 's/.*:"//; s/"$//' | sort -u)
  [ -n "$QUEM" ] || QUEM=$(cat /usr/local/lib/italock/usuario 2>/dev/null)
fi
SESSAO=""; TIPO_S=""; RESERVA=""; RESERVA_T=""
for s in $(loginctl list-sessions --no-legend 2>/dev/null | awk '{print $1}'); do
  [ "$(loginctl show-session "$s" -p Class --value 2>/dev/null)" = user ] || continue
  t=$(loginctl show-session "$s" -p Type --value 2>/dev/null)
  [ "$t" = x11 ] || [ "$t" = wayland ] || continue
  n=$(loginctl show-session "$s" -p Name --value 2>/dev/null)
  if [ -n "$QUEM" ] && ! echo "$QUEM" | grep -qxF "$n"; then continue; fi
  if [ "$(loginctl show-session "$s" -p Active --value 2>/dev/null)" = yes ]; then SESSAO=$s; TIPO_S=$t; break; fi
  [ -n "$RESERVA" ] || { RESERVA=$s; RESERVA_T=$t; }
done
[ -n "$SESSAO" ] || { SESSAO=$RESERVA; TIPO_S=$RESERVA_T; }
[ -n "$SESSAO" ] || exit 3
UID_S=$(loginctl show-session "$SESSAO" -p User --value)
USUARIO_S=$(loginctl show-session "$SESSAO" -p Name --value)
DISPLAY_S=$(loginctl show-session "$SESSAO" -p Display --value)
GID_S=$(id -g "$USUARIO_S")
BUS_S="unix:path=/run/user/$UID_S/bus"
# XAUTHORITY: o do ambiente de um processo da sessão; senão ~/.Xauthority
XAUTH_S=""
LEADER=$(loginctl show-session "$SESSAO" -p Leader --value 2>/dev/null)
for p in $(pgrep -u "$UID_S" -f 'cinnamon-session|mate-session|xfce4-session|gnome-session|plasma|ksmserver|x-session-manager' 2>/dev/null) $LEADER; do
  x=$(tr '\0' '\n' < "/proc/$p/environ" 2>/dev/null | sed -n 's/^XAUTHORITY=//p')
  [ -n "$x" ] && { XAUTH_S=$x; break; }
done
[ -n "$XAUTH_S" ] || XAUTH_S="$(getent passwd "$USUARIO_S" | cut -d: -f6)/.Xauthority"
# Roda um comando como o usuário da sessão, sem abrir sessão PAM (runuser/su/sudo abririam uma a cada consulta)
como_usuario() {
  setpriv --reuid="$UID_S" --regid="$GID_S" --init-groups \
    env HOME="$(getent passwd "$USUARIO_S" | cut -d: -f6)" DISPLAY="$DISPLAY_S" XAUTHORITY="$XAUTH_S" DBUS_SESSION_BUS_ADDRESS="$BUS_S" "$@"
}
# Qual proteção de tela está no ar (nome do serviço D-Bus); vazio = nenhuma conhecida
protecao_de_tela() {
  for n in org.cinnamon.ScreenSaver org.mate.ScreenSaver org.xfce.ScreenSaver org.gnome.ScreenSaver org.freedesktop.ScreenSaver; do
    como_usuario gdbus call --timeout 3 --session --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus \
      --method org.freedesktop.DBus.NameHasOwner "$n" 2>/dev/null | grep -q true && { echo "$n"; return; }
  done
}
EOT
chmod 644 "$LIB/sessao"

cat > "$LIB/travar-tela" <<'EOT'
#!/bin/sh
# Trava a tela da sessão gráfica do usuário do pareamento.
. /usr/local/lib/italock/sessao || exit 1
if command -v cinnamon-screensaver-command >/dev/null 2>&1 && [ "$(protecao_de_tela)" = org.cinnamon.ScreenSaver ]; then
  exec setpriv --reuid="$UID_S" --regid="$GID_S" --init-groups env DISPLAY="$DISPLAY_S" DBUS_SESSION_BUS_ADDRESS="$BUS_S" cinnamon-screensaver-command --lock
fi
# Genérico (GNOME, KDE, MATE, XFCE com light-locker/xfce4-screensaver): o logind pede para a sessão travar
exec loginctl lock-session "$SESSAO"
EOT

cat > "$LIB/acordar-tela" <<'EOT'
#!/bin/sh
# Abre a caixa de senha da proteção de tela (ela então pede o desbloqueio ao celular). Só X11: no Wayland não há como
# simular tecla de fora, então sai 1 (o celular mostra que a tela não está aguardando).
. /usr/local/lib/italock/sessao || exit 1
[ "$TIPO_S" = x11 ] || exit 1
command -v xdotool >/dev/null 2>&1 || exit 1
# Só com a tela travada: destravada (ou sem saber) não há caixa de senha esperando, e fingir sucesso enganaria o celular
/usr/local/lib/italock/estado-tela || exit 1
como_usuario xdotool mousemove_relative 1 1 key shift
EOT

cat > "$LIB/estado-tela" <<'EOT'
#!/bin/sh
# Sai 0 se a tela está travada, 1 se não, 2 se não deu para saber. Não usa runuser (sem sessão PAM no journal).
. /usr/local/lib/italock/sessao || exit 2
n=$(protecao_de_tela)
if [ -n "$n" ]; then
  p=/$(echo "$n" | tr . /)
  r=$(como_usuario gdbus call --timeout 3 --session --dest "$n" --object-path "$p" --method "$n.GetActive" 2>/dev/null) || r=""
  case "$r" in *true*) exit 0 ;; *false*) exit 1 ;; esac
fi
# Sem proteção de tela conhecida no D-Bus: o que o logind sabe (GNOME e KDE atualizam o LockedHint)
case "$(loginctl show-session "$SESSAO" -p LockedHint --value 2>/dev/null)" in yes) exit 0 ;; no) exit 1 ;; esac
exit 2
EOT
chmod 755 "$LIB/travar-tela" "$LIB/acordar-tela" "$LIB/estado-tela"

cat > /etc/systemd/system/italock-agent.service <<EOT
[Unit]
Description=Ítalock: travar a tela a pedido do celular pareado
After=network-online.target

[Service]
ExecStart=$LIB/italock-agent
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOT
systemctl daemon-reload
systemctl enable italock-agent >/dev/null 2>&1
systemctl restart italock-agent  # atualização: sobe o binário novo

# Ícone no tema hicolor + atalho no menu
if [ -d "$ORIGEM/icons/hicolor" ]; then
  cp -r "$ORIGEM/icons/hicolor/." /usr/share/icons/hicolor/
  gtk-update-icon-cache -q -t /usr/share/icons/hicolor 2>/dev/null || true
fi
# Exec passa pelo sh: o lançador do menu não expande variáveis de ambiente sozinho
cat > /usr/share/applications/italock.desktop <<'EOT'
[Desktop Entry]
Type=Application
Name=Ítalock
Exec=sh -c 'pkexec env DISPLAY="$DISPLAY" XAUTHORITY="$XAUTHORITY" WAYLAND_DISPLAY="$WAYLAND_DISPLAY" XDG_RUNTIME_DIR="$XDG_RUNTIME_DIR" /opt/italock/run-italock.sh'
Icon=italock
Categories=System;
EOT

# ---- PAM: só a proteção de tela ----
# Formato (igual ao do app): o celular aprovado pula a verificação de senha mas NÃO encerra a pilha, para o chaveiro
# (pam_gnome_keyring, quando existe) receber a senha. Celular falhou/ausente/módulo sumido = segue para a senha normal.
#   auth [success=1 default=ignore] pam_pcbiounlock.so
#   auth substack common-auth        (era "@include common-auth"; em Fedora/Arch "auth include system-auth")
# Idempotente: arquivo que já tem o formato atual não é tocado. O formato antigo ("auth sufficient pam_pcbiounlock.so",
# em que o cofre nunca recebia a senha) é migrado. Cópia de antes em /root/<serviço>.antes-italock.
pam_ligar() {
  local f=/etc/pam.d/$1 antigo=0
  [ -f "$f" ] || return 1
  if grep -qE '^auth[[:space:]]+sufficient[[:space:]]+pam_pcbiounlock\.so' "$f"; then
    antigo=1
  elif grep -q pam_pcbiounlock.so "$f"; then
    echo "  $f já está com o Ítalock (mantido como está)."; return 0
  fi
  if ! grep -qE '^@include common-auth$|^auth[[:space:]]+include[[:space:]]+[a-z-]+-auth$' "$f"; then
    echo "  $f tem um formato que não conheço; não mexi. (Destravar pelo celular não vai funcionar nessa tela.)"
    return 1
  fi
  [ -e "/root/$1.antes-italock" ] || cp -a "$f" "/root/$1.antes-italock"
  local tmp; tmp=$(mktemp "/etc/pam.d/.italock-XXXXXX")
  {
    echo "# Ítalock: se o celular aprovar, pula a senha e segue para o cofre de senhas (gnome-keyring)"
    echo "auth [success=1 default=ignore] pam_pcbiounlock.so"
    sed -E '/^auth[[:space:]]+sufficient[[:space:]]+pam_pcbiounlock\.so/d; /^# Ítalock:/d; s/^@include common-auth$/auth substack common-auth/; s/^auth([[:space:]]+)include([[:space:]]+)([a-z-]+-auth)$/auth\1substack\2\3/' "$f"
  } > "$tmp"
  # Confere antes de trocar: exatamente uma linha do Ítalock e a pilha de senha ainda lá. Troca de uma vez (mv).
  if [ "$(grep -c pam_pcbiounlock.so "$tmp")" = 1 ] && grep -qE '^auth[[:space:]]+substack[[:space:]]+[a-z-]+-auth$' "$tmp"; then
    chmod --reference="$f" "$tmp"; chown --reference="$f" "$tmp"; mv -f "$tmp" "$f"
    if [ "$antigo" = 1 ]; then echo "  $f: atualizado para o formato novo."; else echo "  $f: celular ligado (cópia em /root/$1.antes-italock)."; fi
  else
    rm -f "$tmp"
    echo "  $f: resultado inesperado; não mexi."
  fi
}

PROTECAO=""
if [ "${SEM_PAM:-0}" != 1 ]; then
  [ -f "$PAMDIR/pam_pcbiounlock.so" ] || { echo "Módulo PAM ausente em $PAMDIR; não vou mexer no PAM."; exit 1; }
  echo "Proteção de tela:"
  for par in cinnamon-screensaver:cinnamon-screensaver:Cinnamon mate-screensaver:mate-screensaver:MATE xfce4-screensaver:xfce4-screensaver:XFCE; do
    prog=${par%%:*}; resto=${par#*:}; svc=${resto%%:*}; nome=${resto#*:}
    if command -v "$prog" >/dev/null 2>&1; then
      pam_ligar "$svc" && PROTECAO="$PROTECAO $nome"
    fi
  done
  if [ -z "$PROTECAO" ]; then
    outro="desconhecida"
    for p in gnome-shell:do_GNOME plasmashell:do_KDE light-locker:light_locker xscreensaver:xscreensaver i3lock:i3lock swaylock:swaylock hyprlock:hyprlock; do
      command -v "${p%%:*}" >/dev/null 2>&1 && { outro=$(echo "${p#*:}" | tr _ ' '); break; }
    done
    echo "  Proteção de tela $outro não suportada: o Ítalock vai só travar a tela pelo celular, sem destravar."
    echo "  (Suportadas: proteções de tela do Cinnamon, do MATE e do XFCE.)"
  fi
fi

# ---- Donos e permissões de tudo o que foi instalado ----
# Scripts e binários que rodam como root: do root, sem escrita para grupo/outros. Registros do root: só o root lê
# (IPs, nomes dos celulares, horários); o module.log é do grupo do usuário da proteção de tela e fica como está.
chown -R root:root "$LIB"
chmod -R go-w "$LIB"
for f in /usr/share/applications/italock.desktop /etc/systemd/system/italock-agent.service; do
  [ -e "$f" ] && chown root:root "$f" && chmod 644 "$f"
done
if [ -d /etc/italock ]; then
  chown root:root /etc/italock
  chmod 755 /etc/italock
  for f in /etc/italock/*.log /etc/italock/*.[0-9].log; do
    [ -f "$f" ] || continue
    [ "$(stat -c %U:%G "$f")" = root:root ] && chmod 600 "$f"
  done
fi

# ---- Firewall ----
# Quem alcança as portas do Ítalock:
#  • as sub-redes privadas em que este computador está AGORA (rotas do kernel), menos as de containers e VMs
#    (docker0, br-*, veth*, virbr*, lxc*, podman*...): um container comprometido não fala com o Ítalock;
#  • as faixas fixas de ancoragem do celular: 192.168.42.0/24 (cabo USB no Android), 192.168.43.0/24 (roteador Wi-Fi
#    do Android) e 172.20.10.0/28 (roteador do iPhone);
#  • o Tailscale só pela tailscale0 (100.64.0.0/10).
# Não liberamos 10.0.0.0/8, 172.16.0.0/12 nem 192.168.0.0/16 inteiros: o docker usa essas faixas (no servidor do Ítalo
# há pontes em 172.17 a 172.31 e em 192.168.144.0/20). Preço: se o computador for para outra rede (ou o roteador do
# celular sortear outra faixa), o celular só alcança pela nova rede depois de rodar o instalador de novo; o Tailscale
# continua valendo em qualquer rede.
# 43295 = pareamento, 43296 = destravar, 43301 = agente (travar/estado). A 43297/udp é a porta em que o CELULAR escuta
# (o PC só envia), não precisa de entrada.
PORTAS="43295 43296 43301"
FAIXAS=$( {
  ip -4 route show proto kernel scope link 2>/dev/null | awk '{d=""; for(i=1;i<NF;i++) if($i=="dev") d=$(i+1);
    if (d !~ /^(lo|docker|br-|veth|virbr|vnet|lxc|lxd|podman|cni|flannel|cali|kube|vmnet|vboxnet|tailscale|zt|wg)/) print $1}' |
    grep -E '^(10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.)' || true
  echo 192.168.42.0/24; echo 192.168.43.0/24; echo 172.20.10.0/28
} | sort -u | tr '\n' ' ')
# Faixas inteiras das versões antigas (para apagar as regras delas no firewalld; no ufw sai tudo pelo comentário)
FAIXAS_ANTIGAS="192.168.0.0/16 10.0.0.0/8 172.16.0.0/12"
if command -v ufw >/dev/null 2>&1; then
  # Tira as regras antigas do Ítalock (inclusive as "Anywhere" e as de faixa inteira) e põe as novas: sem duplicar
  ufw status numbered 2>/dev/null | grep -E '# Italock$' | sed -E 's/^\[ *([0-9]+)\].*/\1/' | sort -rn | while read -r n; do
    ufw --force delete "$n" >/dev/null
  done || true
  for p in $PORTAS; do
    for net in $FAIXAS; do ufw allow from "$net" to any port "$p" proto tcp comment Italock >/dev/null; done
    ufw allow in on tailscale0 from 100.64.0.0/10 to any port "$p" proto tcp comment Italock >/dev/null
  done
fi
if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  # Só a zona padrão e só com origem (rich rules): nunca porta aberta para qualquer origem
  ZONA=$(firewall-cmd --get-default-zone 2>/dev/null || echo public)
  for p in $PORTAS; do
    firewall-cmd -q --permanent --zone="$ZONA" --remove-port="$p/tcp" 2>/dev/null || true
    for net in $FAIXAS_ANTIGAS; do
      firewall-cmd -q --permanent --zone="$ZONA" --remove-rich-rule="rule family=ipv4 source address=$net port port=$p protocol=tcp accept" 2>/dev/null || true
    done
    for net in $FAIXAS 100.64.0.0/10; do
      firewall-cmd -q --permanent --zone="$ZONA" --add-rich-rule="rule family=ipv4 source address=$net port port=$p protocol=tcp accept" 2>/dev/null || true
    done
  done
  firewall-cmd -q --reload || true
fi
printf '%s\n' $FAIXAS > "$LIB/faixas-firewall"  # o desinstalador apaga exatamente estas no firewalld

echo
echo "Ítalock instalado.${USUARIO:+ Usuário da sessão: $USUARIO.}"
[ -n "$PROTECAO" ] && echo "Destravar pelo celular ligado na proteção de tela:$PROTECAO."
command -v xdotool >/dev/null 2>&1 || echo "Dica: instale o xdotool para o botão Destravar do celular abrir a caixa de senha sozinho."
echo "Abra o Ítalock no menu e toque em Parear celular."
