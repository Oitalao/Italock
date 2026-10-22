#include "PairingServer.h"

#include "platform/NetworkHelper.h"
#include "platform/PlatformHelper.h"
#include "storage/AppSettings.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/AppInfo.h"
#include "utils/CryptUtils.h"
#include "utils/I18n.h"
#include "utils/StringUtils.h"

#ifdef WINDOWS
#include <Ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <netinet/tcp.h>
#endif

constexpr int MAX_CLIENTS = 10;

PairingServer::PairingServer(const std::function<void(const std::string &)> &errorCallback,
                             const std::function<void(const std::string &)> &successCallback) {
  m_ErrorCallback = errorCallback;
  m_SuccessCallback = successCallback;
}

PairingServer::~PairingServer() {
  Stop();
}

bool PairingServer::Start(const PairingUIData &uiData) {
  m_UIData = uiData;
  if(m_IsRunning)
    return true;

  WSA_STARTUP
  // Italock: thread anterior que terminou sozinha (porta ocupada...) e recolhida antes; atribuir uma thread nova
  // sobre outra ainda "joinable" derrubava o app (std::terminate) ao tentar parear de novo.
  if(m_AcceptThread.joinable())
    m_AcceptThread.join();
  m_IsRunning = true;
  m_AcceptThread = std::thread(&PairingServer::AcceptThread, this);
  return true;
}

// Italock: Stop so avisa e espera. Cada socket e fechado pela thread dona dele (antes o Stop fechava o socket do
// servidor de outra thread e o fim da AcceptThread fechava de novo, junto com os dos clientes que as threads deles
// tambem fechavam: descritor fechado duas vezes, que pode ja ser de outra conexao).
void PairingServer::Stop() {
  m_IsRunning = false;
  if(m_AcceptThread.joinable())
    m_AcceptThread.join();
}

void PairingServer::AcceptThread() {
  struct sockaddr_in address{};
  socklen_t addrLen = sizeof(address);
  auto settings = AppSettings::Get();
  auto clientThreads = std::vector<std::thread>();
  spdlog::info("Starting TCP pairing server...");

  if((m_ServerSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)) == SOCKET_INVALID) {
    spdlog::error("socket() failed. (Code={})", SOCKET_LAST_ERROR);
    m_ErrorCallback(I18n::Get("error_pairing_server_init_unk"));
    m_IsRunning = false;
    return;
  }

  int opt = 1;
#ifdef LINUX
  if(setsockopt(m_ServerSocket, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char *>(&opt), sizeof(opt))) {
    spdlog::error("setsockopt(SO_REUSEADDR) failed. (Code={})", SOCKET_LAST_ERROR);
    m_ErrorCallback(I18n::Get("error_pairing_server_init_unk"));
    goto threadEnd;
  }
#endif
  if(setsockopt(m_ServerSocket, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char *>(&opt), sizeof(opt))) {
    spdlog::error("setsockopt(TCP_NODELAY) failed. (Code={})", SOCKET_LAST_ERROR);
    m_ErrorCallback(I18n::Get("error_pairing_server_init_unk"));
    goto threadEnd;
  }

  address.sin_family = AF_INET;
  address.sin_addr.s_addr = INADDR_ANY;
  address.sin_port = htons(settings.pairingServerPort);
  if(bind(m_ServerSocket, reinterpret_cast<struct sockaddr *>(&address), sizeof(address)) < 0) {
    spdlog::error("bind() failed. (Code={})", SOCKET_LAST_ERROR);
    m_ErrorCallback(I18n::Get("error_pairing_server_init"));
    goto threadEnd;
  }
  if(listen(m_ServerSocket, MAX_CLIENTS) < 0) {
    spdlog::error("listen() failed. (Code={})", SOCKET_LAST_ERROR);
    m_ErrorCallback(I18n::Get("error_pairing_server_init_unk"));
    goto threadEnd;
  }
  if(!SetSocketBlocking(m_ServerSocket, false)) {
    spdlog::error("Failed setting server socket to non-blocking mode. (Code={})", SOCKET_LAST_ERROR);
    m_ErrorCallback(I18n::Get("error_pairing_server_init_unk"));
    goto threadEnd;
  }

  spdlog::info("TCP pairing server started on port '{}'.", settings.pairingServerPort);
  while(m_IsRunning) {
    if(m_NumConnections >= MAX_CLIENTS) {
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
      continue;
    }
    SOCKET clientSocket;
    if((clientSocket = accept(m_ServerSocket, reinterpret_cast<struct sockaddr *>(&address), (socklen_t *)&addrLen)) == SOCKET_INVALID) {
      auto err = SOCKET_LAST_ERROR;
      if(err == SOCKET_ERROR_TRY_AGAIN || err == SOCKET_ERROR_WOULD_BLOCK) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
        continue;
      }
      if(err != SOCKET_ERROR_CONNECT_ABORTED)
        spdlog::error("accept() failed. (Code={})", err);
      break;
    }
    if(!SetSocketBlocking(clientSocket, false)) {
      spdlog::error("Failed setting client socket to non-blocking mode. (Code={})", SOCKET_LAST_ERROR);
      m_ErrorCallback(I18n::Get("error_pairing_server_init_unk"));
      SOCKET_CLOSE(clientSocket);
      break;
    }
    ++m_NumConnections; // contado aqui: o laco nao aceita alem do limite antes da thread comecar
    clientThreads.emplace_back(&PairingServer::ClientThread, this, clientSocket);
  }

