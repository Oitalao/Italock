#include "ServiceInstaller.h"

#include <Windows.h>

#include "WinFirewallHelper.h"
#include "platform/PlatformHelper.h"
#include "shell/Shell.h"
#include "storage/AppSettings.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/ResourceHelper.h"
#include "utils/StringUtils.h"

#define LIB_MODULE_FILE "italock-cp.dll"
#define CRED_PROVIDER_NAME "italock-cp"
#define CRED_PROVIDER_GUID "{4937B1C9-EBE0-47EF-9E77-8C41E6CEFFFB}"
#define APP_FIREWALL_RULE_NAME "Italock" // antiga, so para apagar
#define LOGONUI_FIREWALL_RULE_NAME "LogonUI (Italock)"
#define LOGONUI_TS_FIREWALL_RULE_NAME "LogonUI (Italock, Tailscale)"
// Italock: so a rede local e o Tailscale (100.64.0.0/10) alcancam as portas, nunca a internet inteira. Rede local so
// em perfil Particular ou Dominio (rede publica de cafe/aeroporto fica de fora); o Tailscale vale pela interface dele
// em qualquer perfil (em alguns PCs o Windows classifica a rede do Tailscale como publica).

std::filesystem::path ServiceInstaller_GetSysDir() {
  wchar_t sysDir[MAX_PATH]{};
  if(GetSystemDirectoryW(sysDir, MAX_PATH) > 0)
    return sysDir;
  return "C:\\Windows\\System32";
}

ServiceInstaller::ServiceInstaller(const std::function<void(const std::string &)> &logCallback) {
  m_Logger = logCallback;
}

std::vector<ServiceSetting> ServiceInstaller::GetSettings() {
  return {
      {"unlockBehavior", I18n::Get("service_setting_unlock_behavior"),
       {
           {"key_press_lock_only", I18n::Get("unlock_behavior_key_press_lock_only")},
           {"foreground_always", I18n::Get("unlock_behavior_foreground_always")},
           {"wake_only", I18n::Get("unlock_behavior_wake_only")},
           {"foreground_lock_only", I18n::Get("unlock_behavior_foreground_lock_only")},
           {"key_press", I18n::Get("unlock_behavior_key_press")},
           {"none", I18n::Get("unlock_behavior_none")},
       },
       AppSettings::Get().winUnlockBehavior, "key_press_lock_only"},
      {"hidePasswordField", I18n::Get("service_setting_hide_pw_field"), AppSettings::Get().winHidePasswordField, false},
      {"forceCredProv", I18n::Get("service_setting_force_cred_prov"), AppSettings::Get().winForceDefaultCredProv, true},
  };
}

void ServiceInstaller::ApplySettings(const std::vector<ServiceSetting> &settings, bool useDefault) {
  for(auto setting : settings) {
    if(setting.id == "unlockBehavior") {
      auto storage = AppSettings::Get();
      storage.winUnlockBehavior = useDefault ? setting.defaultValue : setting.selectedValue;
      AppSettings::Save(storage);
    } else if(setting.id == "hidePasswordField") {
      auto storage = AppSettings::Get();
      storage.winHidePasswordField = useDefault ? setting.defaultVal : setting.enabled;
      AppSettings::Save(storage);
    } else if(setting.id == "forceCredProv") {
      auto storage = AppSettings::Get();
      storage.winForceDefaultCredProv = useDefault ? setting.defaultVal : setting.enabled;
      AppSettings::Save(storage);
    } else {
      spdlog::warn("Unknown service setting {}.", setting.id);
    }
  }
}

bool ServiceInstaller::IsInstalled() {
  return std::filesystem::exists(ServiceInstaller_GetSysDir() / LIB_MODULE_FILE);
}

bool ServiceInstaller::IsProtectionOn() {
  return IsInstalled();
}

void ServiceInstaller::SetProtection(bool enabled) {
  if(enabled == IsInstalled())
    return;
  if(enabled) {
    Install();
    ApplySettings(GetSettings(), false); // mantem o que a pessoa escolheu em Avancado
  } else {
    Uninstall();
  }
}

