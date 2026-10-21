#ifndef PCBU_DESKTOP_BASECONNECTION_H
#define PCBU_DESKTOP_BASECONNECTION_H

#include <atomic>
#include <chrono>
#include <cstdint>
#include <vector>

#ifdef WINDOWS
typedef unsigned long long SOCKET;
#else
#define SOCKET int
#endif

enum class PacketError { UNKNOWN, NONE, CLOSED_CONNECTION, TIMEOUT };

struct Packet {
  PacketError error{};
  uint16_t id{};
  std::vector<uint8_t> data{};
};

class BaseConnection {
public:
  virtual ~BaseConnection() = default;
  virtual bool IsServer();

protected:
  BaseConnection() = default;

  static bool SetSocketBlocking(SOCKET socket, bool isBlocking);
  static bool SetSocketRWTimeout(SOCKET socket, uint32_t secs);

  // isRunning: em socket nao bloqueante, desiste (CLOSED_CONNECTION) quando vira false - quem e dono do socket o fecha.
  // Desiste com TIMEOUT se o pacote inteiro nao chegar em 120 s.
  static Packet ReadPacket(SOCKET socket, const std::atomic<bool> *isRunning = nullptr);
  static PacketError WritePacket(SOCKET socket, uint16_t packetId, const std::vector<uint8_t> &data);

private:
  static std::pair<PacketError, std::vector<uint8_t>> ReadData(SOCKET socket, uint32_t size, const std::atomic<bool> *isRunning,
                                                               std::chrono::steady_clock::time_point deadline);
  static PacketError WriteData(SOCKET socket, const char *data, uint32_t size);

  static PacketError GetPacketError(int result, int error);
};

#endif // PCBU_DESKTOP_BASECONNECTION_H
