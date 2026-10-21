#include "CUnlockListener.h"

#include "CSampleProvider.h"
#include "handler/UnlockHandler.h"
#include "helpers.h"
#include "platform/NetworkHelper.h"
#include "storage/AppSettings.h"
#include "utils/StringUtils.h"

#include <algorithm>
#include <iterator>
#include <sddl.h>
#include <spdlog/fmt/xchar.h>
#include <spdlog/spdlog.h>

// Italock: tecla so dispara um novo pedido ao celular 15 s depois do anterior disparado por tecla. Sem isso, depois de
// uma falha, cada tecla (digitando a senha, Ctrl+Alt+Del) mandava outro pedido. O Destravar do celular e o link
// "Tentar de novo" nao tem esse limite.
static constexpr ULONGLONG KEY_RETRY_MIN_INTERVAL_MS = 15000;

void CUnlockListener::Initialize(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, std::shared_ptr<ProviderLink> providerLink, CUnlockCredential *pCredential,
                                 const std::wstring &userDomain) {
  m_ProviderUsage = cpus;
  m_ProviderLink = std::move(providerLink);
  m_Credential = pCredential;
  m_UserDomain = userDomain;
}

void CUnlockListener::Release() {
  Stop();
}

// Provider: a thread de trabalho avisa a LogonUI pelo link fraco (CSampleProvider::NotifyCredentialsChanged), que so
// pega referencia se o provider ainda estiver vivo. Nada de ponteiro cru para um provider que pode ja ter morrido, e
// nenhum ciclo de referencias.
void CUnlockListener::Start(bool ignoreWaitKeyPress) {
  std::lock_guard lock(m_ControlMutex);
  if(m_IsRunning)
    return;
  // Thread anterior ja terminou sozinha (sucesso, erro de usuario...): recolhe antes de abrir outra.
  if(m_ListenThread.joinable())
    m_ListenThread.join();
  m_IsRunning = true;
  m_HasResponse = false;
  m_IgnoreWaitKeyPress = ignoreWaitKeyPress;
  m_ListenThread = std::thread(&CUnlockListener::ListenThread, this);
}

void CUnlockListener::Stop() {
  std::lock_guard lock(m_ControlMutex);
  m_IsRunning = false;
  m_IgnoreWaitKeyPress = false;
  if(m_ListenThread.joinable())
    m_ListenThread.join();
  if(m_WakeEvent) {
    CloseHandle(m_WakeEvent);
    m_WakeEvent = nullptr;
  }
}

bool CUnlockListener::HasResponse() const {
  return m_HasResponse;
}

#define KEY_RANGE 0xA6
void GetAllKeyState(byte *keys, size_t len) {
  for(int i = 0; i < len; i++) {
    if(GetAsyncKeyState(i) < 0)
      keys[i] = 1;
    else
      keys[i] = 0;
  }
}

static bool IsCancelChordDown() {
  return GetAsyncKeyState(VK_LCONTROL) < 0 && GetAsyncKeyState(VK_LMENU) < 0;
}

// Italock: evento que o agente sinaliza quando o celular pede "Destravar" (substitui a tecla). Um por sessao
// (Global\ItalockWake-<sessao da LogonUI>): com varias sessoes (troca rapida de usuario, area de trabalho remota) o
// agente acorda so a tela certa. Criado uma vez e fechado so no Stop: um pedido que chega durante uma tentativa fica
// guardado e a proxima espera o consome. Auto reset; acesso so para SYSTEM e Administradores (o agente roda como SYSTEM).
void CUnlockListener::EnsureWakeEvent() {
  if(m_WakeEvent)
    return;
  DWORD session = 0;
  if(!ProcessIdToSessionId(GetCurrentProcessId(), &session))
    session = 0;
  auto name = fmt::format(L"Global\\ItalockWake-{}", session);
  PSECURITY_DESCRIPTOR sd = nullptr;
  if(ConvertStringSecurityDescriptorToSecurityDescriptorW(L"D:(A;;GA;;;SY)(A;;GA;;;BA)", SDDL_REVISION_1, &sd, nullptr)) {
    SECURITY_ATTRIBUTES sa{sizeof(sa), sd, FALSE};
    m_WakeEvent = CreateEventW(&sa, FALSE, FALSE, name.c_str());
    if(!m_WakeEvent)
      spdlog::warn("Nao consegui criar o evento de Destravar ({}).", GetLastError());
    LocalFree(sd);
  }
}

