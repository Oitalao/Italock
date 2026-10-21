#include "Shell.h"

#include <boost/filesystem.hpp>
#ifdef WINDOWS
#include <boost/process/v1.hpp>
#else
#include <boost/asio.hpp>
#include <boost/process/v2/environment.hpp>
#include <boost/process/v2/process.hpp>
#include <boost/process/v2/stdio.hpp>
#endif
#include <fstream>
#ifndef WINDOWS
#include <cerrno>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#endif
#include <spdlog/spdlog.h>

#include "utils/StringUtils.h"

#ifdef WINDOWS
#include <boost/process/v1/windows.hpp>
//#include <boost/process/v2/windows/creation_flags.hpp>
#undef CreateFile

#define SHELL_NAME "cmd.exe"
#define SHELL_CMD_ARG "/c"
#elif LINUX
#define SHELL_NAME "bash"
#define SHELL_CMD_ARG "-c"
#elif APPLE
#define SHELL_NAME "zsh"
#define SHELL_CMD_ARG "-c"
#endif

bool Shell::IsRunningAsAdmin() {
#ifdef WINDOWS
  BOOL isAdmin = FALSE;
  PSID adminGroup = nullptr;
  SID_IDENTIFIER_AUTHORITY ntAuthority = SECURITY_NT_AUTHORITY;
  if(!AllocateAndInitializeSid(&ntAuthority, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_ADMINS, 0, 0, 0, 0, 0, 0, &adminGroup)) {
    spdlog::error("AllocateAndInitializeSid failed. (Code={})", GetLastError());
    return false;
  }
  if(!CheckTokenMembership(nullptr, adminGroup, &isAdmin)) {
    spdlog::error("CheckTokenMembership failed. (Code={})", GetLastError());
    isAdmin = FALSE;
  }
  if(adminGroup)
    FreeSid(adminGroup);
  return isAdmin;
#else
  return geteuid() == 0;
#endif
}

ShellCmdResult Shell::RunCommand(const std::string &cmd) {
  /*boost::process::child proc(boost::process::search_path("osascript"), ToDo
                             std::vector<std::string> {"-e", "do shell script \"echo test\" with administrator privileges"},
                             boost::process::std_out > outStream);*/
  return RunUserCommand(cmd);
}

ShellCmdResult Shell::RunUserCommand(const std::string &cmd) {
#ifdef WINDOWS
  boost::process::v1::ipstream outStream{};
  boost::process::v1::ipstream errStream{};
  boost::process::v1::child proc(fmt::format("{0} {1} \"{2}\"", SHELL_NAME, SHELL_CMD_ARG, cmd), boost::process::v1::std_out > outStream,
                                 boost::process::v1::std_err > errStream, boost::process::v1::windows::create_no_window);
  std::string output{};
  std::string line{};
  while(outStream && std::getline(outStream, line) && !line.empty())
    output.append(line + "\n");
  while(errStream && std::getline(errStream, line) && !line.empty())
    output.append(line + "\n");
  proc.wait();
#else
  boost::asio::io_context ctx{};
  boost::asio::readable_pipe pipe{ctx};
  boost::process::v2::process proc(ctx, boost::process::v2::environment::find_executable(SHELL_NAME), {SHELL_CMD_ARG, cmd},
                                   boost::process::v2::process_stdio{{}, pipe, pipe}

#ifdef WINDOWS
                                   ,
                                   boost::process::v2::windows::process_creation_flags<CREATE_NO_WINDOW>{}
#endif
  );

  std::string output{};
  boost::system::error_code ec;
  boost::asio::read(pipe, boost::asio::dynamic_buffer(output), ec);
  proc.wait();
#endif

  spdlog::debug("Process exit. Code: {} Command: '{}' Output: '{}'", proc.exit_code(), cmd, StringUtils::Trim(output));
  auto result = ShellCmdResult();
  result.exitCode = proc.exit_code();
  result.output = output;
  return result;
}

void Shell::SpawnCommand(const std::string &cmd) {
#ifdef WINDOWS
  boost::process::v1::child proc(fmt::format("{0} {1} \"{2}\"", SHELL_NAME, SHELL_CMD_ARG, cmd), boost::process::v1::windows::create_no_window);
  proc.detach();
#else
  boost::asio::io_context ctx{};
  boost::process::v2::process proc(ctx, boost::process::v2::environment::find_executable(SHELL_NAME), {SHELL_CMD_ARG, cmd}
#ifdef WINDOWS
                                   ,
                                   boost::process::v2::windows::process_creation_flags<CREATE_NO_WINDOW>{}
#endif
  );
  proc.detach();
#endif
}

