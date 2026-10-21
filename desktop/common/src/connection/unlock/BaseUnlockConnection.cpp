#include "BaseUnlockConnection.h"

#include "connection/SocketDefs.h"
#ifdef WINDOWS
#include <Ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <sys/select.h>
#endif

#include "utils/AppInfo.h"
#include "utils/StringUtils.h"

BaseUnlockConnection::BaseUnlockConnection() {
  m_UnlockToken = StringUtils::RandomString(64);
  m_UnlockState = UnlockState::UNKNOWN;
}

BaseUnlockConnection::BaseUnlockConnection(const PairedDevice &device) : BaseUnlockConnection() {
  m_PairedDevice = device;
}

BaseUnlockConnection::~BaseUnlockConnection() {
  if(m_AcceptThread.joinable())
    m_AcceptThread.join();
}

void BaseUnlockConnection::SetUnlockInfo(const std::string &authUser, const std::string &authProgram) {
  m_AuthUser = authUser;
  m_AuthProgram = authProgram;
}

PairedDevice BaseUnlockConnection::GetDevice() {
  return m_PairedDevice;
}

std::string BaseUnlockConnection::GetPath() {
  std::lock_guard lock(m_StateMutex);
  return m_Path;
}

// Italock: caminho da conexao, pelo endereco do outro lado. IPv4 do Tailscale (100.64.0.0/10) = "Tailscale";
// 127.x = "este computador"; outro IP = "rede local" (Wi-Fi ou cabo); o que nao e IP (RFCOMM) = "Bluetooth".
static std::string PathOfSocket(SOCKET socket) {
  sockaddr_storage addr{};
  socklen_t len = sizeof(addr);
  if(getpeername(socket, reinterpret_cast<sockaddr *>(&addr), &len) != 0)
    return {};
  if(addr.ss_family == AF_INET) {
    auto ip = ntohl(reinterpret_cast<sockaddr_in *>(&addr)->sin_addr.s_addr);
    if((ip >> 24) == 127)
      return "este computador";
    if((ip >> 24) == 100 && ((ip >> 16) & 0xFF) >= 64 && ((ip >> 16) & 0xFF) <= 127)
      return "Tailscale";
    return "rede local";
  }
  if(addr.ss_family == AF_INET6)
    return "rede local";
  return "Bluetooth";
}

PacketUnlockResponseData BaseUnlockConnection::GetResponseData() {
  return m_ResponseData;
}

bool BaseUnlockConnection::HasClient() const {
  return m_HasConnection;
}

UnlockState BaseUnlockConnection::PollResult() {
  return m_UnlockState;
}

int BaseUnlockConnection::WaitConnectSliced(SOCKET socket, uint32_t timeoutSecs) {
  const uint64_t totalMs = static_cast<uint64_t>(timeoutSecs) * 1000;
  uint64_t waitedMs = 0;
  while(m_IsRunning) {
    fd_set fdSet{};
    FD_ZERO(&fdSet);
    FD_SET(socket, &fdSet);
    fd_set exceptSet{}; // no Windows um connect que falhou aparece aqui, nao no de escrita
    FD_ZERO(&exceptSet);
    FD_SET(socket, &exceptSet);
    struct timeval slice{};
    slice.tv_usec = 200000;
    auto result = select((int)socket + 1, nullptr, &fdSet, &exceptSet, &slice);
    if(result != 0)
      return result;
    waitedMs += 200;
    if(waitedMs >= totalMs)
      return 0;
  }
  return 0;
}

