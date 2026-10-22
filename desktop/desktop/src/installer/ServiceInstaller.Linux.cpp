#include "ServiceInstaller.h"

#include <algorithm>
#include <cstdlib>
#include <regex>
#include <tuple>

#include <optional>
#include <pwd.h>

#include "platform/PlatformHelper.h"
#include "shell/Shell.h"
#include "storage/AppSettings.h"
#include "utils/ResourceHelper.h"

#define EXE_MODULE_DIR std::filesystem::path("/usr/local/sbin/")
#define EXE_MODULE_FILE "pcbu_auth"

#define PAM_MODULE_FILE "pam_pcbiounlock.so"

// Italock: o PAM so procura modulos na pasta dele (no Ubuntu /lib/x86_64-linux-gnu/security, no Fedora /usr/lib64/security).
// A pasta certa e a que tem o pam_unix.so; /lib/security fica junto por compatibilidade com versoes antigas.
static std::vector<std::filesystem::path> PamModuleDirs() {
  std::vector<std::filesystem::path> dirs{};
  auto add = [&](const std::filesystem::path &dir) {
    std::error_code ec{};
    if(std::filesystem::exists(dir / "pam_unix.so", ec) && std::ranges::find(dirs, dir) == dirs.end())
      dirs.emplace_back(dir);
  };
  for(const auto &root : {std::filesystem::path("/lib"), std::filesystem::path("/usr/lib")}) {
    std::error_code ec{};
    for(const auto &entry : std::filesystem::directory_iterator(root, ec))
      if(entry.is_directory(ec))
        add(entry.path() / "security");
  }
  for(const auto &dir : {"/lib64/security", "/usr/lib64/security", "/lib/security", "/usr/lib/security"})
    add(dir);
  return dirs;
}

#define PAM_CONFIG_ENTRY "auth sufficient pam_pcbiounlock.so"
// Italock: na protecao de tela do Cinnamon o celular aprovado pula a senha mas NAO encerra a pilha,
// para o pam_gnome_keyring receber a senha e abrir o cofre (opcao "passar a senha adiante").
#define PAM_CINNAMON_ENTRY "auth [success=1 default=ignore] pam_pcbiounlock.so"
static const std::string PAM_CINNAMON_ITALOCK = "# Ítalock: se o celular aprovar, pula a senha e segue para o cofre de senhas (gnome-keyring)\n"
                                                PAM_CINNAMON_ENTRY "\n"
                                                "auth substack common-auth\n"
                                                "auth optional pam_gnome_keyring.so\n";
static const std::string PAM_CINNAMON_FILE = "/etc/pam.d/cinnamon-screensaver";
// Copia feita antes de o Italock mexer (pelo instalador ou pelo app); versoes antigas deixaram outras.
static const std::string PAM_CINNAMON_BACKUP = "/root/cinnamon-screensaver.antes-italock";
static const std::vector<std::string> PAM_CINNAMON_BACKUPS = {PAM_CINNAMON_BACKUP, "/etc/pam.d/cinnamon-screensaver.antes-italock",
                                                              "/root/cinnamon-screensaver.antes-cofre", "/etc/pam.d/cinnamon-screensaver.antes-cofre"};

// Sub-redes que alcancam as portas do Italock (a mesma regra do instalador .run, onde a decisao esta explicada):
// as redes privadas reais deste computador agora, sem as de containers/VMs (docker0, br-*, veth*...), mais as faixas
// fixas de ancoragem do celular (USB Android, roteador Android, roteador iPhone). Faixas privadas inteiras nao: o docker
// usa 172.16/12 e 192.168/16. O Tailscale entra a parte, so pela tailscale0.
// (chaves dobradas: o texto passa pelo fmt::format)
#define LAN_SUBNETS_CMD                                                                                                                        \
  "{{ ip -4 route show proto kernel scope link 2>/dev/null | awk '{{d=\"\"; for(i=1;i<NF;i++) if($i==\"dev\") d=$(i+1); "                     \
  "if (d !~ /^(lo|docker|br-|veth|virbr|vnet|lxc|lxd|podman|cni|flannel|cali|kube|vmnet|vboxnet|tailscale|zt|wg)/) print $1}}' | "            \
  "grep -E '^(10[.]|172[.](1[6-9]|2[0-9]|3[01])[.]|192[.]168[.])'; echo 192.168.42.0/24 192.168.43.0/24 172.20.10.0/28; }} | sort -u"
