#ifndef PCBU_DESKTOP_UDPUNLOCKBROADCASTER_H
#define PCBU_DESKTOP_UDPUNLOCKBROADCASTER_H

#include <cstdint>
#include <string>
#include <vector>

#include "connection/UDPBroadcaster.h"

struct UDPBroadcastDevice {
  std::string deviceID;
  uint16_t devicePort;
  bool isManual;
  std::vector<std::string> unicastIPs{}; // Italock
};

class UDPUnlockBroadcaster : public UDPBroadcaster {
public:
  UDPUnlockBroadcaster();
  ~UDPUnlockBroadcaster() override;

  void AddDevice(const std::string &deviceID, uint16_t devicePort, bool isManual, const std::vector<std::string> &unicastIPs = {});
  // Italock: false = nada de broadcast na rede local (so o unicast para os IPs dados, ex.: Tailscale)
  void SetBroadcastEnabled(bool enabled) {
    m_BroadcastEnabled = enabled;
  }

protected:
  void SendToTarget(const BroadcastTarget &target) override;
  void SendExtra() override;

private:
  uint16_t m_UnlockPort;
  std::vector<UDPBroadcastDevice> m_Devices{};
  bool m_BroadcastEnabled = true;
};

#endif // PCBU_DESKTOP_UDPUNLOCKBROADCASTER_H
