#include <cctype>
#include <cstring>
#include <pwd.h>
#include <unistd.h>

#include "handler/UnlockHandler.h"
#include "platform/PlatformHelper.h"
#include "storage/AppSettings.h"
#include "storage/LoggingSystem.h"
#include "utils/StringUtils.h"

#ifdef LINUX
#include "shell/Shell.h"
#elif APPLE
#include <sys/sysctl.h>
#endif

constexpr int PASSWORD_PIPE = 3;

// Italock: o nome do programa que pediu a senha vai para a tela do celular. Vem do processo pai (qualquer programa do
// usuario pode chamar o pcbu_auth), entao NUNCA vai cru: programas conhecidos viram um nome amigavel em portugues; o resto
// vira so o nome do executavel, limpo (letras, numeros, ponto e espaco) e curto. Argumentos da linha de comando nunca vao.
static std::string FriendlyServiceName(const std::string &exeName) {
  struct Known {
    const char *needle;
    const char *name;
  };
  static const Known known[] = {
      {"cinnamon-screensaver", "Proteção de tela"}, {"mate-screensaver", "Proteção de tela"}, {"xfce4-screensaver", "Proteção de tela"},
      {"gnome-screensaver", "Proteção de tela"},    {"light-locker", "Proteção de tela"},     {"xscreensaver", "Proteção de tela"},
      {"hyprlock", "Proteção de tela"},             {"swaylock", "Proteção de tela"},         {"i3lock", "Proteção de tela"},
      {"polkit-agent-helper", "Janela de senha de administrador"}, {"pkexec", "Janela de senha de administrador"},
      {"sudo", "sudo no terminal"},                 {"sshd", "Sessão SSH"},                   {"lightdm", "Tela de login"},
      {"gdm", "Tela de login"},                     {"sddm", "Tela de login"},                {"login", "Entrada no terminal"},
  };
  for(const auto &k : known)
    if(exeName.find(k.needle) != std::string::npos)
      return k.name;
  if(exeName == "su")
    return "su no terminal";
  std::string clean{};
  for(unsigned char ch : exeName) {
    if(std::isalnum(ch) || ch == '.')
      clean += static_cast<char>(ch);
    else if(ch == '-' || ch == '_' || ch == ' ')
      clean += ' ';
    if(clean.size() >= 32)
      break;
  }
  clean = StringUtils::Trim(clean);
  return clean.empty() ? std::string("Programa do computador") : clean;
}

std::string GetServiceName() {
#if defined(LINUX) || defined(APPLE)
  std::string exe{};
#ifdef LINUX
  auto cmdline = Shell::ReadBytes(fmt::format("/proc/{}/cmdline", getppid()));
  exe.assign(reinterpret_cast<const char *>(cmdline.data()), strnlen(reinterpret_cast<const char *>(cmdline.data()), cmdline.size()));
#elif APPLE
  int mib[] = {CTL_KERN, KERN_PROCARGS2, getppid()};
  size_t size{};
  if(sysctl(mib, 3, nullptr, &size, nullptr, 0) == 0 && size > sizeof(int)) {
    std::vector<char> args(size);
    if(sysctl(mib, 3, args.data(), &size, nullptr, 0) == 0)
      exe.assign(args.data() + sizeof(int), strnlen(args.data() + sizeof(int), size - sizeof(int)));
  }
#endif
  if(auto slash = exe.rfind('/'); slash != std::string::npos)
    exe = exe.substr(slash + 1);
  return FriendlyServiceName(exe);
#else
  return "Programa do computador";
#endif
}

// Italock: o pcbu_auth e setuid root e devolve a senha pelo fd 3. Qualquer usuario pode executa-lo, entao so atende
// quem pede pelo PROPRIO usuario (ou root, como sshd/lightdm). Sem isso, um usuario comum pediria o destravar em nome
// de outro e, com o celular aprovando por engano, receberia a senha dele em texto.
static bool CallerMayAuthenticate(const std::string &userName) {
  if(userName.empty() || userName.size() > 256)
    return false;
  auto *target = getpwnam(userName.c_str());
  if(!target)
    return false;
  const uid_t caller = getuid();
  if(caller == 0)
    return true;
  auto *self = getpwuid(caller);
  return self && self->pw_name && userName == self->pw_name && target->pw_uid == caller;
}

int runMain(int argc, char *argv[]) {
  if(argc != 2) {
    printf("Invalid parameters.\n");
    return -1;
  }

  std::string userName = argv[1];
  auto handler = UnlockHandler([](const std::string &s) { printf("%s\n", s.c_str()); });
  handler.SetRecordHistory(true);
  auto result = handler.GetResult(userName, GetServiceName());
  if(result.state == UnlockState::SUCCESS) {
    if(userName == result.device.userName && PlatformHelper::CheckLogin(userName, result.password).result == PlatformLoginResult::SUCCESS) {
      if(AppSettings::Get().unixSetPasswordPAM) {
        size_t bytesWritten{};
        while(bytesWritten < result.password.size()) {
          auto count = write(PASSWORD_PIPE, result.password.data() + bytesWritten, result.password.size() - bytesWritten);
          if(count == -1)
            break;
          bytesWritten += count;
        }
      }
      return 0;
    }

    auto errorMsg = I18n::Get("error_password");
    printf("%s\n", errorMsg.c_str());
    return 1;
  }
  if(result.state == UnlockState::CANCELED) {
    return 1;
  }
  return -1;
}

int main(int argc, char *argv[]) {
  setvbuf(stdout, nullptr, _IONBF, 0);
  if(argc != 2 || !CallerMayAuthenticate(argv[1])) {
    printf("%s\n", I18n::Tr("Pedido recusado.").c_str());
    return -1;
  }
  if(setuid(0) != 0) {
    printf("setuid(0) failed.\n");
    return -1;
  }
  LoggingSystem::Init("module", false);
  auto result = runMain(argc, argv);
  LoggingSystem::Destroy();
  return result;
}