threadEnd:
  m_IsRunning = false; // as threads dos clientes saem da leitura sozinhas (socket nao bloqueante) e fecham os seus
  for(auto &thread : clientThreads)
    if(thread.joinable())
      thread.join();
  SOCKET_CLOSE(m_ServerSocket);
  spdlog::info("TCP pairing server stopped.");
}

void PairingServer::ClientThread(SOCKET clientSocket) {
  spdlog::info("TCP pairing client connected.");
  int opt = 1;
  if(setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char *>(&opt), sizeof(opt))) {
    spdlog::error("setsockopt(TCP_NODELAY) failed. (Code={})", SOCKET_LAST_ERROR);
  }
  auto packetRes = ReadEncryptedPacket(clientSocket);
  if(packetRes.data.empty()) {
    // Italock: descontava so no fim; conexao ruim (outro programa, chave errada) saia daqui sem descontar e, depois de
    // 10, o pareamento parava de aceitar conexoes ate reabrir a tela.
    --m_NumConnections;
    spdlog::info("TCP pairing client closed.");
    SOCKET_CLOSE(clientSocket);
    if(packetRes.result == PacketCryptResult::INVALID_TIMESTAMP)
      m_ErrorCallback(I18n::Get("error_aes_time_mismatch"));
    return;
  }
  try {
    auto initPacket = PacketPairInit::FromJson({packetRes.data.begin(), packetRes.data.end()});
    if(!initPacket.has_value())
      throw std::runtime_error(I18n::Get("error_pairing_packet_parse"));
    if(AppInfo::CompareVersion(AppInfo::GetPairingProtocolVersion(), initPacket->protoVersion) != 0)
      throw std::runtime_error(I18n::Get("error_protocol_mismatch"));
    if(initPacket.value().deviceUUID.empty()) {
      spdlog::warn("Device ID is empty. Generating fallback...");
      initPacket.value().deviceUUID = StringUtils::RandomString(32);
    }

    auto passwordKey = StringUtils::RandomString(64);
    auto pwEnc = CryptUtils::EncryptAES(m_UIData.password, passwordKey);
    if(!pwEnc.has_value())
      throw std::runtime_error(I18n::Get("error_password_encrypt"));

    auto device = PairedDevice();
    device.id = CryptUtils::Sha256(AppSettings::Get().machineID + initPacket->deviceUUID + m_UIData.userName);
    device.pairingMethod = m_UIData.method;
    device.deviceName = StringUtils::SafeDeviceName(initPacket->deviceName); // Italock: sem controle, invisiveis ou "-" no comeco
    device.userName = m_UIData.userName;
    device.passwordEnc = pwEnc.value();
    device.encryptionKey = m_UIData.encKey;

    device.ipAddress = initPacket->ipAddress;
    device.tcpPort = initPacket->tcpPort;
    device.udpPort = initPacket->udpPort;
    device.udpManualPort = initPacket->udpManualPort;
    device.bluetoothAddress = m_UIData.btAddress;
    device.cloudToken = initPacket->cloudToken;
    device.ipAddresses = initPacket->ipAddresses;

    auto respPacket = PacketPairResponse();
    respPacket.data = PacketPairResponseData();
    respPacket.data.deviceId = device.id;
    respPacket.data.deviceName = NetworkHelper::GetHostName();
    respPacket.data.deviceOS = AppInfo::GetOperatingSystem();
    respPacket.data.unlockServerPort = AppSettings::Get().unlockServerPort;
    respPacket.data.pairingMethod = device.pairingMethod;
    for(const auto &netIf : NetworkHelper::GetWakeOnLanInterfaces())
      respPacket.data.macAddresses.emplace_back(netIf.macAddress);
    respPacket.data.userName = m_UIData.userName;
    respPacket.data.passwordKey = passwordKey;
    for(const auto &netIf : NetworkHelper::GetLocalNetInterfaces(false))
      if(!netIf.ipAddress.empty() && !netIf.ipAddress.starts_with("127.") && !netIf.ipAddress.starts_with("169.254."))
        respPacket.data.ipAddresses.emplace_back(netIf.ipAddress);

    // Italock: grava ANTES de responder. Se nao gravar, o celular recebe o erro (e nao fica achando que pareou).
    // Se a resposta nao chegar ao celular, desfaz: volta o pareamento anterior deste mesmo celular (re-pareamento)
    // ou apaga o novo, para nao sobrar pareamento orfao que o celular nao conhece.
    auto previous = PairedDevicesStorage::GetDeviceByID(device.id);
    if(!PairedDevicesStorage::AddDevice(device)) {
      m_ErrorCallback(I18n::Tr("Este computador não conseguiu guardar o pareamento. Nada foi alterado; tente de novo."));
      throw std::runtime_error(I18n::Tr("O computador não conseguiu guardar o pareamento. Tente de novo."));
    }
    if(!WriteEncryptedPacket(clientSocket, PACKET_ID_PAIR_RESPONSE, respPacket.ToJson().dump())) {
      bool undone = previous.has_value() ? PairedDevicesStorage::AddDevice(previous.value()) : PairedDevicesStorage::RemoveDevice(device.id);
      spdlog::warn("A resposta do pareamento nao chegou ao celular (ID={}); pareamento {}.", device.id,
                   undone ? "desfeito" : "NAO desfeito (arquivo ocupado)");
      m_ErrorCallback(I18n::Tr("A resposta não chegou ao celular. Nada foi guardado; tente parear de novo."));
    } else {
      spdlog::info("Successfully paired device. (ID={}, Method={})", device.id, PairingMethodUtils::ToString(device.pairingMethod));
      if(m_SuccessCallback)
        m_SuccessCallback(device.deviceName);
#ifdef WINDOWS
      if(PlatformHelper::SetDefaultCredProv(m_UIData.userName, "{4937B1C9-EBE0-47EF-9E77-8C41E6CEFFFB}"))
        spdlog::info("Successfully changed default credential provider for user '{}'.", m_UIData.userName);
      else
        spdlog::error("Failed setting default credential provider for user '{}'.", m_UIData.userName);
#endif
    }
  } catch(const std::exception &ex) {
    spdlog::error("Pairing server exception: {}", ex.what());
    auto respPacket = PacketPairResponse();
    respPacket.errMsg = ex.what();
    if(!WriteEncryptedPacket(clientSocket, PACKET_ID_PAIR_RESPONSE, respPacket.ToJson().dump())) {
      m_ErrorCallback(ex.what());
    }
  }
  --m_NumConnections;
  SOCKET_CLOSE(clientSocket);
  spdlog::info("TCP pairing client closed.");
}

