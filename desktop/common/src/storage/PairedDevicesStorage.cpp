#include "PairedDevicesStorage.h"

#include <chrono>
#include <ctime>
#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>
#include <thread>

#include "AppSettings.h"
#include "shell/Shell.h"
#include "utils/StringUtils.h"

#ifdef WINDOWS
#include <Windows.h>
#include <aclapi.h>
#include <sddl.h>
#else
#include <cerrno>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>
#endif

// Italock: o arquivo de pareamentos e lido pela tela de bloqueio (SYSTEM), pelo agente e pelo app ao mesmo tempo.
//  - permissao fixa (so SYSTEM e Administradores; no Linux root 600) - nada de liga-e-desliga de ACL a cada leitura;
//  - gravacao em arquivo temporario + rename atomico (quem le ve o arquivo velho inteiro ou o novo inteiro);
//  - trava entre processos (mutex Global\ItalockDevices / flock) em volta de ler e gravar;
//  - arquivo ilegivel NUNCA e sobrescrito: se estiver corrompido e renomeado para .corrompido-<data>.
#ifdef WINDOWS
// SYSTEM e Administradores, sem heranca.
static constexpr const wchar_t *DEVICES_FILE_SDDL = L"D:P(A;;FA;;;SY)(A;;FA;;;BA)";
static constexpr const wchar_t *DEVICES_MUTEX_SDDL = L"D:(A;;GA;;;SY)(A;;GA;;;BA)";
// uso.json: sem segredo (so horarios e o nome do celular); Usuarios leem para o icone da bandeja (sem administrador)
// avisar quando o celular travou/destravou.
static constexpr const wchar_t *LAST_USE_FILE_SDDL = L"D:P(A;;FA;;;SY)(A;;FA;;;BA)(A;;FR;;;BU)";
#endif

#ifdef WINDOWS
using LockWaitMs = DWORD;
#else
using LockWaitMs = unsigned int;
#endif