void ServiceInstaller::Install() {
  m_Logger("Copiando o módulo da tela de bloqueio...");
  auto nativeLib = ResourceHelper::GetResource(":/res/natives/{}", LIB_MODULE_FILE);
  auto libPath = ServiceInstaller_GetSysDir() / LIB_MODULE_FILE;
  auto result = Shell::WriteBytes(libPath, nativeLib);
  if(!result)
    throw std::runtime_error(I18n::Get("error_file_write", libPath.string()));

  m_Logger("Criando as entradas no registro...");
  result =
      Shell::RunCommand(
          fmt::format(
              R"(reg add "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{0}" /t REG_SZ /d {1} /f)",
              CRED_PROVIDER_GUID, CRED_PROVIDER_NAME))
              .exitCode == 0 &&
      Shell::RunCommand(fmt::format(R"(reg add "HKEY_CLASSES_ROOT\CLSID\{0}" /t REG_SZ /d {1} /f)", CRED_PROVIDER_GUID, CRED_PROVIDER_NAME))
              .exitCode == 0 &&
      Shell::RunCommand(
          fmt::format(R"(reg add "HKEY_CLASSES_ROOT\CLSID\{0}\InprocServer32" /t REG_SZ /d {1} /f)", CRED_PROVIDER_GUID, CRED_PROVIDER_NAME))
              .exitCode == 0 &&
      Shell::RunCommand(
          fmt::format(R"(reg add "HKEY_CLASSES_ROOT\CLSID\{0}\InprocServer32" /t REG_SZ /v ThreadingModel /d Apartment /f)", CRED_PROVIDER_GUID))
              .exitCode == 0;
  if(!result)
    throw std::runtime_error(I18n::Get("error_registry_add"));

  // A regra de entrada do app ("Italock (app)") e do agente sao do instalador (Instalar-Italock.ps1). A antiga "Italock",
  // que este botao criava, duplicava a do instalador: sai.
  Shell::RunCommand(fmt::format(R"(netsh advfirewall firewall delete rule name="{0}")", APP_FIREWALL_RULE_NAME));

  auto logonUiPath = ServiceInstaller_GetSysDir() / "LogonUI.exe";
  m_Logger("Removendo regras antigas do firewall da tela de bloqueio...");
  WinFirewallHelper::RemoveAllRulesForProgram(logonUiPath.string());
  Shell::RunCommand(fmt::format(R"(netsh advfirewall firewall delete rule name="{0}")", LOGONUI_TS_FIREWALL_RULE_NAME));
  m_Logger("Adicionando a regra do firewall para a tela de bloqueio...");
  result = Shell::RunCommand(fmt::format(R"(netsh advfirewall firewall add rule name="{0}" dir=in program="{1}" profile=private,domain action=allow remoteip=localsubnet,100.64.0.0/10)",
                                         LOGONUI_FIREWALL_RULE_NAME, logonUiPath.string()))
               .exitCode == 0;
  // Tailscale: pela interface dele, qualquer perfil, so enderecos do Tailscale
  Shell::RunCommand(fmt::format(
      R"(powershell -NoProfile -NonInteractive -Command "if (Get-NetAdapter -Name 'Tailscale' -ErrorAction SilentlyContinue) {{ New-NetFirewallRule -DisplayName '{0}' -Direction Inbound -Program '{1}' -Action Allow -Profile Any -InterfaceAlias 'Tailscale' -RemoteAddress 100.64.0.0/10 | Out-Null }}")",
      LOGONUI_TS_FIREWALL_RULE_NAME, logonUiPath.string()));
  if(!result)
    m_Logger(I18n::Get("error_firewall_rule_add", "Firewall do Windows (tela de bloqueio)"));

  m_Logger("Definindo o Ítalock como opção padrão da tela de bloqueio...");
  for(auto device : PairedDevicesStorage::GetDevices()) {
    if(!PlatformHelper::SetDefaultCredProv(device.userName, CRED_PROVIDER_GUID))
      m_Logger(fmt::format("Não foi possível definir a opção padrão da tela de bloqueio para '{}'.", device.userName));
  }
  m_Logger("Pronto.");
}

void ServiceInstaller::Uninstall() {
  m_Logger("Removendo o módulo da tela de bloqueio...");
  auto libPath = ServiceInstaller_GetSysDir() / LIB_MODULE_FILE;
  auto result = Shell::RemoveFile(libPath);
  if(!result)
    throw std::runtime_error(I18n::Get("error_file_remove", libPath.string()));

  m_Logger("Removendo as entradas do registro...");
  result = Shell::RunCommand(
               fmt::format(R"(reg delete "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{0}" /f)",
                           CRED_PROVIDER_GUID))
                   .exitCode == 0 &&
           Shell::RunCommand(fmt::format(R"(reg delete "HKEY_CLASSES_ROOT\CLSID\{0}" /f)", CRED_PROVIDER_GUID)).exitCode == 0;
  if(!result)
    throw std::runtime_error(I18n::Get("error_registry_remove"));

  m_Logger("Removendo a regra do firewall da tela de bloqueio...");
  Shell::RunCommand(fmt::format(R"(netsh advfirewall firewall delete rule name="{0}")", APP_FIREWALL_RULE_NAME));
  Shell::RunCommand(fmt::format(R"(netsh advfirewall firewall delete rule name="{0}")", LOGONUI_TS_FIREWALL_RULE_NAME));
  result = Shell::RunCommand(fmt::format(R"(netsh advfirewall firewall delete rule name="{0}")", LOGONUI_FIREWALL_RULE_NAME)).exitCode == 0;
  if(!result)
    m_Logger(I18n::Get("error_firewall_rule_remove", "Firewall do Windows (tela de bloqueio)"));
  m_Logger("Pronto.");
}