void BaseUnlockConnection::PerformAuthFlow(SOCKET socket, bool needsDeviceID) {
  m_StateMutex.lock();
  m_ConnectionStates[socket] = needsDeviceID ? UnlockConnectionState::NONE : UnlockConnectionState::HAS_DEVICE_ID;
  m_StateMutex.unlock();
  if(!needsDeviceID) {
    if(SendUnlockRequest(socket)) {
      m_StateMutex.lock();
      m_ConnectionStates[socket] = UnlockConnectionState::HAS_UNLOCK_REQUEST;
      m_StateMutex.unlock();
    } else {
      return;
    }
  }

  // Italock: o estado desta conexao sai do mapa em qualquer saida (antes ficava para sempre, e o numero do socket
  // reaproveitado herdava o estado da conexao velha).
  struct StateCleanup {
    BaseUnlockConnection *self;
    SOCKET socket;
    ~StateCleanup() {
      std::lock_guard lock(self->m_StateMutex);
      self->m_ConnectionStates.erase(socket);
    }
  } stateCleanup{this, socket};
  auto isRejected = [&]() {
    std::lock_guard lock(m_StateMutex);
    return m_ConnectionStates[socket] == UnlockConnectionState::REJECTED;
  };
  auto packet = ReadPacket(socket, &m_IsRunning);
  while(packet.error == PacketError::NONE) {
    OnPacketReceived(socket, packet);
    if(isRejected())
      return; // so esta conexao acaba; o pedido segue esperando o celular certo
    packet = ReadPacket(socket, &m_IsRunning);
  }
  if(!m_IsRunning)
    return; // parado de fora (Stop): quem e dono do socket fecha
  if(m_UnlockState == UnlockState::UNKNOWN) {
    switch(packet.error) {
      case PacketError::CLOSED_CONNECTION: {
        m_UnlockState = UnlockState::CONNECT_ERROR;
        break;
      }
      case PacketError::TIMEOUT: {
        m_UnlockState = UnlockState::TIMEOUT;
        break;
      }
      default: {
        m_UnlockState = UnlockState::DATA_ERROR;
        break;
      }
    }
  }
}

void BaseUnlockConnection::OnPacketReceived(SOCKET socket, Packet &packet) {
  std::unique_lock lock(m_StateMutex);
  auto connectionState = m_ConnectionStates[socket];
  if(packet.id == PACKET_ID_DEVICE_ID && connectionState != UnlockConnectionState::NONE) {
    spdlog::error("Unexpected packet received. Got PACKET_ID_DEVICE_ID at state {}.", static_cast<int>(m_ConnectionStates[socket]));
    return;
  }
  if(packet.id == PACKET_ID_UNLOCK_RESPONSE && connectionState != UnlockConnectionState::HAS_UNLOCK_REQUEST) {
    spdlog::error("Unexpected packet received. Got PACKET_ID_UNLOCK_RESPONSE at state {}.", static_cast<int>(m_ConnectionStates[socket]));
    return;
  }

  switch(packet.id) {
    case PACKET_ID_DEVICE_ID: {
      // Italock: so vale celular pareado com a MESMA conta que esta pedindo. Antes, qualquer celular pareado neste PC
      // (de outra conta) recebia o pedido e, aprovando, a senha dele era usada no lugar da certa; e um ID desconhecido
      // (qualquer um na rede) derrubava o pedido inteiro com DATA_ERROR. Agora so esta conexao e recusada.
      auto deviceId = std::string(reinterpret_cast<const char *>(packet.data.data()), packet.data.size());
      std::optional<PairedDevice> device{};
      for(const auto &d : PairedDevicesStorage::GetDevicesForUser(m_AuthUser))
        if(d.id == deviceId)
          device = d;
      if(!device.has_value()) {
        spdlog::warn("Celular desconhecido ou de outra conta conectou; conexao recusada.");
        m_ConnectionStates[socket] = UnlockConnectionState::REJECTED;
        return;
      }
      m_PairedDevice = device.value();
      if(SendUnlockRequest(socket)) {
        m_ConnectionStates[socket] = UnlockConnectionState::HAS_UNLOCK_REQUEST;
      }
      break;
    }
    case PACKET_ID_UNLOCK_RESPONSE: {
      m_Path = PathOfSocket(socket);
      OnResponseReceived(packet);
      break;
    }
    default: {
      spdlog::error("Invalid response packet. (ID={0:X}, State={1})", packet.id, static_cast<int>(m_ConnectionStates[socket]));
      break;
    }
  }
}

