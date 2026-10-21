#ifndef PCBU_DESKTOP_UDPBROADCASTER_H
#define PCBU_DESKTOP_UDPBROADCASTER_H

#include <atomic>
#include <functional>
#include <mutex>
#include <set>
#include <cstdint>
#include <string>
#include <thread>
#include <vector>

#include "platform/NetworkHelper.h"

class UDPBroadcaster {
public:
  virtual ~UDPBroadcaster();

  void Start();
  void Stop();

protected:
  UDPBroadcaster(std::string name, int intervalMs);

  virtual void SendToTarget(const BroadcastTarget &target) = 0;
  // Italock: chamado a cada ciclo, depois dos broadcasts (envios unicast, ex.: Tailscale)
  virtual void SendExtra() {}
  void SendUnicast(const std::string &destIP, uint16_t destPort, const std::function<std::vector<uint8_t>(const std::string &localIP)> &buildPayload);
  void SendBroadcast(const BroadcastTarget &target, const std::vector<uint8_t> &payload, uint16_t destPort);

private:
  void Run();

  std::string m_Name;
  int m_IntervalMs;
  std::thread m_Thread{};
  std::atomic<bool> m_IsRunning{};
  std::mutex m_ControlMutex{};
  std::set<std::string> m_BadSources{}; // Italock: enderecos locais que falharam no bind (so a thread do Run mexe)
};

#endif // PCBU_DESKTOP_UDPBROADCASTER_H
