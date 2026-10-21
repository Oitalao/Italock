#include "UDPBroadcaster.h"

#include <chrono>

#include <boost/asio.hpp>
#include <spdlog/spdlog.h>

#ifdef WINDOWS
#include <WinSock2.h>
#include <iphlpapi.h>
#endif

UDPBroadcaster::UDPBroadcaster(std::string name, int intervalMs) : m_Name(std::move(name)), m_IntervalMs(intervalMs) {}

UDPBroadcaster::~UDPBroadcaster() {
  Stop();
}

// Italock: Start/Stop sao chamados por varias threads ao mesmo tempo (uma por caminho de conexao no UnlockHandler).
// Sem a trava, um Start entre o "false" e o join do Stop atribuia uma thread nova sobre outra ainda "joinable" -
// std::terminate dentro da LogonUI.
void UDPBroadcaster::Start() {
  std::lock_guard lock(m_ControlMutex);
  if(m_IsRunning.load())
    return;
  if(m_Thread.joinable())
    m_Thread.join();
  m_IsRunning = true;
  m_Thread = std::thread([this]() { Run(); });
}

void UDPBroadcaster::Stop() {
  std::lock_guard lock(m_ControlMutex);
  m_IsRunning = false;
  if(m_Thread.joinable())
    m_Thread.join();
}

void UDPBroadcaster::Run() {
  spdlog::info("{} started.", m_Name);
  m_BadSources.clear();
  auto broadcastTargets = NetworkHelper::GetBroadcastTargets();
  // Italock: reenumera os enderecos quando a rede muda. Windows avisa (NotifyAddrChange, custo zero parado);
  // no Linux reenumera a cada 10 s (getifaddrs e barato e o broadcaster so vive durante uma tentativa).
#ifdef WINDOWS
  // No heap: se o cancelamento nao terminar a tempo, o sistema ainda pode escrever nele - entao fica vazado de proposito.
  auto *addrChangePtr = new OVERLAPPED{};
  auto &addrChange = *addrChangePtr;
  addrChange.hEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
  HANDLE notifyHandle = nullptr;
  bool notifying = addrChange.hEvent && NotifyAddrChange(&notifyHandle, &addrChange) == ERROR_IO_PENDING;
#endif
  auto lastEnum = std::chrono::steady_clock::now();
  auto lastSend = std::chrono::steady_clock::now() - std::chrono::milliseconds(m_IntervalMs);
  while(m_IsRunning.load()) {
    auto now = std::chrono::steady_clock::now();
    if(std::chrono::duration_cast<std::chrono::milliseconds>(now - lastSend).count() < m_IntervalMs) {
      std::this_thread::sleep_for(std::chrono::milliseconds(20));
      continue;
    }
    lastSend = now;

    bool networkChanged = false;
#ifdef WINDOWS
    if(notifying && WaitForSingleObject(addrChange.hEvent, 0) == WAIT_OBJECT_0) {
      networkChanged = true;
      ResetEvent(addrChange.hEvent);
      notifying = NotifyAddrChange(&notifyHandle, &addrChange) == ERROR_IO_PENDING;
    }
#else
    networkChanged = now - lastEnum >= std::chrono::seconds(10);
#endif
    if(networkChanged) {
      lastEnum = now;
      m_BadSources.clear();
      broadcastTargets = NetworkHelper::GetBroadcastTargets();
    }

    try {
      for(const auto &target : broadcastTargets)
        SendToTarget(target);
      SendExtra();
    } catch(const std::exception &ex) {
      spdlog::error("{}: Unexpected error while broadcasting: {}", m_Name, ex.what());
    }
    // Endereco que nao aceitou bind sai da lista (ate a rede mudar) em vez de falhar a cada 2 s.
    if(!m_BadSources.empty())
      std::erase_if(broadcastTargets, [this](const BroadcastTarget &t) { return m_BadSources.contains(t.sourceIP); });
  }
#ifdef WINDOWS
  // Cancela e ESPERA o cancelamento concluir antes de liberar o OVERLAPPED e o evento.
  bool canFree = true;
  if(notifying && CancelIPChangeNotify(&addrChange)) {
    DWORD bytes = 0;
    if(!GetOverlappedResultEx(notifyHandle, &addrChange, &bytes, 3000, FALSE) && GetLastError() == WAIT_TIMEOUT) {
      spdlog::warn("{}: aviso de rede nao cancelou a tempo.", m_Name);
      canFree = false;
    }
  }
  if(canFree) {
    if(addrChange.hEvent)
      CloseHandle(addrChange.hEvent);
    delete addrChangePtr;
  }
#endif
  spdlog::info("{} stopped.", m_Name);
}

