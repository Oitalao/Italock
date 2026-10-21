#include <boost/asio.hpp>
#include <boost/process/v2/posix/bind_fd.hpp>
#include <boost/process/v2/process.hpp>
#include <boost/process/v2/stdio.hpp>
#include <spdlog/spdlog.h>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>

#define PAM_SM_AUTH
#include <security/pam_modules.h>

#ifdef APPLE
#include <security/pam_appl.h>
#endif

constexpr auto PCBU_AUTH_PATH = "/usr/local/sbin/pcbu_auth";
constexpr int PASSWORD_PIPE = 3;

static int print_pam(struct pam_conv *conv, const std::string &message) {
  struct pam_message msg{};
  struct pam_response *resp = nullptr;
  const struct pam_message *pMsg = &msg;
  if(!conv)
    return PAM_CONV_ERR;

  msg.msg = const_cast<char *>(message.c_str());
  msg.msg_style = PAM_TEXT_INFO;
  conv->conv(1, &pMsg, &resp, conv->appdata_ptr);
  // Italock: a resposta e alocada pela conversa e e de quem chamou (cada mensagem vazava; o modulo vive dentro da
  // protecao de tela por dias).
  if(resp) {
    free(resp[0].resp);
    free(resp);
  }
  return PAM_SUCCESS;
}

int pam_sm_authenticate(pam_handle_t *pamh, int flags, int argc, const char **argv) {
  const char *userName = nullptr;
  const char *serviceName = nullptr;
  struct pam_conv *conv = nullptr;
  auto statusCode = pam_get_item(pamh, PAM_SERVICE, reinterpret_cast<const void **>(&serviceName));
  if(statusCode == PAM_SUCCESS) {
    statusCode = pam_get_user(pamh, (const char **)&userName, nullptr);
    if(statusCode == PAM_SUCCESS) {
      statusCode = pam_get_item(pamh, PAM_CONV, (const void **)&conv);
    }
  }
  if(statusCode != PAM_SUCCESS) {
    spdlog::error("Failed to get PAM user info.");
    return PAM_IGNORE;
  }

  // Italock: as duas pontas do pipe da senha sao fechadas em qualquer caminho (inclusive excecao): o modulo vive dentro
  // de processos longos (protecao de tela, sshd) e cada tentativa vazava dois descritores.
  struct PipeGuard {
    int fd[2]{-1, -1};
    void CloseWrite() {
      if(fd[1] >= 0) {
        close(fd[1]);
        fd[1] = -1;
      }
    }
    ~PipeGuard() {
      CloseWrite();
      if(fd[0] >= 0)
        close(fd[0]);
    }
  } pipeGuard{};
  int *pipeFd = pipeGuard.fd;
  bool hasPipe = false;
  if(!pipe(pipeFd)) {
    fcntl(pipeFd[0], F_SETFD, FD_CLOEXEC);
    fcntl(pipeFd[1], F_SETFD, FD_CLOEXEC);
    hasPipe = true;
  } else {
    pipeFd[0] = pipeFd[1] = -1;
    spdlog::error("Failed to create password pipe.");
  }

  int pamResult = PAM_IGNORE;
  try {
    std::vector<std::string> args{};
    args.emplace_back(userName);

    boost::asio::io_context ctx{};
    boost::asio::readable_pipe outPipe{ctx};
    boost::process::v2::process proc =
        hasPipe ? boost::process::v2::process(ctx, PCBU_AUTH_PATH, args, boost::process::v2::process_stdio{{}, outPipe, {}},
                                              boost::process::v2::posix::bind_fd(PASSWORD_PIPE, pipeFd[1]))
                : boost::process::v2::process(ctx, PCBU_AUTH_PATH, args, boost::process::v2::process_stdio{{}, outPipe, {}});
    boost::system::error_code ec;
    std::vector<char> charBuffer(4096);
    std::string lineBuffer{};
    while(true) {
      auto n = outPipe.read_some(boost::asio::buffer(charBuffer), ec);
      if(ec)
        break;
      lineBuffer.append(charBuffer.data(), n);
      std::size_t pos{};
      while((pos = lineBuffer.find('\n')) != std::string::npos) {
        std::string line = lineBuffer.substr(0, pos);
        lineBuffer.erase(0, pos + 1);
        if(!line.empty() && line.back() == '\r')
          line.pop_back();
        if(!line.empty()) {
          print_pam(conv, line);
        } else {
          break;
        }
      }
    }
    proc.wait();

    std::string password{};
    if(hasPipe) {
      pipeGuard.CloseWrite();
      char buffer[512]{};
      ssize_t bytesRead{};
      while((bytesRead = read(pipeFd[0], buffer, sizeof(buffer))) > 0) {
        password.append(buffer, static_cast<size_t>(bytesRead));
        if(password.size() > 4096)
          break;
      }
      explicit_bzero(buffer, sizeof(buffer));
    }

    auto result = proc.exit_code();
    if(result == 0) {
      if(!password.empty() && password[0] != '\0') {
        pam_set_item(pamh, PAM_AUTHTOK, password.c_str());
      } else {
        pamResult = PAM_SUCCESS;
      }
    }
    if(result == 1) {
      pamResult = PAM_AUTH_ERR;
    }
    if(!password.empty())
      explicit_bzero(password.data(), password.size());
  } catch(const std::exception &ex) {
    spdlog::error("Installation is corrupt. {}", ex.what());
  }
  return pamResult;
}

int pam_sm_setcred(pam_handle_t *pamh, int flags, int argc, const char **argv) {
  return PAM_SUCCESS;
}