// Faixas das versoes antigas (so para apagar as regras delas)
#define OLD_LAN_SUBNETS "192.168.0.0/16 10.0.0.0/8 172.16.0.0/12"

static std::string ReadText(const std::filesystem::path &path) {
  auto bytes = Shell::ReadBytes(path);
  return {bytes.begin(), bytes.end()};
}

static std::vector<std::string> SplitLines(const std::string &text) {
  std::vector<std::string> lines{};
  std::string line{};
  for(char c : text) {
    if(c == '\n') {
      lines.emplace_back(line);
      line.clear();
    } else {
      line += c;
    }
  }
  if(!line.empty())
    lines.emplace_back(line);
  return lines;
}

// Linha de inclusao da pilha de senha que o Italock sabe transformar (mesma regra do instalador .run):
//   "@include common-auth"                       (Debian, Ubuntu, Mint)
//   "auth include system-auth" / "password-auth" (Fedora, Arch, openSUSE: qualquer "<nome>-auth")
// Devolve o nome incluido ("common-auth", "system-auth"...) ou vazio.
static std::string PamIncludedAuth(const std::string &rawLine) {
  static const std::regex includeRe(R"(^@include[ \t]+(common-auth)[ \t]*$)");
  static const std::regex authIncludeRe(R"(^auth[ \t]+include[ \t]+([a-z-]+-auth)[ \t]*$)");
  std::smatch m{};
  if(std::regex_match(rawLine, m, includeRe) || std::regex_match(rawLine, m, authIncludeRe))
    return m[1].str();
  return {};
}

// Linha "auth sufficient pam_pcbiounlock.so" de versoes antigas (antes do formato do cofre).
static bool IsOldSufficientLine(const std::string &line) {
  static const std::regex oldRe(R"(^auth[ \t]+sufficient[ \t]+pam_pcbiounlock\.so.*$)");
  return std::regex_match(line, oldRe);
}

// Liga: a inclusao da pilha de senha vira substack e o celular entra na frente (mesmo formato do instalador .run).
// Uma linha antiga "auth sufficient pam_pcbiounlock.so" e trocada por este formato (migracao).
// Vazio = formato desconhecido, nao mexe.
static std::string PamCinnamonEnable(const std::string &current) {
  std::string out = "# Ítalock: se o celular aprovar, pula a senha e segue para o cofre de senhas (gnome-keyring)\n" PAM_CINNAMON_ENTRY "\n";
  bool hasInclude = false;
  for(const auto &line : SplitLines(current)) {
    if(IsOldSufficientLine(line) || line.starts_with("# Ítalock:"))
      continue;
    if(line.find("pam_pcbiounlock.so") != std::string::npos)
      return {}; // outra linha do Italock que nao reconheco: nao mexe
    auto included = PamIncludedAuth(line);
    if(!included.empty() && !hasInclude) {
      out += "auth substack " + included + "\n";
      hasInclude = true;
    } else {
      out += line + "\n";
    }
  }
  return hasInclude ? out : std::string{};
}

// Desliga sem backup: tira as linhas do Italock e volta o substack para a inclusao original (inverso do PamCinnamonEnable).
static std::string PamCinnamonDisable(const std::string &current) {
  static const std::regex substackRe(R"(^auth[ \t]+substack[ \t]+([a-z-]+-auth)[ \t]*$)");
  std::string out{};
  bool hadItalock = current.find("pam_pcbiounlock.so") != std::string::npos;
  for(const auto &line : SplitLines(current)) {
    if(line.find("pam_pcbiounlock.so") != std::string::npos || line.starts_with("# Ítalock:"))
      continue;
    std::smatch m{};
    if(hadItalock && std::regex_match(line, m, substackRe))
      out += (m[1].str() == "common-auth" ? std::string("@include common-auth") : "auth include " + m[1].str()) + "\n";
    else
      out += line + "\n";
  }
  return out;
}

