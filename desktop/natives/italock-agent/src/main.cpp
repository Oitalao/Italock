// Italock agent: trava/acorda a tela quando um celular pareado pede.
// Windows: roda como SYSTEM (tarefa agendada na inicializacao do PC). Motivo: os pareamentos e o evento de Destravar
// so abrem para SYSTEM/Administradores; rodando na sessao de um usuario comum ele recebia NOT_PAIRED/NOT_WAITING. Como
// SYSTEM nao se da acesso a mais ninguem: o agente acha a sessao do USUARIO DO PAREAMENTO (WTSEnumerateSessions +
// token da sessao; vale console e area de trabalho remota) e so age nela. Para travar, abre um processo minimo nessa
// sessao (CreateProcessAsUser com o token do usuario) que so chama LockWorkStation.
// Linux: servico systemd (root); os scripts em /usr/local/lib/italock recebem o usuario do pareamento no ambiente
// (ITALOCK_USER) e agem na sessao grafica dele.
//
// Protocolo (extensao do Italock sobre o framing do PC Bio Unlock):
//   celular -> agente: frame 0xC0 {"deviceId":"...","encData":"HEX(EncryptAESPacket(<pedido>, encryptionKey))","wantSealed":true (opcional)}
//     <pedido> = {"action":"lock"|"wake"|"status", "ips":[...], "wantSealed":true (opcional), "nonce":"<texto>" (com wantSealed)}
//   agente -> celular: frame 0xC1
//     sem "wantSealed" (compatibilidade): {"error":""} (sucesso) ou {"error":"<codigo>"}; "status" inclui {"locked":bool}
//     com "wantSealed":true (no JSON externo ou dentro do pacote cifrado) e pedido autenticado:
//       {"sealed":"HEX(EncryptAESPacket(<resposta>, encryptionKey))"}
//       <resposta> = a resposta normal ({"error":"...","locked":bool so em status}) mais "nonce" igual ao recebido.
//       Mesmo pacote cifrado dos pedidos (CryptUtils: IV(16) | salt(16) | dados | tag(16), dados com carimbo de horario
//       de 8 bytes na frente), mesma chave do pareamento; HEX em maiusculas. O "nonce" amarra a resposta ao pedido, entao
//       uma resposta capturada nao serve para outro pedido; o carimbo limita a 60 s. Quem pediu selado deve tratar
//       resposta sem "sealed" como nao confiavel (so aparecem erros anteriores a autenticacao: NOT_PAIRED, DATA_ERROR,
//       TIME_ERROR, BAD_PACKET, BUSY, PATH_DISABLED, REPLAY).
//   acoes: "lock" (travar), "wake" (acordar a tela bloqueada para ela pedir o desbloqueio), "status" (responde tambem
//   {"locked":true|false}).
//
// Codigos de erro ("error"):
//   NOT_PAIRED    deviceId desconhecido neste PC.
//   TIME_ERROR    carimbo de horario fora de +-60 s (relogio do celular ou do PC errado).
//   DATA_ERROR    pacote ilegivel / chave errada.
//   BAD_PACKET    frame com id diferente de 0xC0.
//   BAD_ACTION    acao desconhecida.
//   STATE_UNKNOWN "status": nao deu para saber se a tela esta travada.
//   NOT_WAITING   "wake": a tela de bloqueio nao esta aguardando (nao ha tela travada com o Italock, ou o modulo nao
//                 esta carregado; no Linux tambem quando a tela ja esta destravada). Nada foi feito.
//   LOCK_FAILED   "lock": o sistema recusou travar a tela (conferido: a sessao continua destravada).
//   LOCK_DISABLED "lock": a pessoa desligou "Aceitar pedidos de travar pelo celular" neste PC. Nada foi feito.
//   PATH_DISABLED o pedido chegou por um caminho desligado nas configuracoes (rede local ou Tailscale). Nada foi feito.
//   REPLAY        este mesmo pacote cifrado ja foi aceito antes (copia capturada/repetida). Nada foi feito. O celular
//                 cifra de novo a cada envio (IV aleatorio), entao um pedido legitimo nunca recebe REPLAY.
//   BUSY          agente ocupado (conexoes simultaneas demais, ou pedidos demais vindos do mesmo IP no ultimo minuto).
//                 Nada foi feito; tentar de novo em alguns segundos.
// Um pacote so vale 60 s (carimbo de horario) e so uma vez (cache dos IVs aceitos nessa janela).
//
// Linux, experimental "travar quando o celular se afastar": se ligado, a cada minuto um l2ping (1 pacote, 3 s) em cada
// celular pareado com Bluetooth; nenhum responde por N minutos com a tela destravada = trava uma vez (so trava de novo
// depois que algum celular voltar a responder). No Windows o app usa o Bloqueio dinamico do proprio Windows.

#ifdef _WIN32
#include <WinSock2.h>
#include <Windows.h>
#include <shellapi.h>
#include <wtsapi32.h>
#include <ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <unistd.h>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <spawn.h>
extern char **environ;
using SOCKET = int;
constexpr int INVALID_SOCKET = -1;
#define closesocket close
#endif