void UDPBroadcaster::SendBroadcast(const BroadcastTarget &target, const std::vector<uint8_t> &payload, uint16_t destPort) {
  if(m_BadSources.contains(target.sourceIP))
    return;
  boost::system::error_code ec;
  boost::asio::io_context io_context;
  boost::asio::ip::udp::socket socket(io_context);

  socket.open(boost::asio::ip::udp::v4(), ec);
  if(ec) {
    spdlog::warn("{}: Failed to open socket for {}: {}", m_Name, target.sourceIP, ec.message());
    return;
  }
  socket.set_option(boost::asio::socket_base::reuse_address(true), ec);
  if(ec)
    spdlog::warn("{}: Failed to set reuse_address for {}: {}", m_Name, target.sourceIP, ec.message());
  socket.set_option(boost::asio::socket_base::broadcast(true), ec);
  if(ec)
    spdlog::warn("{}: Failed to set broadcast for {}: {}", m_Name, target.sourceIP, ec.message());

  auto srcAddr = boost::asio::ip::make_address_v4(target.sourceIP, ec);
  if(ec) {
    spdlog::warn("{}: Invalid source address '{}': {}", m_Name, target.sourceIP, ec.message());
    return;
  }
  socket.bind(boost::asio::ip::udp::endpoint(srcAddr, 0), ec);
  if(ec) {
    spdlog::warn("{}: endereco {} sem bind ({}); ignorado ate a rede mudar.", m_Name, target.sourceIP, ec.message());
    m_BadSources.insert(target.sourceIP);
    return;
  }
  auto bcAddr = boost::asio::ip::make_address_v4(target.broadcastIP, ec);
  if(ec) {
    spdlog::warn("{}: Invalid broadcast address '{}': {}", m_Name, target.broadcastIP, ec.message());
    return;
  }
  socket.send_to(boost::asio::buffer(payload), boost::asio::ip::udp::endpoint(bcAddr, destPort), 0, ec);
  if(ec)
    spdlog::warn("{}: Send failed ({} -> {}:{}): {}", m_Name, target.sourceIP, target.broadcastIP, destPort, ec.message());
}

void UDPBroadcaster::SendUnicast(const std::string &destIP, uint16_t destPort,
                                 const std::function<std::vector<uint8_t>(const std::string &localIP)> &buildPayload) {
  boost::system::error_code ec{};
  auto destAddr = boost::asio::ip::make_address_v4(destIP, ec);
  if(ec)
    return;
  boost::asio::io_context ioContext{};
  boost::asio::ip::udp::socket socket(ioContext);
  socket.open(boost::asio::ip::udp::v4(), ec);
  if(ec)
    return;
  boost::asio::ip::udp::endpoint endpoint(destAddr, destPort);
  // connect() num socket UDP so escolhe a rota: o IP local resultante e o que o celular deve usar para responder.
  socket.connect(endpoint, ec);
  if(ec) {
    spdlog::debug("{}: sem rota para {}: {}", m_Name, destIP, ec.message());
    return;
  }
  auto localIP = socket.local_endpoint(ec).address().to_string();
  if(ec)
    return;
  auto payload = buildPayload(localIP);
  socket.send(boost::asio::buffer(payload), 0, ec);
  if(ec)
    spdlog::debug("{}: falha no unicast {} -> {}:{}: {}", m_Name, localIP, destIP, destPort, ec.message());
}
