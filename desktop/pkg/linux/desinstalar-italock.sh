#!/usr/bin/env bash
# Remove o Ítalock. Uso: sudo bash desinstalar-italock.sh [--manter-pareamentos]
set -uo pipefail
[ "$(id -u)" = 0 ] || { echo "Rode com sudo."; exit 1; }

# PAM: tira as linhas do Ítalock de todo serviço que as tenha e desfaz o "substack" (inverso exato do instalador)
for f in $(grep -l pam_pcbiounlock.so /etc/pam.d/* 2>/dev/null); do
  case "$f" in *.antes-*|*.bak|*~) continue ;; esac
  svc=$(basename "$f")
  tmp=$(mktemp)
  if head -3 "$f" | grep -q '^# Ítalock:'; then
    # Formato do Ítalock (proteção de tela): o substack volta a ser include
    sed -E '/pam_pcbiounlock.so/d; /^# Ítalock:/d; s/^auth substack common-auth$/@include common-auth/; s/^auth([[:space:]]+)substack([[:space:]]+)([a-z-]+-auth)$/auth\1include\2\3/' "$f" > "$tmp"
  else
    # Linha avulsa (sudo/login experimentais, versões antigas): só tira a linha
    sed '/pam_pcbiounlock.so/d' "$f" > "$tmp"
  fi
  if grep -qE 'common-auth|system-auth|pam_unix' "$tmp" || [ ! -s "$f" ]; then
    cat "$tmp" > "$f"; echo "PAM restaurado: $f"
  elif [ -f "/root/$svc.antes-italock" ]; then
    cp -a "/root/$svc.antes-italock" "$f"; echo "PAM restaurado da cópia: $f"
  else
    echo "ATENÇÃO: não consegui restaurar $f com segurança; confira à mão (a linha do Ítalock continua lá)."
  fi
  rm -f "$tmp"
done

# SSH experimental: tira o SSH_ASKPASS do Ítalock do ambiente de cada usuário
# (o próprio auxiliar desliga, rodando como cada usuário; o bloco do ~/.xsessionrc também só vale com ele instalado)
for f in /home/*/.config/environment.d/italock-ssh.conf; do
  [ -f "$f" ] || continue
  dono=$(stat -c %U "$f")
  if [ -x /usr/local/sbin/italock-ssh ] && id "$dono" >/dev/null 2>&1; then
    setpriv --reuid="$dono" --regid="$(id -g "$dono")" --init-groups env HOME="$(getent passwd "$dono" | cut -d: -f6)" /usr/local/sbin/italock-ssh desligar
  else
    rm -f "$f"
  fi
done
rm -f /etc/italock/ssh_servers.json.tmp

# Restos da versão antiga com "continuidade": scrcpy/adb do Ítalock, "Enviar ao celular" do Nemo/Nautilus, canal
# local e a configuração da câmera virtual do Ítalock (o módulo v4l2loopback fica como está)
pkill -f '^/opt/italock/scrcpy/' 2>/dev/null
rm -f /usr/share/nemo/actions/italock-enviar.nemo_action /home/*/.local/share/nautilus/scripts/"Enviar ao celular (Ítalock)"
rm -f /etc/modprobe.d/italock-camera.conf /etc/modules-load.d/italock-camera.conf
rm -rf /run/italock

systemctl disable --now italock-agent 2>/dev/null
rm -f /etc/systemd/system/italock-agent.service /usr/share/applications/italock.desktop
find /usr/share/icons/hicolor \( -name "italock.png" -o -name "italock.svg" \) 2>/dev/null | xargs -r rm -f
systemctl daemon-reload
rm -rf /opt/italock
rm -f /usr/local/sbin/pcbu_auth /usr/local/sbin/italock-ssh /lib/security/pam_pcbiounlock.so /lib64/security/pam_pcbiounlock.so \
      /usr/lib64/security/pam_pcbiounlock.so /usr/lib/security/pam_pcbiounlock.so
rm -f /lib/*/security/pam_pcbiounlock.so /usr/lib/*/security/pam_pcbiounlock.so 2>/dev/null

if command -v ufw >/dev/null 2>&1; then
  ufw status numbered 2>/dev/null | grep -E '# Italock$' | sed -E 's/^\[ *([0-9]+)\].*/\1/' | sort -rn | while read -r n; do
    ufw --force delete "$n" >/dev/null
  done
fi
if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  ZONA=$(firewall-cmd --get-default-zone 2>/dev/null || echo public)
  FAIXAS="192.168.0.0/16 10.0.0.0/8 172.16.0.0/12 100.64.0.0/10 192.168.42.0/24 192.168.43.0/24 172.20.10.0/28 $(cat /usr/local/lib/italock/faixas-firewall 2>/dev/null | tr '
' ' ')"
  for p in 43295 43296 43301; do
    for net in $FAIXAS; do
      firewall-cmd -q --permanent --zone="$ZONA" --remove-rich-rule="rule family=ipv4 source address=$net port port=$p protocol=tcp accept" 2>/dev/null
    done
  done
  firewall-cmd -q --reload
fi
rm -rf /usr/local/lib/italock
[ "${1:-}" = "--manter-pareamentos" ] || rm -rf /etc/italock
echo "Ítalock removido."