#include <algorithm>
#include <cctype>
#include <array>
#include <atomic>
#include <filesystem>
#include <map>
#include <mutex>
#include <cstdio>
#include <chrono>
#include <cstdint>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/sinks/rotating_file_sink.h>
#include <spdlog/spdlog.h>

#include "connection/Packets.h"
#include "storage/AppSettings.h"
#include "storage/LoggingSystem.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/CryptUtils.h"
#include "utils/StringUtils.h"

constexpr uint16_t AGENT_PORT = 43301;
constexpr size_t MAX_PAYLOAD = 4096;
constexpr int MAX_CONNECTIONS = 6;          // conexoes simultaneas (cada uma custa um PBKDF2 de 65535 iteracoes)
constexpr int MAX_CONNECTIONS_PER_IP = 3;   // simultaneas vindas do mesmo IP (status + travar + destravar juntos)
// O app consulta o cadeado a cada 5, 15 ou 30 s (configuravel) com a tela aberta. No pior caso (5 s) sao 12 por minuto
// por PC vindas daquele IP, mais travar/destravar e as repeticoes em paralelo nos varios IPs do PC. Cada PC tem o seu
// agente e conta sozinho, entao 3 PCs pareados nao somam aqui. 60 por minuto deixa folga de 5 vezes.
constexpr int MAX_REQUESTS_PER_IP_MIN = 60;
constexpr int64_t PACKET_MAX_AGE_MS = 60000;
constexpr int64_t REPLAY_KEEP_MS = 2 * PACKET_MAX_AGE_MS; // aceito em t cobre carimbos ate t+60 s
constexpr size_t REPLAY_MAX_ENTRIES = 4096;
constexpr size_t IV_SIZE = 16;

static int64_t NowMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

// ---- Limites de conexao ----
struct IpState {
  int active{};
  int64_t windowStart{};
  int count{};
};
static std::mutex g_LimitMutex{};
static std::map<std::string, IpState> g_IpStates{};
static int g_Active = 0;

// true = pode atender (e a vaga fica reservada ate ReleaseSlot)
static bool AcquireSlot(const std::string &ip) {
  std::lock_guard lock(g_LimitMutex);
  auto now = NowMs();
  if(g_IpStates.size() > 256)
    std::erase_if(g_IpStates, [now](const auto &kv) { return kv.second.active == 0 && now - kv.second.windowStart > 60000; });
  auto &st = g_IpStates[ip];
  if(now - st.windowStart > 60000) {
    st.windowStart = now;
    st.count = 0;
  }
  if(g_Active >= MAX_CONNECTIONS || st.active >= MAX_CONNECTIONS_PER_IP || st.count >= MAX_REQUESTS_PER_IP_MIN)
    return false;
  st.count++;
  st.active++;
  g_Active++;
  return true;
}

static void ReleaseSlot(const std::string &ip) {
  std::lock_guard lock(g_LimitMutex);
  g_Active--;
  auto it = g_IpStates.find(ip);
  if(it != g_IpStates.end() && it->second.active > 0)
    it->second.active--;
}

// ---- Anti-repeticao: IVs dos pacotes ja aceitos na janela ----
// (nao da para exigir carimbo crescente: o celular manda para varios IPs em paralelo e status+lock juntos, e eles
// chegam fora de ordem)
static std::mutex g_ReplayMutex{};
static std::map<std::string, int64_t> g_SeenIvs{};

// "" = novo (registrado agora), senao o codigo de erro
static std::string CheckReplay(const std::vector<uint8_t> &enc) {
  if(enc.size() < IV_SIZE)
    return "DATA_ERROR";
  std::string iv(reinterpret_cast<const char *>(enc.data()), IV_SIZE);
  std::lock_guard lock(g_ReplayMutex);
  auto now = NowMs();
  std::erase_if(g_SeenIvs, [now](const auto &kv) { return now - kv.second > REPLAY_KEEP_MS; });
  if(g_SeenIvs.contains(iv))
    return "REPLAY";
  if(g_SeenIvs.size() >= REPLAY_MAX_ENTRIES)
    return "BUSY";
  g_SeenIvs.emplace(iv, now);
  return "";
}

static bool RecvAll(SOCKET s, uint8_t *buf, size_t len) {
  size_t got = 0;
  while(got < len) {
    auto r = recv(s, reinterpret_cast<char *>(buf + got), static_cast<int>(len - got), 0);
    if(r <= 0)
      return false;
    got += r;
  }
  return true;
}

static bool ReadFrame(SOCKET s, uint16_t &id, std::string &payload) {
  std::array<uint8_t, 12> header{};
  if(!RecvAll(s, header.data(), header.size()))
    return false;
  uint64_t magic = 0;
  for(int i = 0; i < 8; i++)
    magic = (magic << 8) | header[i];
  if(magic != PACKET_HEADER)
    return false;
  id = static_cast<uint16_t>((header[8] << 8) | header[9]);
  auto len = static_cast<uint16_t>((header[10] << 8) | header[11]);
  if(len == 0 || len > MAX_PAYLOAD)
    return false;
  payload.resize(len);
  return RecvAll(s, reinterpret_cast<uint8_t *>(payload.data()), len);
}