bool Shell::CreateDir(const std::filesystem::path &path) {
  boost::system::error_code ec{};
  boost::filesystem::create_directories(boost::filesystem::path(path), ec);
  return !ec.failed();
  /*#ifdef WINDOWS
      return RunCommand(fmt::format("mkdir \"{}\"", path.string())).exitCode == 0;
  #else
      return RunCommand(fmt::format("mkdir -p \"{}\"", path.string())).exitCode == 0;
  #endif*/
}

bool Shell::RemoveDir(const std::filesystem::path &path) {
  boost::system::error_code ec{};
  boost::filesystem::remove(boost::filesystem::path(path), ec);
  return !ec.failed();
  /*#ifdef WINDOWS
      return RunCommand(fmt::format("rd /s /q \"{}\"", path.string())).exitCode == 0;
  #else
      return RunCommand(fmt::format("rm -R \"{}\"", path.string())).exitCode == 0;
  #endif*/
}

bool Shell::CreateFile(const std::filesystem::path &path) {
  std::ofstream file(path);
  return file.is_open();
}

bool Shell::RemoveFile(const std::filesystem::path &path) {
  boost::system::error_code ec{};
  boost::filesystem::remove(boost::filesystem::path(path), ec);
  return !ec.failed();
  /*#ifdef WINDOWS
      return RunCommand(fmt::format("del \"{}\"", path.string())).exitCode == 0;
  #else
      return RunCommand(fmt::format("rm \"{}\"", path.string())).exitCode == 0;
  #endif*/
}

std::vector<uint8_t> Shell::ReadBytes(const std::filesystem::path &path) {
  std::ifstream file{};
  file.open(path, std::ios_base::binary);
  if(!file) {
    spdlog::error("Failed to open file '{}'.", path.string());
    return {};
  }
  return {std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>()};
}

// Italock: grava num temporario no mesmo diretorio e troca de uma vez (rename). Quem esta usando o arquivo antigo (a
// biblioteca PAM carregada, a tela de bloqueio lendo a configuracao) continua vendo o antigo inteiro: nada e truncado
// no lugar. No Linux o arquivo novo herda permissao e dono do antigo (ou 0644 se nao existia).
bool Shell::WriteBytes(const std::filesystem::path &path, const std::vector<uint8_t> &data) {
  auto tmpPath = path;
  tmpPath += ".italock-tmp";
#ifdef WINDOWS
  const auto tmpW = tmpPath.wstring();
  DeleteFileW(tmpW.c_str());
  HANDLE file = CreateFileW(tmpW.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
  if(file == INVALID_HANDLE_VALUE) {
    spdlog::error("Nao consegui criar '{}' ({}).", tmpPath.string(), GetLastError());
    return false;
  }
  DWORD written = 0;
  bool ok = data.empty() || (WriteFile(file, data.data(), static_cast<DWORD>(data.size()), &written, nullptr) && written == data.size());
  ok = ok && FlushFileBuffers(file);
  CloseHandle(file);
  if(ok && MoveFileExW(tmpW.c_str(), path.wstring().c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH))
    return true;
  spdlog::error("Nao consegui gravar '{}' ({}).", path.string(), GetLastError());
  DeleteFileW(tmpW.c_str());
  return false;
#else
  struct stat oldSt{};
  const bool hadOld = stat(path.c_str(), &oldSt) == 0;
  int fd = open(tmpPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
  if(fd < 0) {
    spdlog::error("Nao consegui criar '{}' ({}).", tmpPath.string(), errno);
    return false;
  }
  bool ok = true;
  size_t done = 0;
  while(done < data.size()) {
    auto w = write(fd, data.data() + done, data.size() - done);
    if(w < 0 && errno == EINTR)
      continue;
    if(w <= 0) {
      ok = false;
      break;
    }
    done += static_cast<size_t>(w);
  }
  if(ok) {
    if(hadOld) {
      if(fchown(fd, oldSt.st_uid, oldSt.st_gid) != 0) {
        // sem root: fica com o dono atual
      }
      fchmod(fd, oldSt.st_mode & 07777);
    } else {
      fchmod(fd, 0644);
    }
    ok = fsync(fd) == 0;
  }
  close(fd);
  if(!ok || rename(tmpPath.c_str(), path.c_str()) != 0) {
    spdlog::error("Nao consegui gravar '{}' ({}).", path.string(), errno);
    unlink(tmpPath.c_str());
    return false;
  }
  return true;
#endif
}
