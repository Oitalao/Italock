#ifndef PCBU_DESKTOP_APPSTORAGE_H
#define PCBU_DESKTOP_APPSTORAGE_H

#include <cstdint>
#include <filesystem>
#include <mutex>
#include <string>
#include <vector>

struct PCBUAppStorage {
  std::string machineID{};
  std::string installedVersion{};
  std::string language{}; // Italock: "pt_BR" (padrao), "auto" (idioma do sistema) ou um codigo de I18n::Languages()
  std::string serverIP{};
  std::string serverMAC{};
  uint16_t pairingDiscoveryPort{};
  uint16_t pairingServerPort{};
  uint16_t unlockServerPort{};
  uint32_t clientSocketTimeout{};
  uint32_t clientConnectTimeout{};
  uint32_t clientConnectRetries{};

  std::string winUnlockBehavior{};
  bool winHidePasswordField{};
  bool winForceDefaultCredProv{};
  bool unixSetPasswordPAM{};


  // Italock: tela de bloqueio / PAM
  // winUnlockBehavior tambem aceita "wake_only" (so pelo botao Destravar do celular; tecla nao chama o celular).
  uint32_t unlockWaitSeconds = 20; // desiste se o celular nem conectar nesse tempo (10/20/40)
  bool bootAutoRetry = true;       // Windows: na tela de entrada do boot tenta sozinho de novo (1, 2, 5, 10 min)

  // Italock: caminhos permitidos para falar com o celular (destravar, agente de travar)
  bool allowLan = true;       // rede local e cabo (ancoragem USB)
  bool allowBluetooth = true; // Bluetooth
  bool allowTailscale = true; // Tailscale (100.64.0.0/10)

  // Italock: agente (porta 43301)
  bool agentLockEnabled = true; // aceitar pedidos de travar pelo celular (status e Destravar continuam)

  // Italock: experimental "travar quando o celular se afastar" (Linux: agente, Bluetooth; Windows: Bloqueio dinamico)
  bool expProximityLock = false;
  uint32_t proximityMinutes = 2;
};

class AppSettings {
public:
  static std::filesystem::path GetBaseDir();

  static PCBUAppStorage Get();
  static void Save(const PCBUAppStorage &storage);

  static void InvalidateCache();
  static void SetInstalledVersion(bool isInstalled);

private:
  static PCBUAppStorage Load();

  static PCBUAppStorage g_Cache;
  static std::mutex g_Mutex;

  static constexpr std::string_view SETTINGS_FILE_NAME = "app_settings.json";
};

#endif // PCBU_DESKTOP_APPSTORAGE_H