// Protecao de tela ligada no formato antigo ("auth sufficient"): o cofre de senhas nunca recebia a senha.
static bool HasOldScreenEntry(const std::string &name) {
  for(const auto &line : SplitLines(ReadText(std::filesystem::path("/etc/pam.d") / name)))
    if(IsOldSufficientLine(line))
      return true;
  return false;
}
#define PAM_CONFIG_ENTRY_SDDM                                                                                                                        \
  "auth [success=1 new_authtok_reqd=1 default=ignore] pam_unix.so try_first_pass likeauth nullok\nauth sufficient pam_pcbiounlock.so"

#define UFW_NAME "ufw"
#define FIREWALLD_NAME "firewall-cmd"
#define SELINUX_NAME "semodule"
#define SUDO_NAME "sudo"
#define POLKIT_NAME "pkexec"
#define GDM_NAME "gdm"
#define SDDM_NAME "sddm"
#define KDE_NAME "kde-open"
#define LIGHTDM_NAME "lightdm"
#define CINNAMON_NAME "cinnamon"
#define HYPRLAND_NAME "hyprlock"

ServiceInstaller::ServiceInstaller(const std::function<void(const std::string &)> &logCallback) {
  m_Logger = logCallback;
  m_PAMHelper = PAMHelper(m_Logger);
}

// Arquivos do PAM que o Italock pode mexer (fora a protecao de tela): copia de seguranca antes da primeira alteracao.
static void BackupPamFile(const std::string &configName, const std::function<void(const std::string &)> &logger) {
  auto path = std::filesystem::path("/etc/pam.d") / configName;
  auto backup = std::filesystem::path("/root") / (configName + ".antes-italock");
  std::error_code ec{};
  if(!std::filesystem::exists(path, ec) || std::filesystem::exists(backup, ec))
    return;
  std::filesystem::copy_file(path, backup, ec);
  if(ec)
    throw std::runtime_error(I18n::Tr("Não consegui copiar {} antes de mexer; nada foi alterado.", path.string()));
  logger(I18n::Tr("Cópia de segurança: {}", backup.string()));
}

// Linha "sufficient" so entra se o modulo existe na pasta do PAM: sem ele a linha seria ignorada, mas nao ha por que arriscar.
static void RequireModule() {
  for(const auto &dir : PamModuleDirs())
    if(std::filesystem::exists(dir / PAM_MODULE_FILE))
      return;
  throw std::runtime_error(I18n::Tr("O módulo do Ítalock não está instalado na pasta do PAM. Ligue a proteção da tela primeiro."));
}

// Protecoes de tela com PAM proprio que o Italock sabe ligar (arquivo em /etc/pam.d com "@include common-auth").
static std::vector<std::string> SupportedScreensavers() {
  std::vector<std::string> result{};
  for(const char *name : {"cinnamon-screensaver", "mate-screensaver", "xfce4-screensaver"})
    if(std::filesystem::exists(std::filesystem::path("/etc/pam.d") / name))
      result.emplace_back(name);
  return result;
}

// Nome para mostrar na tela (sem o nome tecnico do servico)
static std::string ScreensaverLabel(const std::string &name) {
  if(name == "cinnamon-screensaver")
    return I18n::Tr("proteção de tela do Cinnamon");
  if(name == "mate-screensaver")
    return I18n::Tr("proteção de tela do MATE");
  if(name == "xfce4-screensaver")
    return I18n::Tr("proteção de tela do XFCE");
  return I18n::Tr("proteção de tela");
}

static bool IsScreenEntryOn() {
  for(const auto &name : SupportedScreensavers())
    if(PAMHelper::HasConfigEntry(name, "pam_pcbiounlock.so"))
      return true;
  return false;
}

static bool IsLoginManagerOn() {
  return PAMHelper::HasConfigEntry("gdm-password", PAM_CONFIG_ENTRY) || PAMHelper::HasConfigEntry("sddm", PAM_CONFIG_ENTRY_SDDM) ||
         PAMHelper::HasConfigEntry("kde", PAM_CONFIG_ENTRY) || PAMHelper::HasConfigEntry("lightdm", PAM_CONFIG_ENTRY) ||
         PAMHelper::HasConfigEntry("hyprlock", PAM_CONFIG_ENTRY);
}