bool BaseUnlockConnection::SendUnlockRequest(SOCKET socket) {
  auto encData = PacketUnlockRequestData();
  encData.user = m_AuthUser;
  encData.program = m_AuthProgram;
  encData.unlockToken = m_UnlockToken;
  encData.automatic = m_Automatic;
  auto encDataStr = encData.ToJson().dump();
  auto cryptResult = CryptUtils::EncryptAESPacket({encDataStr.begin(), encDataStr.end()}, m_PairedDevice.encryptionKey);
  if(cryptResult.result != PacketCryptResult::OK) {
    spdlog::error("Failed to encrypt unlock request packet.");
    m_UnlockState = UnlockState::UNK_ERROR;
    return false;
  }
  auto requestPacket = PacketUnlockRequest();
  requestPacket.protoVersion = AppInfo::GetUnlockProtocolVersion();
  requestPacket.deviceId = m_PairedDevice.id;
  requestPacket.encData = StringUtils::ToHexString(cryptResult.data);
  auto requestStr = requestPacket.ToJson().dump();
  spdlog::debug("Writing PacketUnlockRequest...");
  auto writeResult = WritePacket(socket, PACKET_ID_UNLOCK_REQUEST, {requestStr.begin(), requestStr.end()});
  if(writeResult != PacketError::NONE) {
    switch(writeResult) {
      case PacketError::CLOSED_CONNECTION:
        m_UnlockState = UnlockState::CONNECT_ERROR;
        break;
      case PacketError::TIMEOUT:
        m_UnlockState = UnlockState::TIMEOUT;
        break;
      default:
        m_UnlockState = UnlockState::UNK_ERROR;
        break;
    }
    spdlog::error("Failed to write unlock request packet. (WriteResult={}, UnlockState={})", static_cast<int>(writeResult), UnlockStateUtils::ToString(m_UnlockState));
    return false;
  }
  return true;
}

void BaseUnlockConnection::OnResponseReceived(const Packet &packet) {
  // Parse data
  auto respStr = std::string(packet.data.begin(), packet.data.end());
  auto responsePacket = PacketUnlockResponse::FromJson(respStr);
  if(!responsePacket.has_value()) {
    spdlog::error("Error parsing response packet.");
    m_UnlockState = UnlockState::DATA_ERROR;
    return;
  }

  // Error handling
  auto error = responsePacket.value().error;
  if(!error.empty()) {
    spdlog::error("Error in response packet: {}", error);
    if(error == "CANCEL") {
      m_UnlockState = UnlockState::CANCELED;
    } else if(error == "WAITING") {
      // Italock: pedido automatico; o celular deixou o aviso para destravar depois (nao e recusa).
      m_UnlockState = UnlockState::DEFERRED;
    } else if(error == "NOT_PAIRED") {
      m_UnlockState = UnlockState::NOT_PAIRED_ERROR;
    } else if(error == "APP_ERROR") {
      m_UnlockState = UnlockState::APP_ERROR;
    } else if(error == "TIME_ERROR") {
      m_UnlockState = UnlockState::TIME_ERROR;
    } else if(error == "DATA_ERROR") {
      m_UnlockState = UnlockState::DATA_ERROR;
    } else if(error == "PROTOCOL_ERROR") {
      m_UnlockState = UnlockState::PROTOCOL_ERROR;
    } else {
      m_UnlockState = UnlockState::UNK_ERROR;
    }
    return;
  }

  // Decrypt data
  auto cryptData = StringUtils::FromHexString(responsePacket.value().encData);
  auto cryptResult = CryptUtils::DecryptAESPacket(cryptData, m_PairedDevice.encryptionKey);
  if(cryptResult.result != PacketCryptResult::OK) {
    switch(cryptResult.result) {
      case INVALID_TIMESTAMP: {
        spdlog::error("Invalid timestamp on AES data.");
        m_UnlockState = UnlockState::TIME_ERROR;
        break;
      }
      default: {
        spdlog::error("Invalid data received. (Size={})", packet.data.size());
        m_UnlockState = UnlockState::DATA_ERROR;
        break;
      }
    }
    return;
  }

  // Parse encrypted data
  auto dataStr = std::string(cryptResult.data.begin(), cryptResult.data.end());
  auto dataPacket = PacketUnlockResponseData::FromJson(dataStr);
  if(!dataPacket.has_value()) {
    spdlog::error("Error parsing response data.");
    m_UnlockState = UnlockState::DATA_ERROR;
    return;
  }
  m_ResponseData = dataPacket.value();

  // Token check
  if(m_ResponseData.unlockToken == m_UnlockToken) {
    m_UnlockState = UnlockState::SUCCESS;
  } else {
    m_UnlockState = UnlockState::UNK_ERROR;
  }
}
