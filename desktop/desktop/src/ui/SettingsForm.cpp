#include "SettingsForm.h"

#include <algorithm>
#include <QVariantMap>

#include "platform/BluetoothHelper.h"
#include "shell/Shell.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/AppInfo.h"

#include <fstream>

#ifdef WINDOWS
#include <QSettings>
// Bloqueio dinamico do Windows (trava quando o celular pareado por Bluetooth se afasta). Por usuario.
static constexpr auto WINLOGON_KEY = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
static bool IsDynamicLockOn() {
  QSettings reg(WINLOGON_KEY, QSettings::NativeFormat);
  return reg.value("EnableGoodbye", 0).toInt() == 1;
}
static void SetDynamicLock(bool on) {
  QSettings reg(WINLOGON_KEY, QSettings::NativeFormat);
  reg.setValue("EnableGoodbye", on ? 1 : 0);
  reg.sync();
}
#endif

AppSettingsModel SettingsForm::GetSettings() {
  return m_EditSettings;
}

void SettingsForm::SetSettings(const AppSettingsModel &settings) {
  m_EditSettings = settings;
}

QVariantList SettingsForm::GetServiceSettings() {
  QVariantList result{};
  for(const auto &setting : m_EditServiceSettings) {
    ServiceSettingModel entry{};
    entry.id = QString::fromUtf8(setting.id);
    entry.name = QString::fromUtf8(setting.name);
    entry.enabled = setting.enabled;
    entry.type = QString::fromUtf8(setting.type);
    entry.selectedValue = QString::fromUtf8(setting.selectedValue);
    for(const auto &option : setting.options) {
      entry.optionNames.append(QString::fromUtf8(option.name));
      entry.optionValues.append(QString::fromUtf8(option.value));
    }
    result.append(QVariant::fromValue(entry));
  }
  return result;
}

void SettingsForm::SetServiceSettings(const QVariantList &settings) {
  for(const auto &varSetting : settings) {
    auto model = varSetting.value<ServiceSettingModel>();
    auto id = model.id.toStdString();
    for(auto &setting : m_EditServiceSettings) {
      if(setting.id != id)
        continue;
      setting.enabled = model.enabled;
      setting.selectedValue = model.selectedValue.toStdString();
      break;
    }
  }
}

bool SettingsForm::IsDebugLoggingEnabled() {
  return std::filesystem::exists(AppSettings::GetBaseDir() / "LOG_DEBUG");
}

void SettingsForm::SetDebugLoggingEnabled(bool enabled) {
  if(enabled) {
    // std::ofstream: Windows.h (via BluetoothHelper) troca CreateFile por CreateFileW e quebra Shell::CreateFile aqui
    if(!std::ofstream(AppSettings::GetBaseDir() / "LOG_DEBUG").good())
      spdlog::error("Failed to create debug log file.");
  } else {
    if(!Shell::RemoveFile(AppSettings::GetBaseDir() / "LOG_DEBUG"))
      spdlog::error("Failed to remove debug log file.");
  }
}

QString SettingsForm::GetOperatingSystem() {
  return QString::fromUtf8(AppInfo::GetOperatingSystem());
}

static bool IsUDPMethod(PairingMethod method) {
  return method == PairingMethod::UDP || method == PairingMethod::MANUAL_UDP;
}

bool SettingsForm::HasUDPDevices() {
  return std::ranges::any_of(PairedDevicesStorage::GetDevices(), [](PairedDevice &device) { return IsUDPMethod(device.pairingMethod); });
}

void SettingsForm::RemoveUDPDevices() {
  PairedDevicesStorage::RemoveDevicesIf([](const PairedDevice &device) { return IsUDPMethod(device.pairingMethod); });
}

void SettingsForm::Load() {
  AppSettings::InvalidateCache();
  m_EditSettings = AppSettingsModel(AppSettings::Get());
  m_EditServiceSettings = ServiceInstaller().GetSettings();
}

void SettingsForm::SetServiceSetting(const QString &id, bool enabled, const QString &selectedValue) {
  for(auto &setting : m_EditServiceSettings) {
    if(setting.id != id.toStdString())
      continue;
    setting.enabled = enabled;
    setting.selectedValue = selectedValue.toStdString();
  }
}