// ---- SSH pelo celular (experimental; porte do PulseUnlock v4) ----
// O ssh DESTE computador (conexoes que saem daqui) pede ao celular a senha guardada do servidor, pelo SSH_ASKPASS.
// Nao mexe no sshd nem no PAM: ninguem fica trancado fora. Liga/desliga = um arquivo no ambiente do usuario
// (~/.config/environment.d/italock-ssh.conf, lido pelo systemd do usuario; vale a partir da proxima sessao).
#define SSH_HELPER "/usr/local/sbin/italock-ssh"
#define SSH_ENV_REL ".config/environment.d/italock-ssh.conf"

struct SshUser {
  uid_t uid{};
  gid_t gid{};
  std::string home{};
};

static std::optional<SshUser> SshTargetUser() {
  auto name = PlatformHelper::GetCurrentUser();
  auto *pw = name.empty() ? nullptr : getpwnam(name.c_str());
  if(!pw || pw->pw_uid == 0 || !pw->pw_dir)
    return {};
  return SshUser{pw->pw_uid, pw->pw_gid, pw->pw_dir};
}

static bool IsSshEnabled() {
  auto user = SshTargetUser();
  if(!user)
    return false;
  auto path = std::filesystem::path(user->home) / SSH_ENV_REL;
  std::error_code ec{};
  if(!std::filesystem::exists(path, ec))
    return false;
  return ReadText(path).find("SSH_ASKPASS=" SSH_HELPER) != std::string::npos;
}

// Grava/apaga COMO o usuario (setpriv): o root nunca escreve na pasta pessoal de alguem (links simbolicos la dentro
// poderiam apontar para arquivos do sistema).
static void SetSshEnabled(bool enabled) {
  auto user = SshTargetUser();
  if(!user)
    throw std::runtime_error(I18n::Tr("Não achei o usuário da sessão para ligar o SSH pelo celular."));
  if(!std::filesystem::exists(SSH_HELPER)) {
    if(enabled)
      throw std::runtime_error(I18n::Tr("O auxiliar do SSH não está instalado. Instale o Ítalock de novo pelo instalador."));
    return;
  }
  // O proprio auxiliar grava (environment.d + bloco marcado no ~/.xsessionrc), ja sem root, como o usuario.
  auto cmd = fmt::format("setpriv --reuid={} --regid={} --init-groups env HOME=\"{}\" " SSH_HELPER " {}", user->uid, user->gid, user->home,
                         enabled ? "ligar" : "desligar");
  if(Shell::RunCommand(cmd).exitCode != 0)
    throw std::runtime_error(I18n::Get("error_file_write", user->home + "/" SSH_ENV_REL));
}

// Italock: ids
//   login          protecao de tela (Cinnamon) - o interruptor da tela principal
//   pamSetPassword abrir o chaveiro (gnome-keyring) junto
//   sudo, polkit, loginManager, ssh  RECURSOS EXPERIMENTAIS (desligados por padrao; so mudam algo quando ligados na
//                               tela "Recursos experimentais", com confirmacao)
std::vector<ServiceSetting> ServiceInstaller::GetSettings() {
  auto hasLoginManager = IsProgramInstalled(GDM_NAME) || IsProgramInstalled(SDDM_NAME) || IsProgramInstalled(KDE_NAME) ||
                         IsProgramInstalled(LIGHTDM_NAME) || IsProgramInstalled(HYPRLAND_NAME);
  const bool sshAvailable = std::filesystem::exists(SSH_HELPER) && IsProgramInstalled("ssh") && SshTargetUser().has_value();
  return {{"login", I18n::Get("service_setting_login_manager"), IsScreenEntryOn(), !SupportedScreensavers().empty()},
          {"pamSetPassword", I18n::Get("service_setting_pam_set_pw"), AppSettings::Get().unixSetPasswordPAM, false},
          {"sudo", I18n::Get("service_setting_sudo"), PAMHelper::HasConfigEntry("sudo", PAM_CONFIG_ENTRY), IsProgramInstalled(SUDO_NAME)},
          {"polkit", I18n::Get("service_setting_polkit"), PAMHelper::HasConfigEntry("polkit-1", PAM_CONFIG_ENTRY), IsProgramInstalled(POLKIT_NAME)},
          {"loginManager", I18n::Tr("Tela de login"), IsLoginManagerOn(), hasLoginManager},
          {"ssh", I18n::Tr("Destravar sessões SSH pelo celular"), IsSshEnabled(), sshAvailable}};
}

