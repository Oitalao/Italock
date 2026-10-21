#include "UnlockHandler.h"

#include "KeyScanner.h"
#include "connection/unlock/clients/BTUnlockClient.h"
#include "connection/unlock/clients/TCPUnlockClient.h"
#include "connection/unlock/servers/TCPUnlockServer.h"
#include "storage/AppSettings.h"

#ifdef WINDOWS
#include <Windows.h>
#define KEY_LEFTCTRL VK_LCONTROL
#define KEY_LEFTALT VK_LMENU
#elif LINUX
#include <linux/input.h>
#elif APPLE
#include <Carbon/Carbon.h>
#define KEY_LEFTCTRL kVK_Control
#define KEY_LEFTALT kVK_Option
#endif

// Italock: IPs do Tailscale (100.64.0.0/10) contam como caminho "Tailscale"; o resto como "rede local".
static bool IsTailscaleAddress(const std::string &ip) {
  unsigned a = 0, b = 0;
  if(sscanf(ip.c_str(), "%u.%u", &a, &b) != 2)
    return false;
  return a == 100 && b >= 64 && b <= 127;
}

UnlockHandler::UnlockHandler(const std::function<void(std::string)> &printMessage) {
  m_PrintMessage = printMessage;
}

UnlockResult UnlockHandler::GetResult(const std::string &authUser, const std::string &authProgram, std::atomic<bool> *isRunning,
                                      bool skipBluetooth, bool automatic) {
  auto settings = AppSettings::Get();
  auto devices = PairedDevicesStorage::GetDevicesForUser(authUser);
  auto hasTCPServer = false;

  UDPUnlockBroadcaster *udpBroadcaster{};
  std::vector<BaseUnlockConnection *> connections{};
  // Italock: um pareamento usa TODOS os caminhos permitidos ao mesmo tempo:
  // broadcast na rede local/cabo, unicast para os IPs do celular (Tailscale), Bluetooth e TCP.
  // Caminhos desligados em Configuracoes, Conexao (allowLan/allowBluetooth/allowTailscale) ficam de fora.
  const bool allowLan = settings.allowLan;
  const bool allowBt = settings.allowBluetooth && !skipBluetooth;
  const bool allowTailscale = settings.allowTailscale;
  bool blockedByPolicy = false;
  for(const auto &device : devices) {
    auto addConnection = [&](BaseUnlockConnection *connection) {
      connection->SetUnlockInfo(authUser, authProgram);
      connection->SetAutomatic(automatic);
      connections.emplace_back(connection);
    };
    auto isManual = device.pairingMethod == PairingMethod::MANUAL_UDP;
    auto udpPort = isManual ? device.udpManualPort : device.udpPort;
    if(udpPort != 0 && device.pairingMethod != PairingMethod::TCP) {
      std::vector<std::string> unicastIPs{};
      for(const auto &ip : device.ipAddresses) {
        if(IsTailscaleAddress(ip) ? allowTailscale : allowLan)
          unicastIPs.emplace_back(ip);
        else
          blockedByPolicy = true;
      }
      if(allowLan || !unicastIPs.empty()) {
        if(udpBroadcaster == nullptr) {
          udpBroadcaster = new UDPUnlockBroadcaster();
          udpBroadcaster->SetBroadcastEnabled(allowLan);
        }
        udpBroadcaster->AddDevice(device.id, udpPort, isManual, unicastIPs);
        hasTCPServer = true;
      } else {
        blockedByPolicy = true;
      }
    }
    if(!device.bluetoothAddress.empty()) {
      if(allowBt)
        addConnection(new BTUnlockClient(device.bluetoothAddress, device));
      else if(!settings.allowBluetooth)
        blockedByPolicy = true;
    }
    if(device.pairingMethod == PairingMethod::TCP) {
      if(IsTailscaleAddress(device.ipAddress) ? allowTailscale : allowLan)
        addConnection(new TCPUnlockClient(device.ipAddress, device.tcpPort, device));
      else
        blockedByPolicy = true;
    }
    if(device.pairingMethod == PairingMethod::CLOUD_TCP)
      hasTCPServer = true;
  }
  if(hasTCPServer) {
    auto server = new TCPUnlockServer();
    server->SetUnlockInfo(authUser, authProgram);
    server->SetAutomatic(automatic);
    connections.emplace_back(server);
  }
  if(connections.empty() && blockedByPolicy && !devices.empty()) {
    auto errorMsg = UnlockStateUtils::ToString(UnlockState::NO_PATH_ERROR);
    spdlog::error("Nenhum caminho de conexao permitido para {}.", authUser);
    m_PrintMessage(errorMsg);
    return UnlockResult(UnlockState::NO_PATH_ERROR);
  }
  if(connections.empty()) {
    auto errorMsg = I18n::Get("error_not_paired", authUser);
    spdlog::error(errorMsg);
    m_PrintMessage(errorMsg);
    return UnlockResult(UnlockState::NOT_PAIRED_ERROR);
  }

  // Start servers
  std::vector<std::thread> threads{};
  AtomicUnlockResult currentResult{};
  AtomicUnlockResult refusedResult{}; // Italock: recusa no celular (vale mais que o erro de outro caminho que terminou depois)
  AtomicUnlockResult deferredResult{}; // Italock: pedido automatico guardado no celular (idem)
  std::atomic<bool> anyConnected{false};
  std::atomic completed(0);
  std::mutex mutex{};
  std::condition_variable cv{};
  auto numServers = connections.size();
  threads.reserve(numServers);
  for(auto connection : connections) {
    threads.emplace_back([this, connection, numServers, isRunning, &currentResult, &refusedResult, &deferredResult, &anyConnected, &completed,
                          &cv, &mutex, udpBroadcaster]() {
      auto serverResult = RunServer(connection, udpBroadcaster, &currentResult, isRunning);
      if(serverResult.phoneConnected)
        anyConnected = true;
      if(serverResult.refusedByPhone)
        refusedResult.store(serverResult);
      if(serverResult.deferredByPhone)
        deferredResult.store(serverResult);
      // SUCCESS e guardado ANTES de contar esta thread como terminada: a ultima a terminar ve todos os SUCCESS e nao
      // sobrescreve um deles com o proprio erro. Contagem e aviso sob a trava (ninguem perde o notify).
      if(serverResult.state == UnlockState::SUCCESS)
        currentResult.store(serverResult);
      std::lock_guard l(mutex);
      if(static_cast<size_t>(completed.fetch_add(1) + 1) == numServers) {
        if(currentResult.load().state != UnlockState::SUCCESS)
          currentResult.store(serverResult);
        cv.notify_one();
      }
    });
  }

  // UDP Broadcast
  if(udpBroadcaster) {
    udpBroadcaster->Start();
  }

  // Wait
  std::unique_lock lock(mutex);
  cv.wait(lock, [&] { return completed.load() == numServers; });
  auto result = currentResult.load();
  if(result.state != UnlockState::SUCCESS && refusedResult.load().refusedByPhone)
    result = refusedResult.load();
  else if(result.state != UnlockState::SUCCESS && deferredResult.load().deferredByPhone)
    result = deferredResult.load();
  result.phoneConnected = result.phoneConnected || anyConnected;

  // Cleanup
  if(udpBroadcaster) {
    udpBroadcaster->Stop();
    delete udpBroadcaster;
  }
  for(auto &thread : threads) {
    if(thread.joinable())
      thread.join();
  }
  for(const auto connection : connections)
    delete connection;
  if(m_RecordHistory) {
    // "ultimo uso" na lista do app, aviso da bandeja e historico. Tentativas automaticas sem resposta (tela de entrada
    // do boot) nao entram no historico: encheriam os 50 eventos a toa.
    if(result.state == UnlockState::SUCCESS && !result.device.id.empty())
      PairedDevicesStorage::RecordUse(result.device.id, "destravar", result.path, authProgram);
    else if(result.state == UnlockState::CANCELED && result.refusedByPhone)
      PairedDevicesStorage::RecordEvent(result.device.id, "destravar", "recusado", result.path, authProgram);
    else if(result.state == UnlockState::TIMEOUT && result.phoneConnected)
      PairedDevicesStorage::RecordEvent(result.device.id, "destravar", "expirou", result.path, authProgram);
    else if(result.state == UnlockState::TIMEOUT && !skipBluetooth)
      PairedDevicesStorage::RecordEvent({}, "destravar", "sem_resposta", {}, authProgram);
  }
  return result;
}