static void WriteFrame(SOCKET s, uint16_t id, const std::string &payload) {
  std::vector<uint8_t> out{};
  for(int i = 7; i >= 0; i--)
    out.push_back(static_cast<uint8_t>((PACKET_HEADER >> (i * 8)) & 0xFF));
  out.push_back(static_cast<uint8_t>(id >> 8));
  out.push_back(static_cast<uint8_t>(id & 0xFF));
  out.push_back(static_cast<uint8_t>(payload.size() >> 8));
  out.push_back(static_cast<uint8_t>(payload.size() & 0xFF));
  out.insert(out.end(), payload.begin(), payload.end());
  send(s, reinterpret_cast<const char *>(out.data()), static_cast<int>(out.size()), 0);
}

#ifdef _WIN32
// Agente como SYSTEM (sessao 0) ou, em instalacao antiga, dentro da sessao do usuario.
static bool IsServiceSession() {
  DWORD session = 0;
  return ProcessIdToSessionId(GetCurrentProcessId(), &session) && session == 0;
}

// Ha usuario logado na sessao? (tela de entrada do boot = ninguem)
static bool SessionHasUser(DWORD session) {
  LPWSTR name = nullptr;
  DWORD bytes = 0;
  bool hasUser = false;
  if(WTSQuerySessionInformationW(WTS_CURRENT_SERVER_HANDLE, session, WTSUserName, &name, &bytes) && name) {
    hasUser = name[0] != L'\0';
    WTSFreeMemory(name);
  }
  return hasUser;
}

// SID da conta do pareamento ("DOMINIO\usuario" ou "MicrosoftAccount\email"). Conta Microsoft tem SID proprio
// (S-1-11-96-...), que aparece nos grupos do token da sessao.
static std::vector<uint8_t> LookupUserSid(const std::string &userName) {
  auto wide = StringUtils::ToWideString(userName);
  DWORD sidSize = 0, domainSize = 0;
  SID_NAME_USE use{};
  LookupAccountNameW(nullptr, wide.c_str(), nullptr, &sidSize, nullptr, &domainSize, &use);
  if(sidSize == 0)
    return {};
  std::vector<uint8_t> sid(sidSize);
  std::wstring domain(domainSize + 1, L'\0');
  if(!LookupAccountNameW(nullptr, wide.c_str(), sid.data(), &sidSize, domain.data(), &domainSize, &use))
    return {};
  return sid;
}

// O token da sessao pertence ao SID (usuario ou grupo)?
static bool SessionBelongsTo(DWORD session, const std::vector<uint8_t> &sid) {
  if(sid.empty())
    return false;
  HANDLE token = nullptr;
  if(!WTSQueryUserToken(session, &token))
    return false;
  bool match = false;
  DWORD size = 0;
  GetTokenInformation(token, TokenUser, nullptr, 0, &size);
  if(size > 0) {
    std::vector<uint8_t> buf(size);
    if(GetTokenInformation(token, TokenUser, buf.data(), size, &size))
      match = EqualSid(reinterpret_cast<TOKEN_USER *>(buf.data())->User.Sid, (PSID)sid.data());
  }
  if(!match) {
    size = 0;
    GetTokenInformation(token, TokenGroups, nullptr, 0, &size);
    if(size > 0) {
      std::vector<uint8_t> buf(size);
      if(GetTokenInformation(token, TokenGroups, buf.data(), size, &size)) {
        auto groups = reinterpret_cast<TOKEN_GROUPS *>(buf.data());
        for(DWORD i = 0; i < groups->GroupCount && !match; i++)
          match = EqualSid(groups->Groups[i].Sid, (PSID)sid.data());
      }
    }
  }
  CloseHandle(token);
  return match;
}

struct UserSession {
  DWORD id = 0xFFFFFFFF;
  bool active = false; // WTSActive (na tela agora); desconectada = area remota fechada ou troca de usuario
};

// Sessoes do usuario do pareamento (console e area de trabalho remota). A ativa vem primeiro.
static std::vector<UserSession> FindUserSessions(const std::string &userName) {
  std::vector<UserSession> result{};
  auto sid = LookupUserSid(userName);
  if(sid.empty()) {
    spdlog::warn("Conta do pareamento nao encontrada: {}", userName);
    return result;
  }
  PWTS_SESSION_INFOW sessions = nullptr;
  DWORD count = 0;
  if(!WTSEnumerateSessionsW(WTS_CURRENT_SERVER_HANDLE, 0, 1, &sessions, &count))
    return result;
  for(DWORD i = 0; i < count; i++) {
    auto &s = sessions[i];
    if(s.SessionId == 0 || !SessionHasUser(s.SessionId))
      continue;
    if(SessionBelongsTo(s.SessionId, sid))
      result.push_back({s.SessionId, s.State == WTSActive});
  }
  WTSFreeMemory(sessions);
  if(result.empty())
    spdlog::debug("Nenhuma sessao aberta de {}.", userName);
  else
    spdlog::debug("Sessoes de {}: {} (primeira {}).", userName, result.size(), result.front().id);
  std::stable_sort(result.begin(), result.end(), [](const UserSession &a, const UserSession &b) { return a.active && !b.active; });
  return result;
}