void ServiceInstaller::ApplySettings(const std::vector<ServiceSetting> &settings, bool useDefault) {
  for(auto setting : settings) {
    auto isEnabled = useDefault ? false : setting.enabled; // Italock: padrao e sempre desligado
    if(setting.id == "sudo") {
      if(isEnabled == PAMHelper::HasConfigEntry("sudo", PAM_CONFIG_ENTRY))
        continue;
      if(isEnabled) {
        RequireModule();
        BackupPamFile("sudo", m_Logger);
      }
      m_PAMHelper.SetConfigEntry("sudo", PAM_CONFIG_ENTRY, isEnabled);
    } else if(setting.id == "polkit") {
      if(isEnabled == PAMHelper::HasConfigEntry("polkit-1", PAM_CONFIG_ENTRY))
        continue;
      if(isEnabled) {
        RequireModule();
        BackupPamFile("polkit-1", m_Logger);
      }
      m_PAMHelper.SetConfigEntry("polkit-1", PAM_CONFIG_ENTRY, isEnabled);
    } else if(setting.id == "loginManager") {
      if(isEnabled == IsLoginManagerOn())
        continue;
      if(isEnabled)
        RequireModule();
      const std::vector<std::tuple<const char *, const char *, std::string>> managers = {
          {GDM_NAME, "gdm-password", PAM_CONFIG_ENTRY}, {SDDM_NAME, "sddm", PAM_CONFIG_ENTRY_SDDM}, {KDE_NAME, "kde", PAM_CONFIG_ENTRY},
          {LIGHTDM_NAME, "lightdm", PAM_CONFIG_ENTRY},  {HYPRLAND_NAME, "hyprlock", PAM_CONFIG_ENTRY}};
      for(const auto &[program, config, entry] : managers) {
        if(isEnabled && !IsProgramInstalled(program))
          continue;
        if(isEnabled)
          BackupPamFile(config, m_Logger);
        m_PAMHelper.SetConfigEntry(config, entry, isEnabled);
      }
    } else if(setting.id == "login") {
      // Protecoes de tela suportadas (Cinnamon, MATE, XFCE), no formato do cofre (ver PamCinnamonEnable) - o mesmo do
      // instalador .run. GNOME/KDE e outras: o Italock so trava, nao destrava.
      bool any = false;
      for(const auto &name : SupportedScreensavers()) {
        any = true;
        const auto path = std::filesystem::path("/etc/pam.d") / name;
        const bool hasEntry = PAMHelper::HasConfigEntry(name, "pam_pcbiounlock.so");
        // Formato antigo ("auth sufficient"): ligado, mas o cofre de senhas nunca recebia a senha. Migra para o atual.
        const bool migrate = isEnabled && hasEntry && HasOldScreenEntry(name);
        if(hasEntry == isEnabled && !migrate)
          continue;
        if(migrate)
          m_Logger(I18n::Tr("Atualizando a {} para o formato novo (com o cofre de senhas)...", ScreensaverLabel(name)));
        if(isEnabled)
          RequireModule();
        m_Logger(isEnabled ? I18n::Tr("Ligando o celular na {}...", ScreensaverLabel(name)) : I18n::Tr("Desligando o celular da {}...", ScreensaverLabel(name)));
        const auto current = ReadText(path);
        std::string content{};
        std::vector<std::string> backups = {"/root/" + name + ".antes-italock", "/etc/pam.d/" + name + ".antes-italock"};
        if(name == "cinnamon-screensaver")
          backups = PAM_CINNAMON_BACKUPS;
        if(isEnabled) {
          if(!std::filesystem::exists(backups.front()) && !current.empty())
            Shell::WriteBytes(backups.front(), {current.begin(), current.end()});
          content = PamCinnamonEnable(current);
          if(content.empty())
            throw std::runtime_error(I18n::Tr("Formato desconhecido em {}; nada foi alterado.", path.string()));
        } else {
          // Volta a copia de antes do Italock, se houver uma que nao tenha o Italock; senao so tira as linhas dele.
          for(const auto &backup : backups) {
            if(!std::filesystem::exists(backup))
              continue;
            auto saved = ReadText(backup);
            if(!saved.empty() && saved.find("pam_pcbiounlock.so") == std::string::npos) {
              content = saved;
              m_Logger(I18n::Tr("Restaurando a cópia {}...", backup));
              break;
            }
          }
          if(content.empty())
            content = PamCinnamonDisable(current);
        }
        if(content.empty() || !Shell::WriteBytes(path, {content.begin(), content.end()}))
          throw std::runtime_error(I18n::Get("error_file_write", path.string()));
      }
      if(!any && isEnabled)
        throw std::runtime_error(I18n::Tr("A proteção de tela deste computador não é suportada (só Cinnamon, MATE e XFCE). O Ítalock trava a tela "
                                          "pelo celular, mas destravar pelo celular não vai funcionar aqui."));
    } else if(setting.id == "ssh") {
      if(isEnabled != IsSshEnabled()) {
        m_Logger(isEnabled ? I18n::Tr("Ligando o SSH pelo celular (vale a partir da próxima vez que entrar no computador)...")
                           : I18n::Tr("Desligando o SSH pelo celular..."));
        SetSshEnabled(isEnabled);
      }
    } else if(setting.id == "pamSetPassword") {
      auto storage = AppSettings::Get();
      storage.unixSetPasswordPAM = setting.enabled; // "padrao" aqui mantem o que estava
      if(!useDefault)
        AppSettings::Save(storage);
      // Cofre ligado com a protecao de tela ainda no formato antigo: o chaveiro nunca receberia a senha. Migra.
      if(!useDefault && setting.enabled) {
        for(const auto &name : SupportedScreensavers()) {
          if(!HasOldScreenEntry(name))
            continue;
          for(auto login : GetSettings())
            if(login.id == "login") {
              login.enabled = true;
              ApplySettings({login}, false);
            }
          break;
        }
      }
    } else {
      spdlog::warn("Unknown service setting {}.", setting.id);
    }
  }
}