QString SettingsForm::Save() {
  auto storage = AppSettings::Get();
  auto edited = m_EditSettings.ToStorage();
  // So os campos que esta tela edita; o resto (experimentais, opcoes do sistema) vem do arquivo atual.
  storage.serverIP = edited.serverIP;
  storage.serverMAC = edited.serverMAC;
  storage.pairingDiscoveryPort = edited.pairingDiscoveryPort;
  storage.pairingServerPort = edited.pairingServerPort;
  storage.unlockServerPort = edited.unlockServerPort;
  storage.clientSocketTimeout = edited.clientSocketTimeout;
  storage.clientConnectTimeout = edited.clientConnectTimeout;
  storage.clientConnectRetries = edited.clientConnectRetries;
  AppSettings::Save(storage);
  QString error{};
  try {
    // Experimentais nao passam por aqui (tem tela e confirmacao proprias).
    std::vector<ServiceSetting> regular{};
    for(const auto &setting : m_EditServiceSettings)
      if(setting.id != "sudo" && setting.id != "polkit" && setting.id != "loginManager")
        regular.emplace_back(setting);
    auto installer = ServiceInstaller();
    installer.ApplySettings(regular, false);
  } catch(const std::exception &ex) {
    spdlog::error("{}", ex.what());
    error = QString::fromUtf8(ex.what());
  }
  AppSettings::InvalidateCache();
  return error;
}

QVariantList SettingsForm::GetExperimental() {
  QVariantList result{};
  auto add = [&](const char *id, const QString &name, const QString &desc, const QString &risk, bool enabled, bool available, bool confirm) {
    QVariantMap entry{};
    entry["id"] = id;
    entry["name"] = name;
    entry["desc"] = desc;
    entry["risk"] = risk;
    entry["enabled"] = enabled;
    entry["available"] = available;
    entry["needsConfirm"] = confirm;
    result.append(entry);
  };
  bool hasBtPhone = false;
  for(const auto &d : PairedDevicesStorage::GetDevices())
    if(!d.bluetoothAddress.empty())
      hasBtPhone = true;
#ifdef WINDOWS
  add("proximity", "Travar quando o celular se afastar",
      "Usa o Bloqueio dinâmico do Windows: cerca de um minuto depois que o celular pareado por Bluetooth sai de perto, o computador trava.",
      "Se o Bluetooth do celular desligar ou falhar, o computador também trava. Vale só para a sua conta.",
      IsDynamicLockOn(), hasBtPhone && BluetoothHelper::IsAvailable(), false);
#else
  add("proximity", "Travar quando o celular se afastar",
      "Uma vez por minuto o computador procura o celular pareado por Bluetooth. Sem resposta pelo tempo escolhido, a tela trava.",
      "Se o Bluetooth do celular desligar ou falhar, a tela também trava. Precisa do pacote bluez (l2ping).",
      AppSettings::Get().expProximityLock, hasBtPhone, false);
#endif
  add("language", "Idioma das mensagens", "Mostra as mensagens da tela de bloqueio e os avisos de erro no idioma escolhido. As telas do app continuam em português.",
      "Algumas mensagens podem aparecer em inglês ou misturadas.", AppSettings::Get().expLanguage, true, false);
#ifdef LINUX
  for(const auto &setting : ServiceInstaller().GetSettings()) {
    if(setting.id == "sudo")
      add("sudo", "Celular no sudo", "O sudo do terminal chama o celular antes de pedir a senha.",
          "Se o celular não responder, o sudo demora até desistir e só então pede a senha. Mexe em /etc/pam.d/sudo.",
          setting.enabled, setting.defaultVal, true);
    else if(setting.id == "polkit")
      add("polkit", "Celular nas janelas de senha de administrador", "Janelas que pedem a senha para instalar programas ou mudar o sistema chamam o celular.",
          "Afrouxa proteções do serviço das janelas de senha de administrador para ele falar com o celular.", setting.enabled,
          setting.defaultVal, true);
    else if(setting.id == "loginManager")
      add("loginManager", "Celular na tela de login", "A tela de login ao ligar o computador (LightDM, GDM ou SDDM) chama o celular.",
          "Entrando pelo celular, o chaveiro de senhas pode não abrir sozinho. Mexe no PAM da tela de login.", setting.enabled,
          setting.defaultVal, true);
    else if(setting.id == "ssh")
      add("ssh", "Senhas de SSH pelo celular",
          "Ao conectar deste computador em outro servidor por SSH, o celular entrega a senha guardada. Guarde com: italock-ssh adicionar usuario@servidor",
          "Vale depois de sair e entrar de novo. Se o auxiliar for apagado à mão, o ssh deixa de pedir senha até desligar aqui.", setting.enabled,
          setting.defaultVal, true);
  }
#endif
  return result;
}

