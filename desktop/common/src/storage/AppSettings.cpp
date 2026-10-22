#include "AppSettings.h"

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "shell/Shell.h"
#include "utils/AppInfo.h"
#include "utils/StringUtils.h"

#ifdef WINDOWS
#include <ShlObj_core.h>
#include <spdlog/fmt/xchar.h>
#endif

PCBUAppStorage AppSettings::g_Cache{};
std::mutex AppSettings::g_Mutex{};

std::filesystem::path AppSettings::GetBaseDir() {
#ifdef WINDOWS
  wchar_t szPath[MAX_PATH]{};
  if(SHGetFolderPathW(nullptr, CSIDL_COMMON_APPDATA, nullptr, 0, szPath) == S_OK)
    return fmt::format(L"{}\\Italock", szPath);
  return "C:\\ProgramData\\Italock";
#else
  return {"/etc/italock"};
#endif
}

PCBUAppStorage AppSettings::Get() {
  std::unique_lock lock(g_Mutex);
  if(!g_Cache.machineID.empty())
    return g_Cache;
  g_Cache = Load();
  return g_Cache;
}

// Italock: cada campo com valor padrao (json.value): chave faltando ou de tipo errado NAO zera mais o arquivo inteiro
// (antes, uma chave nova ausente caia no catch e gravava os padroes por cima de tudo).
static PCBUAppStorage DefaultSettings() {
  auto def = PCBUAppStorage();
  def.language = "pt_BR"; // Italock: portugues do Brasil para todo mundo, ate a pessoa escolher outro
  def.serverIP = "auto";
  def.pairingDiscoveryPort = 43297;
  def.pairingServerPort = 43295;
  def.unlockServerPort = 43296;
  def.clientSocketTimeout = 120;
  def.clientConnectTimeout = 5;
  def.clientConnectRetries = 2;
  def.winUnlockBehavior = "key_press_lock_only";
  def.winHidePasswordField = false;
  def.winForceDefaultCredProv = true;
  def.unixSetPasswordPAM = false;
  return def;
}

template<typename T> static T JsonGet(const nlohmann::json &json, const char *key, const T &fallback) {
  try {
    if(json.contains(key) && !json[key].is_null())
      return json[key].get<T>();
  } catch(...) {
  }
  return fallback;
}

PCBUAppStorage AppSettings::Load() {
  auto def = DefaultSettings();
  std::string jsonData{};
  bool exists = false;
  try {
    exists = std::filesystem::exists(GetBaseDir() / SETTINGS_FILE_NAME);
    if(exists) {
      auto bytes = Shell::ReadBytes(GetBaseDir() / SETTINGS_FILE_NAME);
      jsonData.assign(bytes.begin(), bytes.end());
    }
  } catch(...) {
  }
  nlohmann::json json = nlohmann::json::object();
  bool parsed = false;
  if(!jsonData.empty()) {
    try {
      json = nlohmann::json::parse(jsonData);
      parsed = json.is_object();
      if(!parsed)
        json = nlohmann::json::object();
    } catch(const std::exception &ex) {
      spdlog::error("Failed reading app storage: {}", ex.what());
    }
  }
  auto settings = PCBUAppStorage();
  settings.machineID = JsonGet<std::string>(json, "machineID", "");
  bool save = !exists; // primeira vez: cria o arquivo. Arquivo existente e ilegivel NAO e sobrescrito aqui.
  if(settings.machineID.empty()) {
    settings.machineID = StringUtils::RandomString(32);
    save = save || parsed;
  }
  settings.installedVersion = JsonGet<std::string>(json, "installedVersion", def.installedVersion);
  // Italock: chave nova "idioma" (a antiga "language" era "auto" por padrao e so valia com o experimental ligado).
  settings.language = JsonGet<std::string>(json, "idioma", def.language);
  settings.serverIP = JsonGet<std::string>(json, "serverIP", def.serverIP);
  settings.serverMAC = JsonGet<std::string>(json, "serverMAC", def.serverMAC);
  settings.pairingDiscoveryPort = JsonGet<uint16_t>(json, "pairingDiscoveryPort", def.pairingDiscoveryPort);
  settings.pairingServerPort = JsonGet<uint16_t>(json, "pairingServerPort", def.pairingServerPort);
  settings.unlockServerPort = JsonGet<uint16_t>(json, "unlockServerPort", def.unlockServerPort);
  settings.clientSocketTimeout = JsonGet<uint32_t>(json, "clientSocketTimeout", def.clientSocketTimeout);
  settings.clientConnectTimeout = JsonGet<uint32_t>(json, "clientConnectTimeout", def.clientConnectTimeout);
  settings.clientConnectRetries = JsonGet<uint32_t>(json, "clientConnectRetries", def.clientConnectRetries);

  settings.winUnlockBehavior = JsonGet<std::string>(json, "winUnlockBehavior", def.winUnlockBehavior);
  settings.winHidePasswordField = JsonGet<bool>(json, "winHidePasswordField", def.winHidePasswordField);
  settings.winForceDefaultCredProv = JsonGet<bool>(json, "winForceDefaultCredProv", def.winForceDefaultCredProv);
  settings.unixSetPasswordPAM = JsonGet<bool>(json, "unixSetPasswordPAM", def.unixSetPasswordPAM);

  settings.unlockWaitSeconds = JsonGet<uint32_t>(json, "unlockWaitSeconds", def.unlockWaitSeconds);
  if(settings.unlockWaitSeconds < 5 || settings.unlockWaitSeconds > 300)
    settings.unlockWaitSeconds = def.unlockWaitSeconds;
  settings.bootAutoRetry = JsonGet<bool>(json, "bootAutoRetry", def.bootAutoRetry);
  settings.allowLan = JsonGet<bool>(json, "allowLan", def.allowLan);
  settings.allowBluetooth = JsonGet<bool>(json, "allowBluetooth", def.allowBluetooth);
  settings.allowTailscale = JsonGet<bool>(json, "allowTailscale", def.allowTailscale);
  settings.agentLockEnabled = JsonGet<bool>(json, "agentLockEnabled", def.agentLockEnabled);
  settings.expProximityLock = JsonGet<bool>(json, "expProximityLock", def.expProximityLock);
  settings.proximityMinutes = JsonGet<uint32_t>(json, "proximityMinutes", def.proximityMinutes);
  if(settings.proximityMinutes < 1 || settings.proximityMinutes > 60)
    settings.proximityMinutes = def.proximityMinutes;
  if(save) {
    g_Mutex.unlock();
    Save(settings);
    g_Mutex.lock();
  }
  return settings;
}