bool ServiceInstaller::IsProtectionOn() {
  return IsInstalled() && IsScreenEntryOn();
}

void ServiceInstaller::SetProtection(bool enabled) {
  if(enabled && !IsInstalled())
    Install();
  for(auto setting : GetSettings()) {
    if(setting.id != "login")
      continue;
    setting.enabled = enabled;
    ApplySettings({setting}, false);
  }
}

bool ServiceInstaller::IsInstalled() {
  for(const auto &pamDir : PamModuleDirs()) {
    if(std::filesystem::exists(pamDir / PAM_MODULE_FILE))
      return std::filesystem::exists(EXE_MODULE_DIR / EXE_MODULE_FILE);
  }
  return false;
}

void ServiceInstaller::Install() {
  m_Logger(I18n::Tr("Copiando o auxiliar de autenticação..."));
  auto nativeExe = ResourceHelper::GetResource(":/res/natives/{}", EXE_MODULE_FILE);
  auto exePath = EXE_MODULE_DIR / EXE_MODULE_FILE;
  auto result = Shell::WriteBytes(EXE_MODULE_DIR / EXE_MODULE_FILE, nativeExe);
  if(!result)
    throw std::runtime_error(I18n::Get("error_file_write", exePath.string()));
  result = Shell::RunCommand(fmt::format("chmod +x {0} && chmod u+s {0}", exePath.string())).exitCode == 0;
  if(!result)
    throw std::runtime_error(I18n::Get("error_exec_setuid", exePath.string()));

  m_Logger(I18n::Tr("Copiando o módulo PAM..."));
  auto pamModule = ResourceHelper::GetResource(":/res/natives/{}", PAM_MODULE_FILE);
  auto pamDirs = PamModuleDirs();
  if(pamDirs.empty())
    throw std::runtime_error(I18n::Tr("Não achei a pasta de módulos do PAM (a que tem o pam_unix.so)."));
  for(const auto &pamDir : pamDirs) {
    auto pamPath = pamDir / PAM_MODULE_FILE;
    result = Shell::WriteBytes(pamPath, pamModule);
    if(!result)
      throw std::runtime_error(I18n::Get("error_file_write", pamPath.string()));
  }

  auto settings = AppSettings::Get();
  if(IsProgramInstalled(UFW_NAME)) {
    // Italock: so a rede local e o Tailscale (antes abria as portas para qualquer endereco).
    m_Logger(I18n::Tr("Adicionando regras do firewall (ufw) só para a rede local e o Tailscale..."));
    result = Shell::RunCommand(fmt::format(
                 "for net in $(" LAN_SUBNETS_CMD "); do "
                 "ufw allow from \"$net\" to any port {1} proto tcp comment Italock && "
                 "ufw allow from \"$net\" to any port {2} proto tcp comment Italock || exit 1; done; "
                 "ufw allow in on tailscale0 from 100.64.0.0/10 to any port {1} proto tcp comment Italock && "
                 "ufw allow in on tailscale0 from 100.64.0.0/10 to any port {2} proto tcp comment Italock",
                 settings.pairingDiscoveryPort, settings.pairingServerPort, settings.unlockServerPort))
                 .exitCode == 0;
    if(!result)
      m_Logger(I18n::Get("error_firewall_rule_add", "ufw"));
  }
  if(IsProgramInstalled(FIREWALLD_NAME) && Shell::RunCommand("firewall-cmd --state >/dev/null 2>&1").exitCode == 0) {
    // Italock: so na zona padrao e so com origem (rich rules), como o instalador .run; nunca porta aberta para qualquer um.
    m_Logger(I18n::Tr("Adicionando regras do firewall (firewalld) só para a rede local e o Tailscale..."));
    result = Shell::RunCommand(fmt::format(
                 "Z=$(firewall-cmd --get-default-zone 2>/dev/null || echo public); "
                 "for p in {0} {1}; do for net in $(" LAN_SUBNETS_CMD ") 100.64.0.0/10; do "
                 "firewall-cmd -q --permanent --zone=\"$Z\" --add-rich-rule=\"rule family=ipv4 source address=$net port port=$p protocol=tcp accept\" "
                 "|| exit 1; done; done; firewall-cmd -q --reload",
                 settings.pairingServerPort, settings.unlockServerPort))
                 .exitCode == 0;
    if(!result)
      m_Logger(I18n::Get("error_firewall_rule_add", "firewalld"));
  }
  if(IsProgramInstalled(SELINUX_NAME)) {
    m_Logger(I18n::Tr("Instalando a política do SELinux..."));
    auto policyData = ResourceHelper::GetResource(":/res/selinux/pcbu_auth_policy.pp");
    // Pasta temporaria propria (mkdtemp, 0700): um /tmp/<nome fixo> poderia ser trocado por outro usuario no meio.
    char dirTemplate[] = "/tmp/italock-selinux-XXXXXX";
    if(!mkdtemp(dirTemplate))
      throw std::runtime_error(I18n::Get("error_selinux_policy_install"));
    auto tmpDir = std::filesystem::path(dirTemplate);
    auto tmpPath = tmpDir / "pcbu_auth_policy.pp";
    result = Shell::WriteBytes(tmpPath, policyData) &&
             Shell::RunCommand(fmt::format("semodule -i \"{}\"", tmpPath.string())).exitCode == 0;
    std::error_code ec{};
    std::filesystem::remove_all(tmpDir, ec);
    if(!result)
      throw std::runtime_error(I18n::Get("error_selinux_policy_install"));
  }
  m_Logger(I18n::Tr("Pronto."));
}

