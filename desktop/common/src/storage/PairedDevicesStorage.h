#ifndef PCBU_DESKTOP_PAIREDDEVICESSTORAGE_H
#define PCBU_DESKTOP_PAIREDDEVICESSTORAGE_H

#include <cstdint>
#include <functional>
#include <map>
#include <optional>
#include <string>
#include <vector>

#include "PairingMethod.h"

struct PairedDevice {
  std::string id{};
  PairingMethod pairingMethod{};
  std::string deviceName{};
  std::string userName{};
  std::string passwordEnc{};
  std::string encryptionKey{};

  std::string ipAddress{};
  uint16_t tcpPort{};
  uint16_t udpPort{};
  uint16_t udpManualPort{};
  std::string bluetoothAddress{};
  std::string cloudToken{};
  std::vector<std::string> ipAddresses{}; // Italock
};

class PairedDevicesStorage {
public:
  static std::optional<PairedDevice> GetDeviceByID(const std::string &id);
  static std::vector<PairedDevice> GetDevicesForUser(const std::string &userName);

  // Escritores devolvem false (e nao gravam nada) se o arquivo nao pode ser lido, a trava nao veio ou a gravacao falhou.
  static bool AddDevice(const PairedDevice &device);
  static bool RemoveDevice(const std::string &id);
  static bool RemoveDevicesIf(const std::function<bool(const PairedDevice &)> &pred);
  // Italock: le, altera e grava dentro da mesma trava (sem perder alteracao de outro processo).
  static bool UpdateDevice(const std::string &id, const std::function<void(PairedDevice &)> &change);

  // So leitura: em erro devolve vazio.
  static std::vector<PairedDevice> GetDevices();
  // true = leu (ou o arquivo ainda nao existe); false = erro de leitura ou arquivo ilegivel.
  static bool LoadDevices(std::vector<PairedDevice> &devices);
  static bool SaveDevices(const std::vector<PairedDevice> &devices);

  // Italock: ultimo uso (destravar/travar) por celular, em segundos desde 1970 (arquivo separado, melhor esforco).
  // path: "rede local", "Tailscale", "Bluetooth" ou "este computador" (vazio = nao se sabe); program: quem pediu
  // (ex.: "Tela de bloqueio do Windows"). Tambem entra no historico.
  static void RecordUse(const std::string &id, const std::string &action = "destravar", const std::string &path = {},
                        const std::string &program = {});
  // So historico (nao mexe no "ultimo uso" nem no aviso da bandeja): pedido recusado, expirado, sem resposta...
  // result: "recusado", "expirou", "sem_resposta", "desligado", "falhou". id pode ser vazio (nenhum celular respondeu).
  static void RecordEvent(const std::string &id, const std::string &action, const std::string &result, const std::string &path = {},
                          const std::string &program = {});
  static std::map<std::string, int64_t> GetLastUses();

  // Historico (no maximo HISTORY_MAX eventos, do mais antigo ao mais novo). result "ok" = deu certo.
  struct UseEvent {
    int64_t ts{};
    std::string id{};
    std::string name{};
    std::string action{};
    std::string result{};
    std::string path{};
    std::string program{};
  };
  static constexpr size_t HISTORY_MAX = 50;
  static std::vector<UseEvent> GetHistory();
  static bool ClearHistory();

private:
  static constexpr std::string_view DEVICES_FILE_NAME = "paired_devices.json";
};

#endif // PCBU_DESKTOP_PAIREDDEVICESSTORAGE_H