void CUnlockListener::ListenThread() {
  // Ao sair da thread por qualquer caminho, m_IsRunning volta a false: assim o proximo SetSelected consegue abrir outra
  // (antes, um return no meio deixava true e o Start seguinte nao fazia nada).
  struct RunningGuard {
    std::atomic<bool> &flag;
    ~RunningGuard() {
      flag = false;
    }
  } runningGuard{m_IsRunning};

  // Mensagens da thread so chegam a tela enquanto o listener nao foi mandado parar (Stop/SetDeselected).
  auto show = [this](const std::string &s) {
    if(m_IsRunning)
      m_Credential->UpdateMessage(s);
  };

  EnsureWakeEvent();

  // Init
  show(I18n::Get("initializing"));
  const auto userDomainStr = StringUtils::FromWideString(m_UserDomain);
  const auto userSplit = StringUtils::Split(userDomainStr, "\\");
  if(userSplit.size() != 2) {
    show(I18n::Get("error_invalid_user"));
    return;
  }

  // Wait
  Sleep(500);
  auto storage = AppSettings::Get();
  auto devices = PairedDevicesStorage::GetDevices();
  const auto waitForNetwork = (storage.allowLan || storage.allowTailscale) && std::ranges::any_of(devices, [](const PairedDevice &device) {
    return device.pairingMethod == PairingMethod::TCP || device.pairingMethod == PairingMethod::UDP || device.pairingMethod == PairingMethod::MANUAL_UDP;
  });
  // Italock (18/10/2026): as tentativas sozinhas da tela de entrada do boot vao so pela rede, e a rede vale para TODO
  // pareamento que tenha porta UDP ou TCP, inclusive o feito por Bluetooth (o UnlockHandler usa todos os caminhos de
  // qualquer pareamento). Antes elas dependiam de waitForNetwork, que olha o METODO do pareamento: com o celular pareado
  // por Bluetooth a tela do boot chamava uma vez as 5h, o celular nao respondia e nunca mais chamava; so uma
  // tecla no PC fazia o pedido chegar. waitForNetwork continua decidindo so a espera pela rede logo acima.
  const bool canRetryOverNetwork = (storage.allowLan || storage.allowTailscale) && std::ranges::any_of(devices, [](const PairedDevice &device) {
    return device.pairingMethod == PairingMethod::TCP || device.udpPort != 0 || device.udpManualPort != 0;
  });
  const bool lockScreen = m_ProviderUsage == CPUS_LOGON || m_ProviderUsage == CPUS_UNLOCK_WORKSTATION;
  bool isUnlockScenario = false;
  bool isBootLogon = false;
  if(lockScreen) {
    const bool isUserLoggedOn = IsUserLoggedOn(m_UserDomain, 15);

    // Network
    if(waitForNetwork) {
      show(I18n::Get("wait_network"));
      while(m_IsRunning) {
        auto isAbort = IsCancelChordDown();
        if(NetworkHelper::HasLANConnection() || isAbort) {
          if(isAbort) {
            m_HasResponse = true;
            show(I18n::Get("unlock_canceled"));
            return;
          }
          break;
        }
        Sleep(100);
      }
    }

    isUnlockScenario = m_ProviderUsage == CPUS_UNLOCK_WORKSTATION || (m_ProviderUsage == CPUS_LOGON && isUserLoggedOn);
    isBootLogon = m_ProviderUsage == CPUS_LOGON && !isUserLoggedOn;
  }

  // Quando chamar o celular (Configuracoes, Tela de bloqueio):
  //  key_press_lock_only  tecla na tela travada; na tela de entrada do boot chama na hora (padrao)
  //  key_press            sempre depois de uma tecla
  //  foreground_always    quando a tela de bloqueio aparece de verdade (janela da LogonUI em primeiro plano)
  //  foreground_lock_only idem so na tela travada; no boot chama na hora
  //  wake_only            so pelo botao Destravar do celular; tecla nunca chama
  //  none                 na hora
  // Em todos, o Destravar do celular libera a espera, e Ctrl+Alt esquerdos cancelam.
  const auto &behavior = storage.winUnlockBehavior;
  const bool wakeOnly = behavior == "wake_only";
  WaitFor firstWait = WaitFor::KEY;
  bool firstWaits = false;
  if(lockScreen && !m_IgnoreWaitKeyPress) {
    if(wakeOnly) {
      firstWaits = true;
      firstWait = WaitFor::WAKE_ONLY;
    } else if(behavior == "key_press" || (behavior == "key_press_lock_only" && isUnlockScenario)) {
      firstWaits = true;
      firstWait = WaitFor::KEY;
    } else if(behavior == "foreground_always" || (behavior == "foreground_lock_only" && isUnlockScenario)) {
      firstWaits = true;
      firstWait = WaitFor::FOREGROUND;
    }
  }
  const auto waitMessage = [&](WaitFor what) -> std::string {
    if(what == WaitFor::WAKE_ONLY)
      return I18n::Get("wait_wake_only");
    if(what == WaitFor::FOREGROUND)
      return I18n::Get("wait_foreground");
    return I18n::Get("wait_key_press");
  };

  // Italock (18/10/2026): na tela de entrada do boot o PC chama o celular sozinho, sem ninguem tocar no teclado, e esse
  // pedido vai marcado como automatico: o celular novo nao toca alerta (as 5h ninguem quer), deixa um aviso silencioso
  // "Destravar <PC>?" e responde WAITING (estado DEFERRED aqui). Tocar no aviso, a qualquer hora e de qualquer lugar, faz
  // o celular pedir pelo agente (evento ItalockWake) e o PC entra sem tecla. Enquanto o celular nem conectar, o PC
  // continua tentando (1, 2, 5 e depois a cada 10 min, sem limite de vezes); depois que o aviso foi entregue, recusado
  // ou expirou, para e fica so esperando tecla/Destravar.
  // Italock: tenta, e se nao der certo (celular fora de alcance, Tailscale caido, recusou...) volta a esperar em vez
  // de desistir de vez: assim o botao "Destravar" do app funciona a qualquer momento enquanto a tela estiver aberta.
  // Na tela de entrada do boot (ninguem logado) tenta sozinho de novo (opcao bootAutoRetry), mas so se o celular
  // nem tiver se conectado, com intervalo crescente (1, 2, 5 e depois 10 min); tecla/Destravar zeram a contagem. As repeticoes automaticas usam so a rede
  // (UDP/TCP): Bluetooth fica para quando alguem esta na frente do PC. Em wake_only nao ha repeticao automatica.
  static constexpr DWORD AUTO_RETRY_DELAYS_MS[] = {60000, 120000, 300000, 600000};
  // Limite antigo de 10 tentativas seguidas: com o celular dormindo de madrugada as 10 acabavam antes das 6h30 e o pedido
  // nunca chegava. Cada tentativa e so rede por ate 20 s a cada 10 min, entao o limite saiu (18/10/2026).
  // static constexpr int AUTO_RETRY_MAX = 10;
  std::function<void(const std::string &)> printMessage = show;
  bool firstAttempt = true;
  int autoRetries = 0;
  auto lastState = UnlockState::UNKNOWN;
  while(m_IsRunning) {
    const bool waits = firstAttempt ? firstWaits : true;
    const WaitFor what = firstAttempt ? firstWait : (wakeOnly ? WaitFor::WAKE_ONLY : WaitFor::KEY);
    bool autoAttempt = false;
    // Primeira tentativa da tela de entrada do boot, que sai na hora sem ninguem pedir: tambem automatica (mas com
    // Bluetooth, para quem acabou de ligar o PC com o celular do lado e sem rede).
    bool automatic = !waits && isBootLogon && !m_IgnoreWaitKeyPress;
    if(waits) {
      const bool autoRetry = !firstAttempt && !wakeOnly && storage.bootAutoRetry && isBootLogon && canRetryOverNetwork &&
                             (lastState == UnlockState::TIMEOUT || lastState == UnlockState::CONNECT_ERROR);
      if(firstAttempt) {
        Sleep(500);
        show(waitMessage(what));
      }
      const auto delayIdx = std::min<size_t>(static_cast<size_t>(autoRetries), std::size(AUTO_RETRY_DELAYS_MS) - 1);
      const auto waitResult = Wait(what, autoRetry ? AUTO_RETRY_DELAYS_MS[delayIdx] : INFINITE);
      if(waitResult == WaitResult::STOPPED)
        break; // listener parado
      if(waitResult == WaitResult::CANCELED) {
        // Ctrl+Alt esquerdos durante a espera: nao chama o celular; volta a esperar tecla/Destravar.
        show(I18n::Get("unlock_canceled"));
        firstAttempt = false;
        lastState = UnlockState::CANCELED;
        continue;
      }
      if(waitResult == WaitResult::TIMEOUT) {
        autoAttempt = true;
        automatic = true;
        if(autoRetries < 1000)
          autoRetries++;
      } else {
        autoRetries = 0; // alguem tocou numa tecla ou pediu pelo celular
      }
    }
    firstAttempt = false;

    auto handler = UnlockHandler(printMessage);
    handler.SetRecordHistory(true);
    if(automatic)
      spdlog::info("Pedido automatico ao celular (tela de entrada, sem tecla).");
    const auto result = handler.GetResult(userDomainStr, "Tela de bloqueio do Windows", &m_IsRunning, autoAttempt, automatic);
    lastState = result.state;
    if(!m_IsRunning)
      return; // mandaram parar (SetDeselected/Stop): nada de mexer na tela a partir daqui
    if(result.state == UnlockState::SUCCESS) {
      m_HasResponse = true;
      m_Credential->SetUnlockData(result);
      CSampleProvider::NotifyCredentialsChanged(m_ProviderLink);
      return;
    }
    // Falhou: mostra o motivo e fica aguardando tecla/Destravar (ou o proximo ciclo automatico no boot).
    m_Credential->SetUnlockData(result);
  }
}