UnlockResult UnlockHandler::RunServer(BaseUnlockConnection *connection, UDPUnlockBroadcaster *udpBroadcaster, AtomicUnlockResult *currentResult,
                                      std::atomic<bool> *isRunning) {
  if(!connection->Start()) {
    auto errorMsg = I18n::Get("error_start_handler");
    spdlog::error(errorMsg);
    m_PrintMessage(errorMsg);
    return UnlockResult(UnlockState::START_ERROR);
  }

  auto connectMessage = I18n::Get(connection->IsServer() ? "wait_server_phone_connect" : "wait_client_phone_connect");
  m_PrintMessage(connectMessage);
  auto keyScanner = KeyScanner();
  keyScanner.Start();

  auto state = UnlockState::UNKNOWN;
  auto startTime = Utils::GetCurrentTimeMs();
  // Italock: se o celular nem se conectar nesse tempo, desiste e libera a senha (Configuracoes: 10, 20 ou 40 s).
  const long long noPhoneTimeoutMs = static_cast<long long>(AppSettings::Get().unlockWaitSeconds) * 1000;
  auto isWaitingForConnection = true;
  auto isFutureCancel = false;
  bool everConnected = false;
  while(true) {
    if(currentResult->load().state == UnlockState::SUCCESS || (isRunning != nullptr && !isRunning->load())) {
      state = UnlockState::CANCELED;
      isFutureCancel = true;
      break;
    }
    if(connection->HasClient())
      everConnected = true;
    if(connection->HasClient() && isWaitingForConnection) {
      m_PrintMessage(I18n::Get("wait_phone_unlock"));
      isWaitingForConnection = false;
      if(udpBroadcaster) {
        udpBroadcaster->Stop();
      }
    }

    state = connection->PollResult();
    if(state != UnlockState::UNKNOWN)
      break;
    // Depois de conectado, o tempo para a biometria continua o do socket.
    if(!connection->HasClient() && Utils::GetCurrentTimeMs() - startTime > noPhoneTimeoutMs) {
      state = UnlockState::TIMEOUT;
      break;
    }
    if(keyScanner.GetKeyState(KEY_LEFTCTRL) && keyScanner.GetKeyState(KEY_LEFTALT)) {
      state = UnlockState::CANCELED;
      break;
    }

    if(!connection->HasClient() && !isWaitingForConnection) {
      m_PrintMessage(connectMessage);
      isWaitingForConnection = true;
      if(udpBroadcaster) {
        udpBroadcaster->Start();
      }
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
  }

  connection->Stop();
  keyScanner.Stop();
  if(!isFutureCancel)
    m_PrintMessage(UnlockStateUtils::ToString(state));
  spdlog::info("Connection result: {}", UnlockStateUtils::ToString(state));

  auto pwDec = CryptUtils::DecryptAES(connection->GetDevice().passwordEnc, connection->GetResponseData().passwordKey);
  if(!pwDec.has_value() && state == UnlockState::SUCCESS) {
    auto errorMsg = I18n::Get("error_password_decrypt");
    spdlog::error(errorMsg);
    m_PrintMessage(errorMsg);
    return UnlockResult(UnlockState::DATA_ERROR);
  }

  auto result = UnlockResult();
  result.state = state;
  result.device = connection->GetDevice();
  result.password = pwDec.has_value() ? pwDec.value() : "";
  result.path = connection->GetPath();
  result.refusedByPhone = !isFutureCancel && connection->PollResult() == UnlockState::CANCELED;
  result.deferredByPhone = !isFutureCancel && connection->PollResult() == UnlockState::DEFERRED;
  result.phoneConnected = everConnected;
  return result;
}
