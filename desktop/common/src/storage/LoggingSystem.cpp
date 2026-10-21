#include "LoggingSystem.h"

#include <cstdio>
#include <mutex>
#include <spdlog/sinks/rotating_file_sink.h>
#include <spdlog/sinks/stdout_sinks.h>

#include "AppSettings.h"
#include "shell/Shell.h"

#ifdef _WIN32
#include <Windows.h>
#include <aclapi.h>
#include <sddl.h>
#pragma comment(lib, "advapi32.lib")
#else
#include <sys/stat.h>
#include <unistd.h>
#endif

std::string LoggingSystem::g_LogName{};

// Italock: Init/Destroy aninhados (a LogonUI cria um provider por cenario, cada um chamava Init e o Destroy de um
// desligava o spdlog dos outros ainda vivos). So o primeiro Init configura e so o ultimo Destroy desliga.
static std::mutex g_LogRefMutex{};
static int g_LogRefCount = 0;

// Italock: log com teto (1 MB x 3 arquivos, mesmo enquanto a tela de bloqueio fica carregada dias) e com BOM UTF-8
// no arquivo novo, para acentos abrirem certo no Bloco de Notas/PowerShell.
// Italock: registros so para SYSTEM/root e Administradores (tem IPs, nomes de celular e horarios de uso).
// Windows: DACL protegida SYSTEM + Administradores (a pasta de dados da leitura a Usuarios; os .log nao herdam).
// Linux: 600 nos arquivos do root (module.log, do grupo do usuario da protecao de tela, fica como esta).
static void RestrictLogFile(const spdlog::filename_t &name) {
#ifdef _WIN32
  PSECURITY_DESCRIPTOR sd = nullptr;
  if(!ConvertStringSecurityDescriptorToSecurityDescriptorW(L"D:P(A;;FA;;;SY)(A;;FA;;;BA)", SDDL_REVISION_1, &sd, nullptr))
    return;
  BOOL present = FALSE, defaulted = FALSE;
  PACL dacl = nullptr;
  if(GetSecurityDescriptorDacl(sd, &present, &dacl, &defaulted) && present) {
    auto wide = std::filesystem::path(name).wstring();
    SetNamedSecurityInfoW(wide.data(), SE_FILE_OBJECT, DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION, nullptr, nullptr, dacl, nullptr);
  }
  LocalFree(sd);
#else
  struct stat st{};
  std::string path = std::filesystem::path(name).string();
  if(geteuid() == 0 && stat(path.c_str(), &st) == 0 && st.st_uid == 0 && st.st_gid == 0)
    chmod(path.c_str(), 0600);
#endif
}

std::shared_ptr<spdlog::sinks::rotating_file_sink_mt> LoggingSystem::MakeFileSink(const std::string &path, size_t maxSize, size_t maxFiles) {
  spdlog::file_event_handlers handlers{};
  handlers.after_open = [](const spdlog::filename_t &name, std::FILE *file) {
    RestrictLogFile(name);
    static const unsigned char bom[] = {0xEF, 0xBB, 0xBF};
    if(file && std::fseek(file, 0, SEEK_END) == 0 && std::ftell(file) == 0)
      std::fwrite(bom, 1, sizeof(bom), file);
  };
  return std::make_shared<spdlog::sinks::rotating_file_sink_mt>(path, maxSize, maxFiles, false, handlers);
}

void LoggingSystem::Init(const std::string &logName, bool printToConsole) {
  std::lock_guard refLock(g_LogRefMutex);
  if(g_LogRefCount++ > 0)
    return;
  g_LogName = logName;
  auto logPath = AppSettings::GetBaseDir() / fmt::format("{}.log", g_LogName);

  try {
    auto enableDebug = std::filesystem::exists(AppSettings::GetBaseDir() / "LOG_DEBUG");
    auto fileSink = MakeFileSink(logPath.string(), 1024 * 1024, 3);
    fileSink->set_level(enableDebug ? spdlog::level::debug : spdlog::level::info);

    std::shared_ptr<spdlog::logger> loggerPtr{};
    if(printToConsole) {
      auto consoleSink = std::make_shared<spdlog::sinks::stdout_sink_mt>();
      consoleSink->set_level(enableDebug ? spdlog::level::debug : spdlog::level::info);
      auto logger = spdlog::logger("pcbu_logger", {consoleSink, fileSink});
      loggerPtr = std::make_shared<spdlog::logger>(logger);
    } else {
      auto logger = spdlog::logger("pcbu_logger", {fileSink});
      loggerPtr = std::make_shared<spdlog::logger>(logger);
    }

    loggerPtr->set_pattern("[%Y-%m-%d %H:%M:%S.%e] [%l] %v");
    loggerPtr->set_level(spdlog::level::debug);
    spdlog::set_default_logger(loggerPtr);
#ifdef _DEBUG
    spdlog::flush_on(spdlog::level::debug);
#else
    spdlog::flush_on(spdlog::level::info);
#endif

    spdlog::info("Logger init.");
  } catch(const std::exception &ex) {
    spdlog::error("Error initializing logger: {}", ex.what());
  }
}

void LoggingSystem::Destroy() {
  std::lock_guard refLock(g_LogRefMutex);
  if(g_LogRefCount <= 0 || --g_LogRefCount > 0)
    return;
  spdlog::info("Logger destroy.");
  spdlog::shutdown();
  g_LogName = {};
}