void ServiceInstaller::Uninstall() {
  m_Logger(I18n::Tr("Removendo o auxiliar de autenticação..."));
  auto exePath = EXE_MODULE_DIR / EXE_MODULE_FILE;
  auto result = Shell::RemoveFile(exePath);
  if(!result)
    throw std::runtime_error(I18n::Get("error_file_remove", exePath.string()));

  m_Logger(I18n::Tr("Removendo o módulo PAM..."));
  // Antes de apagar o modulo, tira todas as linhas do PAM que o usam (senha continua funcionando de qualquer jeito).
  SetProtection(false);
  for(auto setting : GetSettings()) {
    if(setting.id == "sudo" || setting.id == "polkit" || setting.id == "loginManager" || setting.id == "ssh") {
      setting.enabled = false;
      ApplySettings({setting}, false);
    }
  }
  auto removeDirs = PamModuleDirs();
  removeDirs.emplace_back("/lib/security");
  for(const auto &pamDir : removeDirs) {
    auto pamPath = pamDir / PAM_MODULE_FILE;
    if(!std::filesystem::exists(pamPath))
      continue;
    result = Shell::RemoveFile(pamPath);
    if(!result)
      throw std::runtime_error(I18n::Get("error_file_remove", pamPath.string()));
  }

  auto settings = AppSettings::Get();
  if(IsProgramInstalled(UFW_NAME)) {
    m_Logger(I18n::Tr("Removendo regras do firewall (ufw)..."));
    // Regras abertas de versoes antigas e as restritas atuais (a do agente, porta 43301, fica: e do instalador).
    Shell::RunCommand(fmt::format("ufw delete allow {}/udp; ufw delete allow {}/tcp; ufw delete allow {}/tcp", settings.pairingDiscoveryPort,
                                  settings.pairingServerPort, settings.unlockServerPort));
    result = Shell::RunCommand(fmt::format("for net in $(" LAN_SUBNETS_CMD ") " OLD_LAN_SUBNETS "; do "
                                           "ufw delete allow from \"$net\" to any port {0} proto udp; "
                                           "ufw delete allow from \"$net\" to any port {1} proto tcp; "
                                           "ufw delete allow from \"$net\" to any port {2} proto tcp; done; "
                                           "ufw delete allow in on tailscale0 to any port {1} proto tcp; "
                                           "ufw delete allow in on tailscale0 to any port {2} proto tcp; "
                                           "ufw delete allow in on tailscale0 from 100.64.0.0/10 to any port {1} proto tcp; "
                                           "ufw delete allow in on tailscale0 from 100.64.0.0/10 to any port {2} proto tcp; true",
                                           settings.pairingDiscoveryPort, settings.pairingServerPort, settings.unlockServerPort))
                 .exitCode == 0;
    if(!result)
      m_Logger(I18n::Get("error_firewall_rule_remove", "ufw"));
  }
  if(IsProgramInstalled(FIREWALLD_NAME) && Shell::RunCommand("firewall-cmd --state >/dev/null 2>&1").exitCode == 0) {
    m_Logger(I18n::Tr("Removendo regras do firewall (firewalld)..."));
    // Portas abertas de versoes antigas e as rich rules atuais, so na zona padrao
    result = Shell::RunCommand(fmt::format(
                 "Z=$(firewall-cmd --get-default-zone 2>/dev/null || echo public); "
                 "firewall-cmd -q --permanent --zone=\"$Z\" --remove-port={0}/udp; "
                 "for p in {1} {2}; do firewall-cmd -q --permanent --zone=\"$Z\" --remove-port=$p/tcp; "
                 "for net in $(" LAN_SUBNETS_CMD ") " OLD_LAN_SUBNETS " 100.64.0.0/10; do "
                 "firewall-cmd -q --permanent --zone=\"$Z\" --remove-rich-rule=\"rule family=ipv4 source address=$net port port=$p protocol=tcp accept\"; "
                 "done; done; firewall-cmd -q --reload",
                 settings.pairingDiscoveryPort, settings.pairingServerPort, settings.unlockServerPort))
                 .exitCode == 0;
    if(!result)
      m_Logger(I18n::Get("error_firewall_rule_remove", "firewalld"));
  }
  if(IsProgramInstalled(SELINUX_NAME)) {
    m_Logger(I18n::Tr("Removendo a política do SELinux..."));
    result = Shell::RunCommand("semodule -r pcbu_auth_policy").exitCode == 0;
    if(!result)
      throw std::runtime_error(I18n::Get("error_selinux_policy_uninstall"));
  }
  m_Logger(I18n::Tr("Pronto."));
}

bool ServiceInstaller::IsProgramInstalled(const std::string &pathName) {
  return Shell::RunUserCommand(fmt::format("command -v {} >/dev/null 2>&1", pathName)).exitCode == 0 ||
         Shell::RunUserCommand(fmt::format("systemctl cat {}", pathName)).exitCode == 0;
}
