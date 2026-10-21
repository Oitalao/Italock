#ifndef PCBU_DESKTOP_PACKETS_H
#define PCBU_DESKTOP_PACKETS_H

#include <cstdint>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "storage/PairingMethod.h"

constexpr uint64_t PACKET_HEADER = 0xDB065AC7AFDFA4CC;

constexpr uint16_t PACKET_ID_PAIR_INIT = 0x50;
constexpr uint16_t PACKET_ID_PAIR_RESPONSE = 0x51;

constexpr uint16_t PACKET_ID_DEVICE_ID = 0xB0;
constexpr uint16_t PACKET_ID_UNLOCK_REQUEST = 0xB1;
constexpr uint16_t PACKET_ID_UNLOCK_RESPONSE = 0xB2;

// Italock: travar a tela a pedido do celular
constexpr uint16_t PACKET_ID_LOCK_REQUEST = 0xC0;
constexpr uint16_t PACKET_ID_LOCK_RESPONSE = 0xC1;

struct PacketPairInit { // From phone
  std::string protoVersion{};
  std::string deviceUUID{};
  std::string deviceName{};
  std::string ipAddress{};
  uint16_t tcpPort{};
  uint16_t udpPort{};
  uint16_t udpManualPort{};
  std::string cloudToken{};
  std::vector<std::string> ipAddresses{}; // Italock: todos os IPs do celular (inclui Tailscale)

  static std::optional<PacketPairInit> FromJson(const std::string &jsonStr) {
    try {
      auto json = nlohmann::json::parse(jsonStr);
      auto packet = PacketPairInit();
      packet.protoVersion = json["protoVersion"];
      try {
        packet.deviceUUID = json["deviceUUID"];
        packet.deviceName = json["deviceName"];
        packet.ipAddress = json["ipAddress"];
        packet.tcpPort = json["tcpPort"];
        packet.udpPort = json["udpPort"];
        packet.udpManualPort = json["udpManualPort"];
        packet.cloudToken = json["cloudToken"];
      } catch(...) {
      }
      try {
        if(json.contains("ipAddresses") && json["ipAddresses"].is_array())
          for(const auto &ip : json["ipAddresses"])
            if(ip.is_string())
              packet.ipAddresses.emplace_back(ip.get<std::string>());
      } catch(...) {
      }
      return packet;
    } catch(...) {
    }
    return {};
  }
};

struct PacketPairResponseData { // From PC
  PairingMethod pairingMethod{};
  std::string deviceId{};
  std::string deviceName{};
  std::string deviceOS{};
  uint16_t unlockServerPort{};
  std::vector<std::string> macAddresses{};
  std::string userName{};
  std::string passwordKey{};
  std::vector<std::string> ipAddresses{}; // Italock: IPs do PC

  nlohmann::json ToJson() {
    return {{"ipAddresses", ipAddresses},
            {"pairingMethod", PairingMethodUtils::ToString(pairingMethod)},
            {"deviceId", deviceId},
            {"deviceName", deviceName},
            {"deviceOS", deviceOS},
            {"unlockServerPort", unlockServerPort},
            {"macAddresses", macAddresses},
            {"userName", userName},
            {"passwordKey", passwordKey}};
  }
};

struct PacketPairResponse { // From PC
  std::string errMsg{};
  PacketPairResponseData data{};

  nlohmann::json ToJson() {
    return {{"errMsg", errMsg}, {"data", data.ToJson()}};
  }
};

struct PacketUnlockRequest {
  std::string protoVersion;
  std::string deviceId;
  std::string encData;

  nlohmann::json ToJson() {
    return {{"protoVersion", protoVersion}, {"deviceId", deviceId}, {"encData", encData}};
  }
};

struct PacketUnlockRequestData {
  std::string user;
  std::string program;
  std::string unlockToken;
  // Italock: pedido que o PC fez sozinho (ninguem apertou tecla nem tocou em Destravar), por exemplo na tela de entrada
  // do boot. O celular novo nao mostra o pedido com alerta: deixa um aviso silencioso "Destravar <PC>?" e responde
  // "WAITING". Celular antigo ignora o campo (o JSON so ganha a chave quando e true).
  bool automatic{};

  nlohmann::json ToJson() {
    nlohmann::json j = {{"user", user}, {"program", program}, {"unlockToken", unlockToken}};
    if(automatic)
      j["auto"] = true;
    return j;
  }
};

struct PacketUnlockResponse {
  std::string error;
  std::string encData;

  static std::optional<PacketUnlockResponse> FromJson(const std::string &jsonStr) {
    try {
      auto json = nlohmann::json::parse(jsonStr);
      auto packet = PacketUnlockResponse();
      packet.error = json["error"];
      packet.encData = json["encData"];
      return packet;
    } catch(...) {
    }
    return {};
  }
};

struct PacketUnlockResponseData {
  std::string unlockToken;
  std::string passwordKey;

  static std::optional<PacketUnlockResponseData> FromJson(const std::string &jsonStr) {
    try {
      auto json = nlohmann::json::parse(jsonStr);
      auto packet = PacketUnlockResponseData();
      packet.unlockToken = json["unlockToken"];
      packet.passwordKey = json["passwordKey"];
      return packet;
    } catch(...) {
    }
    return {};
  }
};

struct PacketUDPBroadcast {
  std::string deviceId;
  std::string pcbuIP;
  uint16_t pcbuPort;
  bool isManual;

  nlohmann::json ToJson() {
    return {{"deviceId", deviceId}, {"pcbuIP", pcbuIP}, {"pcbuPort", pcbuPort}, {"isManual", isManual}};
  }
};

struct PacketUDPPairBeacon {
  std::string serverId;
  std::string ip;
  uint16_t port;

  nlohmann::json ToJson() {
    return {{"serverId", serverId}, {"ip", ip}, {"port", port}};
  }
};

#endif // PCBU_DESKTOP_PACKETS_H
