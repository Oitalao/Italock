#ifndef PAM_PCBIOUNLOCK_UNLOCKHANDLER_H
#define PAM_PCBIOUNLOCK_UNLOCKHANDLER_H

#include <functional>
#include <future>
#include <string>

#include "UnlockState.h"
#include "connection/unlock/BaseUnlockConnection.h"
#include "connection/unlock/UDPUnlockBroadcaster.h"
#include "storage/PairedDevicesStorage.h"

struct UnlockResult {
  UnlockResult() = default;
  explicit UnlockResult(UnlockState state) {
    this->state = state;
  }

  UnlockState state{};
  PairedDevice device{};
  std::string password{};
  // Italock: por onde o celular respondeu ("rede local", "Tailscale", "Bluetooth"...), se ele recusou no celular e
  // se algum celular chegou a conectar.
  std::string path{};
  bool refusedByPhone{};
  bool phoneConnected{};
  bool deferredByPhone{}; // pedido automatico guardado no celular (WAITING)
};

class AtomicUnlockResult {
public:
  UnlockResult load() const {
    std::lock_guard<std::mutex> lock(m_Mutex);
    return m_Data;
  }
  void store(const UnlockResult &data) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    m_Data = data;
  }

private:
  UnlockResult m_Data{};
  mutable std::mutex m_Mutex{};
};

class UnlockHandler {
public:
  explicit UnlockHandler(const std::function<void(std::string)> &printMessage);
  // skipBluetooth: so rede (UDP/TCP) - usado nas repeticoes automaticas da tela de entrada do boot.
  // automatic: pedido feito pelo PC sozinho (ninguem apertou tecla nem pediu pelo celular); o celular novo responde com
  // um aviso silencioso em vez do alerta (ver PacketUnlockRequestData::automatic).
  UnlockResult GetResult(const std::string &authUser, const std::string &authProgram, std::atomic<bool> *isRunning = nullptr,
                         bool skipBluetooth = false, bool automatic = false);
  // Italock: grava o resultado no historico e no "ultimo uso" (so a tela de bloqueio e o pcbu_auth; o teste do app e
  // o SSH pelo celular nao contam como destravar).
  void SetRecordHistory(bool record) {
    m_RecordHistory = record;
  }

private:
  UnlockResult RunServer(BaseUnlockConnection *connection, UDPUnlockBroadcaster *udpBroadcaster, AtomicUnlockResult *currentResult,
                         std::atomic<bool> *isRunning);
  std::function<void(std::string)> m_PrintMessage{};
  bool m_RecordHistory{};
};

#endif // PAM_PCBIOUNLOCK_UNLOCKHANDLER_H