// Espera o que libera a proxima tentativa. Sempre: o "Destravar" do celular (evento sinalizado pelo agente) e
// Ctrl+Alt esquerdos (cancela). KEY: qualquer tecla/clique, mas nao enquanto a pessoa digita a senha no campo do
// Italock nem antes de 15 s da tentativa anterior disparada por tecla. FOREGROUND: a janela da LogonUI em primeiro plano
// (a tela apareceu de verdade). WAKE_ONLY: so o evento.
CUnlockListener::WaitResult CUnlockListener::Wait(WaitFor what, DWORD timeoutMs) {
  const auto start = GetTickCount64();
  const DWORD currentProcessId = GetCurrentProcessId();
  byte lastKeys[KEY_RANGE];
  GetAllKeyState(lastKeys, KEY_RANGE);
  while(true) {
    if(!m_IsRunning)
      return WaitResult::STOPPED;
    if(IsCancelChordDown()) {
      m_LastKeyAttemptMs = GetTickCount64();
      // espera soltar, para a soltura nao contar como tecla nova
      while(m_IsRunning && (GetAsyncKeyState(VK_LCONTROL) < 0 || GetAsyncKeyState(VK_LMENU) < 0))
        Sleep(50);
      return WaitResult::CANCELED;
    }
    if(what == WaitFor::KEY) {
      byte keys[KEY_RANGE];
      GetAllKeyState(keys, KEY_RANGE);
      if(memcmp(keys, lastKeys, KEY_RANGE) != 0) {
        memcpy(lastKeys, keys, KEY_RANGE);
        const auto now = GetTickCount64();
        const bool typingPassword = m_Credential->IsPasswordTyped();
        const bool tooSoon = m_LastKeyAttemptMs != 0 && now - m_LastKeyAttemptMs < KEY_RETRY_MIN_INTERVAL_MS;
        if(!typingPassword && !tooSoon) {
          m_LastKeyAttemptMs = now;
          return WaitResult::KEY;
        }
      }
    } else if(what == WaitFor::FOREGROUND) {
      if(HWND hwndForeground = GetForegroundWindow()) {
        DWORD foregroundProcessId = 0;
        GetWindowThreadProcessId(hwndForeground, &foregroundProcessId);
        if(foregroundProcessId == currentProcessId)
          return WaitResult::FOREGROUND;
      }
    }
    // Espera no evento em vez de Sleep: dorme 50 ms, mas acorda na hora se o celular pedir (auto reset: consome o pedido).
    if(m_WakeEvent ? WaitForSingleObject(m_WakeEvent, 50) == WAIT_OBJECT_0 : (Sleep(50), false)) {
      spdlog::info("Destravar pedido pelo celular (sem tecla).");
      return WaitResult::WAKE;
    }
    if(timeoutMs != INFINITE && GetTickCount64() - start >= timeoutMs)
      return WaitResult::TIMEOUT;
  }
}