// Sessao travada? 1 sim, 0 nao, -1 nao deu para saber.
static int IsSessionLocked(DWORD session) {
  DWORD bytes = 0;
  LPWSTR buf = nullptr;
  int result = -1;
  if(WTSQuerySessionInformationW(WTS_CURRENT_SERVER_HANDLE, session, WTSSessionInfoEx, &buf, &bytes) && buf) {
    auto info = reinterpret_cast<WTSINFOEXW *>(buf);
    if(info->Level == 1) {
      if(info->Data.WTSInfoExLevel1.SessionState == WTSDisconnected)
        result = 1; // desconectada: para voltar passa pela tela de entrada
      else
        result = info->Data.WTSInfoExLevel1.SessionFlags == WTS_SESSIONSTATE_LOCK ? 1 : 0;
    }
    WTSFreeMemory(buf);
  }
  return result;
}
#endif

#ifndef _WIN32
// Roda um script do Italock com o usuario do pareamento em ITALOCK_USER (os scripts usam para achar a sessao dele).
// Devolve o codigo de saida, ou -1 se nem rodou.
static int RunScript(const std::string &path, const std::string &userName) {
  std::vector<std::string> envStore{};
  for(char **e = environ; e && *e; e++)
    if(strncmp(*e, "ITALOCK_USER=", 13) != 0)
      envStore.emplace_back(*e);
  envStore.emplace_back("ITALOCK_USER=" + userName);
  std::vector<char *> envp{};
  for(auto &s : envStore)
    envp.push_back(s.data());
  envp.push_back(nullptr);
  std::string p = path;
  char *argv[] = {p.data(), nullptr};
  pid_t pid{};
  if(posix_spawn(&pid, p.c_str(), nullptr, nullptr, argv, envp.data()) != 0)
    return -1;
  int status = 0;
  while(waitpid(pid, &status, 0) < 0) {
    if(errno != EINTR)
      return -1;
  }
  return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static std::string ScriptPath(const char *envName, const char *fallback) {
  const char *v = std::getenv(envName);
  return v && *v ? std::string(v) : std::string(fallback);
}
#endif

// A tela do usuario do pareamento esta travada? (-1 = nao deu para saber). Nao logado conta como travada.
static int IsScreenLocked(const std::string &userName) {
#ifdef _WIN32
  if(!IsServiceSession()) {
    // instalacao antiga: agente dentro da sessao do usuario
    DWORD self = 0;
    ProcessIdToSessionId(GetCurrentProcessId(), &self);
    return IsSessionLocked(self);
  }
  auto sessions = FindUserSessions(userName);
  if(sessions.empty())
    return 1; // usuario nao esta logado: so entra pela tela de entrada
  // Qualquer sessao dele aberta e destravada = destravado.
  bool unknown = false;
  for(const auto &s : sessions) {
    auto st = IsSessionLocked(s.id);
    if(st == 0)
      return 0;
    if(st < 0)
      unknown = true;
  }
  return unknown ? -1 : 1;
#else
  // Script: sai 0 se a protecao de tela esta ativa, 1 se nao, outro valor em erro.
  auto code = RunScript(ScriptPath("ITALOCK_STATE_CMD", "/usr/local/lib/italock/estado-tela"), userName);
  return code == 0 ? 1 : code == 1 ? 0 : -1;
#endif
}

// Tailscale usa 100.64.0.0/10.
static bool IsTailscaleIp(const std::string &ip) {
  unsigned a = 0, b = 0;
  if(sscanf(ip.c_str(), "%u.%u", &a, &b) != 2)
    return false;
  return a == 100 && b >= 64 && b <= 127;
}

// O celular manda os IPs atuais em cada pedido autenticado: o PC aprende o endereco novo sozinho (Wi-Fi que trocou,
// Tailscale que voltou), sem precisar parear de novo. Se o celular estiver sem Tailscale agora, guarda so o ULTIMO IP
// do Tailscale que ele teve (os anteriores saem: a conta pode ter mudado e o IP velho ser de outra maquina).
static std::vector<std::string> MergeIps(const std::vector<std::string> &reported, const std::vector<std::string> &old) {
  std::vector<std::string> merged{};
  auto add = [&](const std::string &ip) {
    if(!ip.empty() && std::find(merged.begin(), merged.end(), ip) == merged.end() && merged.size() < 8)
      merged.emplace_back(ip);
  };
  bool reportedTailscale = false;
  for(const auto &ip : reported) {
    if(IsTailscaleIp(ip)) {
      if(reportedTailscale)
        continue; // um so
      reportedTailscale = true;
    }
    add(ip);
  }
  if(!reportedTailscale) {
    for(const auto &ip : old) {
      if(IsTailscaleIp(ip)) {
        add(ip); // o primeiro guardado e o mais recente (os informados vem sempre na frente)
        break;
      }
    }
  }
  return merged;
}

static bool SameIps(std::vector<std::string> a, std::vector<std::string> b) {
  std::sort(a.begin(), a.end());
  std::sort(b.begin(), b.end());
  return a == b;
}

static void UpdatePhoneIps(const PairedDevice &device, const std::vector<std::string> &reported) {
  if(reported.empty() || SameIps(device.ipAddresses, MergeIps(reported, device.ipAddresses)))
    return; // nada mudou: nem mexe no arquivo
  std::vector<std::string> merged{};
  // Le, altera e grava dentro da trava dos pareamentos (outro processo pode estar gravando junto).
  auto changed = PairedDevicesStorage::UpdateDevice(device.id, [&](PairedDevice &d) {
    merged = MergeIps(reported, d.ipAddresses);
    d.ipAddresses = merged;
  });
  if(!changed)
    return;
  std::string list{};
  for(const auto &ip : merged)
    list += (list.empty() ? "" : ", ") + ip;
  spdlog::info("IPs de '{}' atualizados: {}", device.deviceName, list);
}

#ifdef _WIN32
// Trava uma sessao. Como SYSTEM nao da para chamar LockWorkStation direto (ela trava a sessao de quem chama, a 0):
// abre este mesmo exe com --travar na sessao do usuario, com o token dele, e espera o resultado. Depois confere o
// estado da sessao (o travador pode demorar mais que a espera ou sair com erro mesmo tendo travado).
static bool LockSession(DWORD session) {
  if(IsSessionLocked(session) == 1)
    return true;
  HANDLE userToken = nullptr;
  if(!WTSQueryUserToken(session, &userToken)) {
    spdlog::error("WTSQueryUserToken falhou ({}).", GetLastError());
    return false;
  }
  wchar_t exePath[MAX_PATH]{};
  GetModuleFileNameW(nullptr, exePath, MAX_PATH);
  std::wstring cmdLine = L"\"" + std::wstring(exePath) + L"\" --travar";
  STARTUPINFOW si{sizeof(si)};
  si.lpDesktop = const_cast<LPWSTR>(L"winsta0\\default");
  PROCESS_INFORMATION pi{};
  bool ok = CreateProcessAsUserW(userToken, exePath, cmdLine.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi);
  auto err = GetLastError();
  CloseHandle(userToken);
  if(!ok) {
    spdlog::error("Nao consegui abrir o travador na sessao {} ({}).", session, err);
    return false;
  }
  DWORD exitCode = 1;
  if(WaitForSingleObject(pi.hProcess, 5000) == WAIT_OBJECT_0)
    GetExitCodeProcess(pi.hProcess, &exitCode);
  CloseHandle(pi.hThread);
  CloseHandle(pi.hProcess);
  if(exitCode == 0)
    return true;
  // Confere de verdade: ate 3 s para a tela de bloqueio aparecer.
  for(int i = 0; i < 6; i++) {
    if(IsSessionLocked(session) == 1) {
      spdlog::info("Travador saiu com {}, mas a sessao {} esta travada.", exitCode, session);
      return true;
    }
    Sleep(500);
  }
  spdlog::error("Travador saiu com {} e a sessao {} continua destravada.", exitCode, session);
  return false;
}

// Trava as sessoes do usuario do pareamento que estiverem destravadas (outras contas no mesmo PC nao sao tocadas).
static bool LockUserSessions(const std::string &userName) {
  if(!IsServiceSession()) {
    if(LockWorkStation())
      return true;
    spdlog::error("LockWorkStation falhou ({}).", GetLastError());
    return false;
  }
  auto sessions = FindUserSessions(userName);
  if(sessions.empty())
    return true; // usuario nao esta logado: ja esta na tela de entrada
  bool ok = true;
  for(const auto &s : sessions)
    if(IsSessionLocked(s.id) == 0)
      ok = LockSession(s.id) && ok;
  return ok;
}

// Sinaliza o evento de Destravar da tela de bloqueio de uma sessao (criado pelo modulo na LogonUI daquela sessao).
static bool SignalWake(DWORD session) {
  auto name = L"Global\\ItalockWake-" + std::to_wstring(session);
  HANDLE ev = OpenEventW(EVENT_MODIFY_STATE, FALSE, name.c_str());
  if(!ev)
    return false;
  auto ok = SetEvent(ev);
  CloseHandle(ev);
  return ok != FALSE;
}

// Acorda a tela de bloqueio que vai destravar este usuario: a da sessao dele e a do console (sessao desconectada,
// troca de usuario e tela de entrada do boot aparecem no console). Modulo antigo ainda carregado usa o evento unico.
static bool WakeUserScreens(const std::string &userName) {
  std::vector<DWORD> targets{};
  if(!IsServiceSession()) {
    DWORD self = 0;
    ProcessIdToSessionId(GetCurrentProcessId(), &self);
    targets.push_back(self);
  } else {
    for(const auto &s : FindUserSessions(userName))
      targets.push_back(s.id);
    auto console = WTSGetActiveConsoleSessionId();
    if(console != 0xFFFFFFFF && std::find(targets.begin(), targets.end(), console) == targets.end())
      targets.push_back(console);
  }
  bool any = false;
  for(auto id : targets)
    any = SignalWake(id) || any;
  if(!any) {
    HANDLE ev = OpenEventW(EVENT_MODIFY_STATE, FALSE, L"Global\\ItalockWake");
    if(ev) {
      any = SetEvent(ev) != FALSE;
      CloseHandle(ev);
    }
  }
  return any;
}
#endif

// Caminho do pedido em palavras, para o historico.
static std::string PeerPath(const std::string &peer) {
  if(peer.rfind("127.", 0) == 0)
    return "este computador";
  return IsTailscaleIp(peer) ? "Tailscale" : "rede local";
}

// Caminho do pedido: 127.0.0.1 (testes locais) sempre; Tailscale e rede local conforme as configuracoes.
static bool PeerAllowed(const std::string &peer, const PCBUAppStorage &settings) {
  if(peer == "127.0.0.1")
    return true;
  return IsTailscaleIp(peer) ? settings.allowTailscale : settings.allowLan;
}

// Resposta selada: cifra o JSON da resposta com a chave do pareamento (ver o topo do arquivo).
static std::string SealResponse(nlohmann::json response, const std::string &key, const std::string &nonce) {
  response["nonce"] = nonce;
  auto plain = response.dump();
  auto packet = CryptUtils::EncryptAESPacket(std::vector<uint8_t>(plain.begin(), plain.end()), key);
  if(packet.result != PacketCryptResult::OK)
    return {};
  nlohmann::json outer = {{"sealed", StringUtils::ToHexString(packet.data)}};
  return outer.dump();
}

// Pedido autenticado que pediu resposta selada: guarda a chave e o pacote para cifrar a resposta.
struct SealInfo {
  bool want = false;
  std::string key{};
  std::string nonce{};
};

static std::string Handle(const std::string &peer, uint16_t id, const std::string &payload, nlohmann::json &extra, SealInfo &seal) {
  if(id != PACKET_ID_LOCK_REQUEST)
    return "BAD_PACKET";
  // Configuracoes relidas a cada pedido (o app muda o arquivo; o agente fica no ar o tempo todo).
  AppSettings::InvalidateCache();
  const auto settings = AppSettings::Get();
  if(!PeerAllowed(peer, settings)) {
    spdlog::warn("Pedido de {} recusado: caminho desligado nas configuracoes.", peer);
    return "PATH_DISABLED";
  }
  nlohmann::json req{};
  try {
    req = nlohmann::json::parse(payload);
  } catch(...) {
    return "DATA_ERROR";
  }
  auto deviceId = req.value("deviceId", std::string{});
  auto device = PairedDevicesStorage::GetDeviceByID(deviceId);
  if(!device.has_value()) {
    spdlog::warn("Pedido de {} com dispositivo desconhecido.", peer);
    return "NOT_PAIRED";
  }
  auto enc = StringUtils::FromHexString(req.value("encData", std::string{}));
  auto dec = CryptUtils::DecryptAESPacket(enc, device->encryptionKey, PACKET_MAX_AGE_MS);
  if(dec.result == PacketCryptResult::INVALID_TIMESTAMP)
    return "TIME_ERROR";
  if(dec.result != PacketCryptResult::OK)
    return "DATA_ERROR";
  if(auto replay = CheckReplay(enc); !replay.empty()) {
    spdlog::warn("Pacote repetido de {} recusado ({}).", peer, replay);
    return replay;
  }
  std::string action{};
  std::vector<std::string> phoneIps{};
  try {
    auto inner = nlohmann::json::parse(std::string(dec.data.begin(), dec.data.end()));
    action = inner.value("action", std::string{});
    if(inner.contains("ips") && inner["ips"].is_array())
      for(const auto &ip : inner["ips"])
        if(ip.is_string())
          phoneIps.emplace_back(ip.get<std::string>());
    auto wants = [](const nlohmann::json &j) { return j.contains("wantSealed") && j["wantSealed"].is_boolean() && j["wantSealed"].get<bool>(); };
    if(wants(inner) || wants(req)) {
      seal.want = true;
      seal.key = device->encryptionKey;
      if(inner.contains("nonce") && inner["nonce"].is_string())
        seal.nonce = inner["nonce"].get<std::string>();
    }
  } catch(...) {
    return "DATA_ERROR";
  }
  UpdatePhoneIps(*device, phoneIps);
  if(action == "status") {
    auto locked = IsScreenLocked(device->userName);
    if(locked < 0)
      return "STATE_UNKNOWN";
    extra["locked"] = locked == 1;
    return "";
  }
  if(action == "wake") {
    // Acordar a tela bloqueada para ela pedir o desbloqueio ao celular (o celular ja confirmou a biometria antes).
#ifdef _WIN32
    // No Windows o modulo da tela de bloqueio espera uma tecla antes de chamar o celular; o evento substitui a tecla.
    // O evento so existe enquanto ha uma tela de bloqueio do Italock aberta (um por sessao).
    if(!WakeUserScreens(device->userName)) {
      spdlog::warn("Tela de bloqueio nao esta aguardando.");
      return "NOT_WAITING";
    }
#else
    auto rc = RunScript(ScriptPath("ITALOCK_WAKE_CMD", "/usr/local/lib/italock/acordar-tela"), device->userName);
    if(rc != 0) {
      spdlog::warn("Acordar a tela nao foi feito (saida {}).", rc);
      return "NOT_WAITING";
    }
#endif
    spdlog::info("Tela acordada a pedido de '{}' ({}).", device->deviceName, peer);
    PairedDevicesStorage::RecordUse(device->id, "acordar", PeerPath(peer), "Botão Destravar do celular");
    return "";
  }
  if(action != "lock")
    return "BAD_ACTION";
  if(!settings.agentLockEnabled) {
    spdlog::info("Pedido de travar de '{}' recusado: desligado nas configuracoes.", device->deviceName);
    PairedDevicesStorage::RecordEvent(device->id, "travar", "desligado", PeerPath(peer));
    return "LOCK_DISABLED";
  }
#ifdef _WIN32
  if(!LockUserSessions(device->userName)) {
    PairedDevicesStorage::RecordEvent(device->id, "travar", "falhou", PeerPath(peer));
    return "LOCK_FAILED";
  }
#else
  // Linux (servico de sistema): trava a protecao de tela na sessao grafica do usuario do pareamento.
  auto rc = RunScript(ScriptPath("ITALOCK_LOCK_CMD", "/usr/local/lib/italock/travar-tela"), device->userName);
  if(rc != 0) {
    // Confere: pode ter travado mesmo com erro no script.
    if(IsScreenLocked(device->userName) != 1) {
      spdlog::error("Travar falhou (saida {}).", rc);
      PairedDevicesStorage::RecordEvent(device->id, "travar", "falhou", PeerPath(peer));
      return "LOCK_FAILED";
    }
  }
#endif
  spdlog::info("Tela travada a pedido de '{}' ({}).", device->deviceName, peer);
  PairedDevicesStorage::RecordUse(device->id, "travar", PeerPath(peer));
  return "";
}

#ifndef _WIN32
// Experimental (Linux): travar quando nenhum celular pareado com Bluetooth responde por N minutos. Uma checagem por
// minuto (l2ping de 1 pacote), nada roda se estiver desligado ou sem celular com Bluetooth.
static void ProximityThread() {
  int64_t lastSeen = NowMs();
  bool lockedForAbsence = false;
  bool warnedNoTool = false;
  while(true) {
    std::this_thread::sleep_for(std::chrono::seconds(60));
    try {
      AppSettings::InvalidateCache();
      auto settings = AppSettings::Get();
      if(!settings.expProximityLock || !settings.allowBluetooth) {
        lastSeen = NowMs();
        lockedForAbsence = false;
        continue;
      }
      std::vector<PairedDevice> btDevices{};
      for(const auto &d : PairedDevicesStorage::GetDevices())
        if(!d.bluetoothAddress.empty())
          btDevices.push_back(d);
      if(btDevices.empty()) {
        lastSeen = NowMs();
        continue;
      }
      if(access("/usr/bin/l2ping", X_OK) != 0) {
        if(!warnedNoTool)
          spdlog::warn("Travar ao se afastar: l2ping nao encontrado (pacote bluez).");
        warnedNoTool = true;
        continue;
      }
      bool seen = false;
      for(const auto &d : btDevices) {
        // Endereco vem do pareamento (so hexa e dois pontos); confere antes de passar ao comando.
        bool okAddr = d.bluetoothAddress.size() == 17 &&
                      std::all_of(d.bluetoothAddress.begin(), d.bluetoothAddress.end(), [](char ch) { return std::isxdigit((unsigned char)ch) || ch == ':'; });
        if(!okAddr)
          continue;
        auto cmd = "/usr/bin/l2ping -c 1 -t 3 " + d.bluetoothAddress + " >/dev/null 2>&1";
        auto rc = std::system(cmd.c_str());
        if(rc != -1 && WIFEXITED(rc) && WEXITSTATUS(rc) == 0) {
          seen = true;
          break;
        }
      }
      if(seen) {
        lastSeen = NowMs();
        lockedForAbsence = false;
        continue;
      }
      if(lockedForAbsence || NowMs() - lastSeen < static_cast<int64_t>(settings.proximityMinutes) * 60000)
        continue;
      const auto &user = btDevices.front().userName;
      if(IsScreenLocked(user) == 0) {
        auto rc = RunScript(ScriptPath("ITALOCK_LOCK_CMD", "/usr/local/lib/italock/travar-tela"), user);
        spdlog::info("Celular longe ha {} min: tela travada (saida {}).", settings.proximityMinutes, rc);
        PairedDevicesStorage::RecordEvent(btDevices.front().id, "travar", rc == 0 ? "ok" : "falhou", "Bluetooth", "Celular se afastou");
      }
      lockedForAbsence = true;
    } catch(const std::exception &ex) {
      spdlog::error("Travar ao se afastar: {}", ex.what());
    }
  }
}
#endif

// Responde BUSY sem gastar thread nem PBKDF2: descarta o que o celular ja mandou (fechar com dados nao lidos vira RST
// e o celular nao leria a resposta), escreve e fecha.
static void RejectBusy(SOCKET client) {
#ifdef _WIN32
  u_long nonBlocking = 1;
  ioctlsocket(client, FIONBIO, &nonBlocking);
#endif
  char drain[1024];
  for(int i = 0; i < 8; i++) {
#ifdef _WIN32
    auto r = recv(client, drain, sizeof(drain), 0);
#else
    auto r = recv(client, drain, sizeof(drain), MSG_DONTWAIT);
#endif
    if(r <= 0)
      break;
  }
  WriteFrame(client, PACKET_ID_LOCK_RESPONSE, R"({"error":"BUSY"})");
#ifdef _WIN32
  shutdown(client, SD_SEND);
#else
  shutdown(client, SHUT_WR);
#endif
  closesocket(client);
}

#ifdef _WIN32
int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR cmdLine, int) {
  // Modo travador: aberto pelo agente (SYSTEM) dentro da sessao do usuario, so para travar e sair.
  if(cmdLine && wcsstr(cmdLine, L"--travar"))
    return LockWorkStation() ? 0 : 2;
  // Uma instancia so no PC (como SYSTEM): Global. Instalacao antiga (por sessao) continua com Local.
  auto mutex = CreateMutexW(nullptr, TRUE, IsServiceSession() ? L"Global\\ItalockAgent" : L"Local\\ItalockAgent");
  if(GetLastError() == ERROR_ALREADY_EXISTS)
    return 0;
#else
int main() {
  void *mutex = nullptr;
#endif

  try {
    auto logPath = (AppSettings::GetBaseDir() / "agent.log").string();
    auto logger = std::make_shared<spdlog::logger>("agent", LoggingSystem::MakeFileSink(logPath, 512 * 1024, 2));
    if(std::filesystem::exists(AppSettings::GetBaseDir() / "LOG_DEBUG")) // "Registro detalhado" nas configuracoes
      logger->set_level(spdlog::level::debug);
    logger->flush_on(logger->level());
    spdlog::set_default_logger(logger);
    spdlog::flush_on(logger->level() < spdlog::level::info ? logger->level() : spdlog::level::info);
  } catch(...) {
  }

#ifdef _WIN32
  WSADATA wsa{};
  if(WSAStartup(MAKEWORD(2, 2), &wsa) != 0)
    return 1;
#else
  std::thread(ProximityThread).detach();
#endif

  while(true) {
    SOCKET server = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(AGENT_PORT);
    int opt = 1;
#ifdef _WIN32
    setsockopt(server, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, reinterpret_cast<const char *>(&opt), sizeof(opt));
#else
    setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
#endif
    if(server == INVALID_SOCKET || bind(server, reinterpret_cast<sockaddr *>(&addr), sizeof(addr)) != 0 || listen(server, 8) != 0) {
      spdlog::error("Nao consegui escutar na porta {}. Tentando de novo em 10 s.", AGENT_PORT);
      closesocket(server);
      std::this_thread::sleep_for(std::chrono::seconds(10));
      continue;
    }
    spdlog::info("Agente escutando na porta {}.", AGENT_PORT);
    while(true) {
      sockaddr_in peerAddr{};
      socklen_t peerLen = sizeof(peerAddr);
      SOCKET client = accept(server, reinterpret_cast<sockaddr *>(&peerAddr), &peerLen);
      if(client == INVALID_SOCKET)
        break;
      char ipBuf[INET_ADDRSTRLEN]{};
      inet_ntop(AF_INET, &peerAddr.sin_addr, ipBuf, sizeof(ipBuf));
      std::string peerIp = ipBuf;
      if(!AcquireSlot(peerIp)) {
        RejectBusy(client);
        continue;
      }
      std::thread([client, peerIp]() {
#ifdef _WIN32
        DWORD timeout = 8000;
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char *>(&timeout), sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<const char *>(&timeout), sizeof(timeout));
#else
        timeval timeout{8, 0};
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
#endif
        uint16_t id{};
        std::string payload{};
        if(ReadFrame(client, id, payload)) {
          nlohmann::json response{};
          std::string error{};
          SealInfo seal{};
          try {
            error = Handle(peerIp, id, payload, response, seal);
          } catch(const std::exception &ex) {
            spdlog::error("Erro ao atender {}: {}", peerIp, ex.what());
            error = "DATA_ERROR";
          }
          response["error"] = error;
          std::string out{};
          if(seal.want)
            out = SealResponse(response, seal.key, seal.nonce);
          if(out.empty())
            out = response.dump();
          WriteFrame(client, PACKET_ID_LOCK_RESPONSE, out);
        }
        closesocket(client);
        ReleaseSlot(peerIp);
      }).detach();
    }
    closesocket(server);
  }
  (void)mutex;
  return 0;
}