QString SettingsForm::SetExperimental(const QString &id, bool enabled) {
  if(id == "proximity") {
#ifdef WINDOWS
    SetDynamicLock(enabled);
    if(IsDynamicLockOn() != enabled)
      return "Não deu para mudar o Bloqueio dinâmico do Windows.";
#else
    auto storage = AppSettings::Get();
    storage.expProximityLock = enabled;
    AppSettings::Save(storage);
    AppSettings::InvalidateCache();
#endif
    return {};
  }
  if(id == "language") {
    auto storage = AppSettings::Get();
    storage.expLanguage = enabled;
    AppSettings::Save(storage);
    AppSettings::InvalidateCache();
    return {};
  }
#ifdef LINUX
  try {
    auto installer = ServiceInstaller();
    for(auto setting : installer.GetSettings()) {
      if(setting.id != id.toStdString())
        continue;
      setting.enabled = enabled;
      installer.ApplySettings({setting}, false);
      return {};
    }
  } catch(const std::exception &ex) {
    spdlog::error("Recurso experimental {}: {}", id.toStdString(), ex.what());
    return QString::fromUtf8(ex.what());
  }
#endif
  return "Recurso não disponível neste sistema.";
}

QString SettingsForm::GetLanguage() {
  return QString::fromUtf8(AppSettings::Get().language);
}

void SettingsForm::SetLanguage(const QString &lang) {
  auto storage = AppSettings::Get();
  storage.language = lang.toStdString();
  AppSettings::Save(storage);
  AppSettings::InvalidateCache();
}

QVariantMap SettingsForm::GetOptions() {
  AppSettings::InvalidateCache();
  auto s = AppSettings::Get();
  QVariantMap m{};
  m["winUnlockBehavior"] = QString::fromStdString(s.winUnlockBehavior);
  m["unlockWaitSeconds"] = static_cast<int>(s.unlockWaitSeconds);
  m["bootAutoRetry"] = s.bootAutoRetry;
  m["allowLan"] = s.allowLan;
  m["allowBluetooth"] = s.allowBluetooth;
  m["allowTailscale"] = s.allowTailscale;
  m["agentLockEnabled"] = s.agentLockEnabled;
  m["proximityMinutes"] = static_cast<int>(s.proximityMinutes);
  return m;
}

QString SettingsForm::SetOption(const QString &key, const QVariant &value) {
  AppSettings::InvalidateCache();
  auto s = AppSettings::Get();
  if(key == "winUnlockBehavior") {
    static const QStringList allowed{"key_press_lock_only", "foreground_always", "wake_only", "key_press", "foreground_lock_only", "none"};
    if(!allowed.contains(value.toString()))
      return "Opção inválida.";
    s.winUnlockBehavior = value.toString().toStdString();
  } else if(key == "unlockWaitSeconds") {
    auto v = value.toInt();
    if(v < 5 || v > 300)
      return "Tempo inválido.";
    s.unlockWaitSeconds = static_cast<uint32_t>(v);
  } else if(key == "bootAutoRetry") {
    s.bootAutoRetry = value.toBool();
  } else if(key == "allowLan" || key == "allowBluetooth" || key == "allowTailscale") {
    bool on = value.toBool();
    bool lan = key == "allowLan" ? on : s.allowLan;
    bool bt = key == "allowBluetooth" ? on : s.allowBluetooth;
    bool ts = key == "allowTailscale" ? on : s.allowTailscale;
    if(!lan && !bt && !ts)
      return "Deixe pelo menos um caminho ligado: sem nenhum, o celular não alcança este computador.";
    s.allowLan = lan;
    s.allowBluetooth = bt;
    s.allowTailscale = ts;
  } else if(key == "agentLockEnabled") {
    s.agentLockEnabled = value.toBool();
  } else if(key == "proximityMinutes") {
    auto v = value.toInt();
    if(v < 1 || v > 60)
      return "Tempo inválido.";
    s.proximityMinutes = static_cast<uint32_t>(v);
  } else {
    return "Opção desconhecida.";
  }
  AppSettings::Save(s);
  AppSettings::InvalidateCache();
  auto check = AppSettings::Get();
  if(check.unlockWaitSeconds != s.unlockWaitSeconds || check.winUnlockBehavior != s.winUnlockBehavior || check.allowLan != s.allowLan ||
     check.allowBluetooth != s.allowBluetooth || check.allowTailscale != s.allowTailscale || check.agentLockEnabled != s.agentLockEnabled ||
     check.bootAutoRetry != s.bootAutoRetry || check.proximityMinutes != s.proximityMinutes)
    return "Não deu para gravar as configurações.";
  return {};
}

bool SettingsForm::IsTrayAutostartEnabled() {
#ifdef WINDOWS
  auto result = Shell::RunCommand(R"(schtasks /Query /TN "Italock Bandeja" /FO CSV /NH)");
  return result.exitCode == 0 && result.output.find("Desabilitad") == std::string::npos &&
         result.output.find("Disabled") == std::string::npos;
#else
  return false;
#endif
}

bool SettingsForm::SetTrayAutostartEnabled(bool enabled) {
#ifdef WINDOWS
  return Shell::RunCommand(fmt::format(R"(schtasks /Change /TN "Italock Bandeja" /{})", enabled ? "ENABLE" : "DISABLE")).exitCode == 0;
#else
  (void)enabled;
  return false;
#endif
}