CryptPacket PairingServer::ReadEncryptedPacket(SOCKET clientSocket) const {
  auto packet = ReadPacket(clientSocket, &m_IsRunning); // Stop interrompe a espera
  if(packet.error != PacketError::NONE) {
    spdlog::error("Error reading pairing packet. (Code={})", static_cast<int>(packet.error));
    return {};
  }
  auto decRes = CryptUtils::DecryptAESPacket(packet.data, m_UIData.encKey);
  if(decRes.result != PacketCryptResult::OK)
    spdlog::error("Error decrypting pairing packet. (Code={})", static_cast<int>(decRes.result));
  return decRes;
}

bool PairingServer::WriteEncryptedPacket(SOCKET clientSocket, uint8_t packetId, const std::string &data) const {
  auto encRes = CryptUtils::EncryptAESPacket({data.begin(), data.end()}, m_UIData.encKey);
  if(encRes.result != PacketCryptResult::OK) {
    spdlog::error("Error encrypting pairing packet. (Size={}, Code={})", data.size(), static_cast<int>(encRes.result));
    return false;
  }
  auto writeRes = WritePacket(clientSocket, packetId, {encRes.data.begin(), encRes.data.end()});
  if(writeRes != PacketError::NONE) {
    spdlog::error("Error writing pairing packet. (Code={})", static_cast<int>(writeRes));
    return false;
  }
  return true;
}