void AppSettings::Save(const PCBUAppStorage &storage) {
  std::unique_lock lock(g_Mutex);
  try {
    nlohmann::json json = {
        {"machineID", storage.machineID},
        {"installedVersion", storage.installedVersion},
        {"idioma", storage.language},
        {"serverIP", storage.serverIP},
        {"serverMAC", storage.serverMAC},
        {"pairingDiscoveryPort", storage.pairingDiscoveryPort},
        {"pairingServerPort", storage.pairingServerPort},
        {"unlockServerPort", storage.unlockServerPort},
        {"clientSocketTimeout", storage.clientSocketTimeout},
        {"clientConnectTimeout", storage.clientConnectTimeout},
        {"clientConnectRetries", storage.clientConnectRetries},

        {"winUnlockBehavior", storage.winUnlockBehavior},
        {"winHidePasswordField", storage.winHidePasswordField},
        {"winForceDefaultCredProv", storage.winForceDefaultCredProv},
        {"unixSetPasswordPAM", storage.unixSetPasswordPAM},

        {"unlockWaitSeconds", storage.unlockWaitSeconds},
        {"bootAutoRetry", storage.bootAutoRetry},
        {"allowLan", storage.allowLan},
        {"allowBluetooth", storage.allowBluetooth},
        {"allowTailscale", storage.allowTailscale},
        {"agentLockEnabled", storage.agentLockEnabled},
        {"expProximityLock", storage.expProximityLock},
        {"proximityMinutes", storage.proximityMinutes},
    };
    auto baseDir = GetBaseDir();
    if(!std::filesystem::exists(baseDir))
      Shell::CreateDir(baseDir);
    auto jsonStr = json.dump();
    Shell::WriteBytes(baseDir / SETTINGS_FILE_NAME, {jsonStr.begin(), jsonStr.end()});
    g_Cache = storage;
  } catch(const std::exception &ex) {
    spdlog::error("Failed writing app storage: {}", ex.what());
  }
}

void AppSettings::InvalidateCache() {
  std::unique_lock lock(g_Mutex);
  g_Cache = {};
}

void AppSettings::SetInstalledVersion(bool isInstalled) {
  auto settings = Get();
  settings.installedVersion = isInstalled ? AppInfo::GetVersion() : "";
  Save(settings);
  InvalidateCache();
}