namespace {
// Quem so le (tela de bloqueio) espera pouco e le assim mesmo (o rename e atomico): a LogonUI nao pode ficar parada
// esperando o app gravar. Quem grava espera mais e DESISTE se nao conseguir a trava.
constexpr LockWaitMs READ_LOCK_WAIT_MS = 1500;
constexpr LockWaitMs WRITE_LOCK_WAIT_MS = 10000;

// Trava reentrante entre processos (AddDevice le e grava dentro da mesma trava).
class DevicesLock {
public:
  explicit DevicesLock(LockWaitMs timeoutMs) {
    if(t_Depth++ > 0) {
      m_Nested = true;
      return;
    }
#ifdef WINDOWS
    PSECURITY_DESCRIPTOR sd = nullptr;
    SECURITY_ATTRIBUTES sa{sizeof(sa), nullptr, FALSE};
    if(ConvertStringSecurityDescriptorToSecurityDescriptorW(DEVICES_MUTEX_SDDL, SDDL_REVISION_1, &sd, nullptr))
      sa.lpSecurityDescriptor = sd;
    m_Handle = CreateMutexW(&sa, FALSE, L"Global\\ItalockDevices");
    if(!m_Handle)
      m_Handle = OpenMutexW(SYNCHRONIZE, FALSE, L"Global\\ItalockDevices");
    if(sd)
      LocalFree(sd);
    if(!m_Handle) {
      spdlog::warn("Trava dos pareamentos indisponivel ({}).", GetLastError());
      return;
    }
    auto wait = WaitForSingleObject(m_Handle, timeoutMs);
    if(wait == WAIT_OBJECT_0 || wait == WAIT_ABANDONED) {
      m_Owned = true;
    } else {
      spdlog::warn("Trava dos pareamentos ocupada ({} ms).", timeoutMs);
    }
#else
    auto lockPath = (AppSettings::GetBaseDir() / ".paired_devices.lock").string();
    m_Fd = open(lockPath.c_str(), O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    if(m_Fd < 0)
      m_Fd = open(lockPath.c_str(), O_RDONLY | O_CLOEXEC);
    // flock nao tem prazo: tenta sem bloquear em fatias de 50 ms ate timeoutMs.
    unsigned int waited = 0;
    while(m_Fd >= 0 && flock(m_Fd, LOCK_EX | LOCK_NB) != 0) {
      if(errno != EWOULDBLOCK || waited >= timeoutMs) {
        spdlog::warn("Trava dos pareamentos ocupada ({} ms).", timeoutMs);
        close(m_Fd);
        m_Fd = -1;
        break;
      }
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
      waited += 50;
    }
#endif
  }
  // Trava obtida (ou ja obtida antes, na mesma thread).
  [[nodiscard]] bool Owned() const {
    if(m_Nested)
      return true;
#ifdef WINDOWS
    return m_Owned;
#else
    return m_Fd >= 0;
#endif
  }
  ~DevicesLock() {
    if(--t_Depth > 0)
      return;
#ifdef WINDOWS
    if(m_Handle) {
      if(m_Owned)
        ReleaseMutex(m_Handle);
      CloseHandle(m_Handle);
    }
#else
    if(m_Fd >= 0) {
      flock(m_Fd, LOCK_UN);
      close(m_Fd);
    }
#endif
  }
  DevicesLock(const DevicesLock &) = delete;
  DevicesLock &operator=(const DevicesLock &) = delete;

private:
  static thread_local int t_Depth;
  bool m_Nested{};
#ifdef WINDOWS
  HANDLE m_Handle{};
  bool m_Owned{};
#else
  int m_Fd = -1;
#endif
};
thread_local int DevicesLock::t_Depth = 0;

enum class ReadStatus { OK, MISSING, IO_ERROR };

ReadStatus ReadWholeFile(const std::filesystem::path &path, std::string &out) {
#ifdef WINDOWS
  HANDLE file = CreateFileW(path.wstring().c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, nullptr, OPEN_EXISTING,
                            FILE_ATTRIBUTE_NORMAL, nullptr);
  if(file == INVALID_HANDLE_VALUE) {
    auto err = GetLastError();
    if(err == ERROR_FILE_NOT_FOUND || err == ERROR_PATH_NOT_FOUND)
      return ReadStatus::MISSING;
    spdlog::error("Nao consegui abrir os pareamentos ({}).", err);
    return ReadStatus::IO_ERROR;
  }
  LARGE_INTEGER size{};
  if(!GetFileSizeEx(file, &size) || size.QuadPart > 16 * 1024 * 1024) {
    CloseHandle(file);
    return ReadStatus::IO_ERROR;
  }
  out.resize(static_cast<size_t>(size.QuadPart));
  DWORD total = 0;
  while(total < out.size()) {
    DWORD got = 0;
    if(!ReadFile(file, out.data() + total, static_cast<DWORD>(out.size() - total), &got, nullptr) || got == 0)
      break;
    total += got;
  }
  CloseHandle(file);
  if(total != out.size())
    return ReadStatus::IO_ERROR;
  return ReadStatus::OK;
#else
  int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
  if(fd < 0) {
    if(errno == ENOENT)
      return ReadStatus::MISSING;
    spdlog::error("Nao consegui abrir os pareamentos ({}).", errno);
    return ReadStatus::IO_ERROR;
  }
  out.clear();
  char buf[4096];
  ssize_t r;
  while((r = read(fd, buf, sizeof(buf))) > 0)
    out.append(buf, static_cast<size_t>(r));
  close(fd);
  return r < 0 ? ReadStatus::IO_ERROR : ReadStatus::OK;
#endif
}

// Grava em <arquivo>.tmp ja com a permissao final e troca de uma vez.
// publicRead: Windows = Usuarios podem ler (uso.json); Linux = 0644.
bool AtomicWrite(const std::filesystem::path &path, const std::string &data, bool publicRead = false) {
  auto tmpPath = path;
  tmpPath += ".tmp";
#ifdef WINDOWS
  PSECURITY_DESCRIPTOR sd = nullptr;
  if(!ConvertStringSecurityDescriptorToSecurityDescriptorW(publicRead ? LAST_USE_FILE_SDDL : DEVICES_FILE_SDDL, SDDL_REVISION_1, &sd, nullptr)) {
    spdlog::error("Descritor de seguranca invalido ({}).", GetLastError());
    return false;
  }
  SECURITY_ATTRIBUTES sa{sizeof(sa), sd, FALSE};
  DeleteFileW(tmpPath.wstring().c_str()); // sobra de gravacao interrompida: recria com dono e permissao certos
  HANDLE file = CreateFileW(tmpPath.wstring().c_str(), GENERIC_WRITE, 0, &sa, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
  if(file == INVALID_HANDLE_VALUE) {
    spdlog::error("Nao consegui criar {} ({}).", tmpPath.string(), GetLastError());
    LocalFree(sd);
    return false;
  }
  DWORD written = 0;
  bool ok = WriteFile(file, data.data(), static_cast<DWORD>(data.size()), &written, nullptr) && written == data.size() && FlushFileBuffers(file);
  CloseHandle(file);
  LocalFree(sd);
  if(!ok) {
    spdlog::error("Falha ao gravar {}.", tmpPath.string());
    DeleteFileW(tmpPath.wstring().c_str());
    return false;
  }
  for(int i = 0; i < 20; i++) {
    if(MoveFileExW(tmpPath.wstring().c_str(), path.wstring().c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH))
      return true;
    std::this_thread::sleep_for(std::chrono::milliseconds(50)); // alguem lendo sem compartilhar exclusao
  }
  spdlog::error("Nao consegui trocar {} ({}).", path.string(), GetLastError());
  DeleteFileW(tmpPath.wstring().c_str());
  return false;
#else
  int fd = open(tmpPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
  if(fd < 0) {
    spdlog::error("Nao consegui criar {} ({}).", tmpPath.string(), errno);
    return false;
  }
  fchmod(fd, publicRead ? 0644 : 0600);
  if(fchown(fd, 0, 0) != 0) {
    // sem root: fica com o dono atual
  }
  size_t done = 0;
  bool ok = true;
  while(done < data.size()) {
    auto w = write(fd, data.data() + done, data.size() - done);
    if(w <= 0) {
      ok = false;
      break;
    }
    done += static_cast<size_t>(w);
  }
  ok = ok && fsync(fd) == 0;
  close(fd);
  if(!ok || rename(tmpPath.c_str(), path.c_str()) != 0) {
    spdlog::error("Falha ao gravar {} ({}).", path.string(), errno);
    unlink(tmpPath.c_str());
    return false;
  }
  return true;
#endif
}

void SetAsideCorrupted(const std::filesystem::path &path) {
  auto now = std::time(nullptr);
  std::tm tm{};
#ifdef WINDOWS
  localtime_s(&tm, &now);
#else
  localtime_r(&now, &tm);
#endif
  char stamp[32]{};
  std::strftime(stamp, sizeof(stamp), "%Y%m%d-%H%M%S", &tm);
  auto target = path;
  target += fmt::format(".corrompido-{}", stamp);
  std::error_code ec{};
  std::filesystem::rename(path, target, ec);
  if(ec)
    spdlog::error("Nao consegui separar o arquivo corrompido: {}", ec.message());
  else
    spdlog::error("Arquivo de pareamentos ilegivel guardado como {}.", target.string());
}
} // namespace

std::optional<PairedDevice> PairedDevicesStorage::GetDeviceByID(const std::string &id) {
  for(const auto &device : GetDevices())
    if(device.id == id)
      return device;
  return {};
}

std::vector<PairedDevice> PairedDevicesStorage::GetDevicesForUser(const std::string &userName) {
  std::vector<PairedDevice> result{};
#ifdef WINDOWS
  auto userNameLower = StringUtils::ToLower(userName);
  std::string principalName{};
  auto userSplit = StringUtils::Split(userName, "\\");
  if(userSplit.size() == 2 && userSplit[1].find('@') != std::string::npos)
    principalName = StringUtils::ToLower(userSplit[1]);
#endif
  for(const auto &device : GetDevices()) {
#ifdef WINDOWS
    auto deviceUserName = StringUtils::ToLower(device.userName);
    if(deviceUserName == userNameLower || (!principalName.empty() && deviceUserName == principalName))
      result.emplace_back(device);
#else
    if(device.userName == userName)
      result.emplace_back(device);
#endif
  }
  return result;
}

bool PairedDevicesStorage::AddDevice(const PairedDevice &device) {
  DevicesLock lock(WRITE_LOCK_WAIT_MS);
  std::vector<PairedDevice> devices{};
  if(!lock.Owned() || !LoadDevices(devices))
    return false; // sem trava ou arquivo ilegivel: nao grava por cima
  auto rmRange = std::ranges::remove_if(devices, [&](const PairedDevice &d) { return d.id == device.id; });
  devices.erase(rmRange.begin(), rmRange.end());
  devices.emplace_back(device);
  return PairedDevicesStorage::SaveDevices(devices);
}

bool PairedDevicesStorage::RemoveDevice(const std::string &id) {
  return RemoveDevicesIf([&](const PairedDevice &d) { return d.id == id; });
}

bool PairedDevicesStorage::RemoveDevicesIf(const std::function<bool(const PairedDevice &)> &pred) {
  DevicesLock lock(WRITE_LOCK_WAIT_MS);
  std::vector<PairedDevice> devices{};
  if(!lock.Owned() || !LoadDevices(devices))
    return false;
  if(std::erase_if(devices, pred) > 0)
    return PairedDevicesStorage::SaveDevices(devices);
  return true;
}

bool PairedDevicesStorage::UpdateDevice(const std::string &id, const std::function<void(PairedDevice &)> &change) {
  DevicesLock lock(WRITE_LOCK_WAIT_MS);
  std::vector<PairedDevice> devices{};
  if(!lock.Owned() || !LoadDevices(devices))
    return false;
  bool found = false;
  for(auto &d : devices) {
    if(d.id == id) {
      change(d);
      found = true;
    }
  }
  return found && PairedDevicesStorage::SaveDevices(devices);
}

std::vector<PairedDevice> PairedDevicesStorage::GetDevices() {
  std::vector<PairedDevice> result{};
  LoadDevices(result); // so leitura: em erro fica vazio. Quem grava usa LoadDevices e desiste.
  return result;
}

bool PairedDevicesStorage::LoadDevices(std::vector<PairedDevice> &result) {
  result.clear();
  DevicesLock lock(READ_LOCK_WAIT_MS);
  auto filePath = AppSettings::GetBaseDir() / DEVICES_FILE_NAME;
  std::string jsonData{};
  auto status = ReadWholeFile(filePath, jsonData);
  if(status == ReadStatus::MISSING)
    return true; // nenhum pareamento ainda
  if(status != ReadStatus::OK)
    return false; // erro de leitura: ninguem grava por cima
  nlohmann::json json{};
  try {
    json = nlohmann::json::parse(jsonData);
  } catch(const std::exception &ex) {
    spdlog::error("Pareamentos ilegiveis: {}", ex.what());
    SetAsideCorrupted(filePath);
    return false;
  }
  if(!json.is_array()) {
    spdlog::error("Pareamentos em formato inesperado.");
    SetAsideCorrupted(filePath);
    return false;
  }
  for(const auto &entry : json) {
    try {
      auto device = PairedDevice();
      device.id = entry.value("id", std::string{});
      device.pairingMethod = PairingMethodUtils::FromString(entry.value("pairingMethod", std::string{}));
      device.deviceName = StringUtils::SafeDeviceName(entry.value("deviceName", std::string{})); // Italock: pareamentos antigos tambem
      device.userName = entry.value("userName", std::string{});
      device.passwordEnc = entry.value("passwordEnc", std::string{});
      device.encryptionKey = entry.value("encryptionKey", std::string{});

      device.ipAddress = entry.value("ipAddress", std::string{});
      device.bluetoothAddress = entry.value("bluetoothAddress", std::string{});
      device.cloudToken = entry.value("cloudToken", std::string{});
      device.tcpPort = entry.value("tcpPort", uint16_t{});
      device.udpPort = entry.value("udpPort", uint16_t{});
      device.udpManualPort = entry.value("udpManualPort", uint16_t{});
      if(entry.contains("ipAddresses") && entry["ipAddresses"].is_array())
        for(const auto &ip : entry["ipAddresses"])
          if(ip.is_string())
            device.ipAddresses.emplace_back(ip.get<std::string>());
      if(!device.id.empty())
        result.emplace_back(device);
    } catch(const std::exception &ex) {
      spdlog::error("Pareamento com dado invalido ignorado: {}", ex.what());
    }
  }
  return true;
}

bool PairedDevicesStorage::SaveDevices(const std::vector<PairedDevice> &devices) {
  try {
    auto devicesJson = nlohmann::json::array();
    for(const auto &device : devices) {
      nlohmann::json deviceJson = {{"id", device.id},
                                   {"pairingMethod", PairingMethodUtils::ToString(device.pairingMethod)},
                                   {"deviceName", device.deviceName},
                                   {"userName", device.userName},
                                   {"passwordEnc", device.passwordEnc},
                                   {"encryptionKey", device.encryptionKey},

                                   {"ipAddress", device.ipAddress},
                                   {"tcpPort", device.tcpPort},
                                   {"udpPort", device.udpPort},
                                   {"udpManualPort", device.udpManualPort},
                                   {"bluetoothAddress", device.bluetoothAddress},
                                   {"cloudToken", device.cloudToken},
                                   {"ipAddresses", device.ipAddresses}};
      devicesJson.emplace_back(deviceJson);
    }
    auto baseDir = AppSettings::GetBaseDir();
    if(!std::filesystem::exists(baseDir))
      Shell::CreateDir(baseDir);
    DevicesLock lock(WRITE_LOCK_WAIT_MS);
    if(!lock.Owned()) {
      spdlog::error("Trava dos pareamentos ocupada; nada gravado.");
      return false;
    }
    return AtomicWrite(baseDir / DEVICES_FILE_NAME, devicesJson.dump());
  } catch(const std::exception &ex) {
    spdlog::error("Falha ao gravar os pareamentos: {}", ex.what());
  }
  return false;
}

// Italock: "ultimo uso" de cada celular num arquivo a parte (uso.json), para nunca arriscar o arquivo de pareamentos
// por causa de uma informacao de tela. Melhor esforco: se falhar, so nao aparece (mas fica no registro).
// Formato: {"<id>": <segundos desde 1970>, ..., "_ultimo": {"id","nome","acao","caminho","ts"},
//           "historico": [{"ts","id","nome","acao","resultado","caminho","programa"}, ...]}.
// "_ultimo" e o que o icone da bandeja le para avisar (acao: "travar", "destravar" ou "acordar"; so o que deu certo).
// "historico" guarda os ultimos HISTORY_MAX eventos (tambem recusados/expirados), do mais antigo ao mais novo.
// Varios processos gravam (tela de bloqueio, agente com varias threads, pcbu_auth): ler, alterar e gravar acontece
// dentro da trava dos pareamentos (o arquivo temporario tem nome fixo; dois escritores juntos se atropelavam).
static constexpr std::string_view LAST_USE_FILE_NAME = "uso.json";

namespace {
nlohmann::json ReadUseFile(const std::filesystem::path &path) {
  nlohmann::json json = nlohmann::json::object();
  std::string data{};
  if(ReadWholeFile(path, data) == ReadStatus::OK) {
    try {
      json = nlohmann::json::parse(data);
    } catch(...) {
    }
    if(!json.is_object())
      json = nlohmann::json::object();
  }
  return json;
}

// Texto que vai para arquivo lido pela bandeja: sem controle, curto.
std::string CleanField(const std::string &s, size_t max = 80) {
  std::string out{};
  for(unsigned char ch : s) {
    if(ch < 0x20 || ch == 0x7F)
      continue;
    out += static_cast<char>(ch);
    if(out.size() >= max)
      break;
  }
  // nao corta um caractere UTF-8 no meio
  while(!out.empty() && (static_cast<unsigned char>(out.back()) & 0xC0) == 0x80)
    out.pop_back();
  if(!out.empty() && (static_cast<unsigned char>(out.back()) & 0xC0) == 0xC0)
    out.pop_back();
  return out;
}

void WriteUseEvent(const std::string &id, const std::string &action, const std::string &result, const std::string &path,
                   const std::string &program, bool updateLast) {
  try {
    std::string name{};
    if(!id.empty())
      for(const auto &d : PairedDevicesStorage::GetDevices())
        if(d.id == id)
          name = d.deviceName;
    DevicesLock lock(READ_LOCK_WAIT_MS); // a tela de bloqueio nao pode ficar parada esperando
    if(!lock.Owned()) {
      spdlog::warn("Uso nao registrado: trava ocupada.");
      return;
    }
    auto filePath = AppSettings::GetBaseDir() / LAST_USE_FILE_NAME;
    auto json = ReadUseFile(filePath);
    auto now = static_cast<int64_t>(std::time(nullptr));
    if(updateLast && !id.empty()) {
      json[id] = now;
      json["_ultimo"] = {{"id", id}, {"nome", CleanField(name)}, {"acao", action}, {"caminho", path}, {"ts", now}};
    }
    nlohmann::json history = json.contains("historico") && json["historico"].is_array() ? json["historico"] : nlohmann::json::array();
    history.push_back({{"ts", now},
                       {"id", id},
                       {"nome", CleanField(name)},
                       {"acao", action},
                       {"resultado", result},
                       {"caminho", CleanField(path, 40)},
                       {"programa", CleanField(program, 60)}});
    while(history.size() > PairedDevicesStorage::HISTORY_MAX)
      history.erase(history.begin());
    json["historico"] = std::move(history);
    if(!AtomicWrite(filePath, json.dump(), true))
      spdlog::warn("Nao consegui registrar o uso em {}.", filePath.string());
  } catch(const std::exception &ex) {
    spdlog::warn("Nao consegui registrar o uso: {}", ex.what());
  }
}
} // namespace

void PairedDevicesStorage::RecordUse(const std::string &id, const std::string &action, const std::string &path, const std::string &program) {
  WriteUseEvent(id, action, "ok", path, program, true);
}

void PairedDevicesStorage::RecordEvent(const std::string &id, const std::string &action, const std::string &result, const std::string &path,
                                       const std::string &program) {
  WriteUseEvent(id, action, result, path, program, false);
}

std::vector<PairedDevicesStorage::UseEvent> PairedDevicesStorage::GetHistory() {
  std::vector<UseEvent> result{};
  try {
    auto json = ReadUseFile(AppSettings::GetBaseDir() / LAST_USE_FILE_NAME);
    if(!json.contains("historico") || !json["historico"].is_array())
      return result;
    for(const auto &e : json["historico"]) {
      if(!e.is_object())
        continue;
      UseEvent ev{};
      ev.ts = e.value("ts", int64_t{});
      ev.id = e.value("id", std::string{});
      ev.name = e.value("nome", std::string{});
      ev.action = e.value("acao", std::string{});
      ev.result = e.value("resultado", std::string{});
      ev.path = e.value("caminho", std::string{});
      ev.program = e.value("programa", std::string{});
      result.emplace_back(std::move(ev));
    }
    if(result.size() > HISTORY_MAX)
      result.erase(result.begin(), result.end() - HISTORY_MAX);
  } catch(...) {
  }
  return result;
}

bool PairedDevicesStorage::ClearHistory() {
  try {
    DevicesLock lock(WRITE_LOCK_WAIT_MS);
    if(!lock.Owned())
      return false;
    auto filePath = AppSettings::GetBaseDir() / LAST_USE_FILE_NAME;
    auto json = ReadUseFile(filePath);
    if(!json.contains("historico"))
      return true;
    json.erase("historico");
    return AtomicWrite(filePath, json.dump(), true);
  } catch(const std::exception &ex) {
    spdlog::warn("Nao consegui limpar o historico: {}", ex.what());
  }
  return false;
}

std::map<std::string, int64_t> PairedDevicesStorage::GetLastUses() {
  std::map<std::string, int64_t> result{};
  try {
    std::string data{};
    if(ReadWholeFile(AppSettings::GetBaseDir() / LAST_USE_FILE_NAME, data) != ReadStatus::OK)
      return result;
    auto json = nlohmann::json::parse(data);
    if(!json.is_object())
      return result;
    for(auto it = json.begin(); it != json.end(); ++it)
      if(it.value().is_number_integer())
        result[it.key()] = it.value().get<int64_t>();
  } catch(...) {
  }
  return result;
}
