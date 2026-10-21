// Italock: destravar SSH pelo celular (EXPERIMENTAL, Linux).
// Porte do pcbu-ssh-askpass do PulseUnlock v4 (MeisApps, GPL versao 3; github.com/MeisApps/pcbu-desktop), adaptado.
//
// O que faz: quando ESTE computador abre uma conexao SSH para outro servidor (ssh usuario@servidor) e o servidor pede
// senha, ou a chave pede a frase secreta, o ssh chama este programa (SSH_ASKPASS). Se houver uma senha guardada para
// aquele destino, o celular pede a digital; aprovado, a senha vai para o ssh. Sem senha guardada, com o celular fora
// ou recusando, pergunta no terminal como o ssh faria (nada deixa de funcionar).
// Nao mexe no servidor SSH deste computador (sshd, PAM): so no ssh que sai daqui.
//
// Uso (terminal):
//   italock-ssh adicionar usuario@servidor      guarda a senha desse servidor (pede a digital no celular)
//   italock-ssh adicionar chave ~/.ssh/id_ed25519  guarda a frase secreta dessa chave
//   italock-ssh listar | remover <numero>
//   italock-ssh ligar | desligar                   (o app usa: SSH_ASKPASS no ambiente do usuario, ver RunToggle)
//
// Seguranca: executavel setuid root (le os pareamentos, que so o root le). Roda com os privilegios do usuario e so
// volta a root para ler os arquivos e falar com o celular. Cada usuario so ve e usa as proprias senhas. As senhas ficam
// cifradas com a senha do usuario neste computador (que o celular entrega so depois da digital) em
// /etc/italock/ssh_servers.json (root, 600).
#include <fcntl.h>
#include <pwd.h>
#include <sys/stat.h>
#include <termios.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <optional>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "handler/UnlockHandler.h"
#include "platform/PlatformHelper.h"
#include "storage/AppSettings.h"
#include "storage/LoggingSystem.h"
#include "utils/CryptUtils.h"
#include "utils/StringUtils.h"

namespace {
constexpr const char *SERVERS_FILE = "ssh_servers.json";

// Volta a root enquanto existir; ao sair, devolve os privilegios do usuario (ou encerra, se nao der).
class RootGuard {
public:
  RootGuard() : m_Ok(seteuid(0) == 0) {}
  ~RootGuard() {
    if(m_Ok && !Drop())
      _exit(1);
  }
  static bool Drop() {
    return seteuid(getuid()) == 0 && geteuid() == getuid();
  }
  RootGuard(const RootGuard &) = delete;
  RootGuard &operator=(const RootGuard &) = delete;

private:
  bool m_Ok;
};

struct Server {
  std::string owner{};   // usuario deste computador
  std::string host{};    // servidor (senha)
  std::string sshUser{}; // usuario no servidor (vazio = qualquer)
  std::string keyPath{}; // chave (frase secreta)
  std::string passwordEnc{};
};

std::string Clean(std::string s, size_t max) {
  std::erase_if(s, [](unsigned char c) { return c < 0x20 || c == 0x7f; });
  s = StringUtils::Trim(s);
  return s.size() > max ? s.substr(0, max) : s;
}

std::string ExpandPath(const std::string &path, const std::string &home) {
  std::string p = path;
  if(p == "~")
    p = home;
  else if(p.starts_with("~/"))
    p = home + p.substr(1);
  std::error_code ec{};
  auto abs = std::filesystem::absolute(p, ec);
  if(ec)
    return p;
  auto canon = std::filesystem::weakly_canonical(abs, ec);
  return ec ? abs.lexically_normal().string() : canon.string();
}

// ---- Arquivo das senhas (so como root) ----
std::vector<Server> LoadServers() {
  std::vector<Server> result{};
  auto path = AppSettings::GetBaseDir() / SERVERS_FILE;
  int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
  if(fd < 0)
    return result;
  std::string data{};
  char buf[4096];
  ssize_t r;
  while((r = read(fd, buf, sizeof(buf))) > 0 && data.size() < 1024 * 1024)
    data.append(buf, static_cast<size_t>(r));
  close(fd);
  try {
    auto json = nlohmann::json::parse(data);
    for(const auto &e : json) {
      Server s{};
      s.owner = e.value("owner", std::string{});
      s.host = e.value("host", std::string{});
      s.sshUser = e.value("sshUser", std::string{});
      s.keyPath = e.value("keyPath", std::string{});
      s.passwordEnc = e.value("passwordEnc", std::string{});
      if(!s.owner.empty() && !s.passwordEnc.empty() && (!s.host.empty() || !s.keyPath.empty()))
        result.emplace_back(s);
    }
  } catch(...) {
    fprintf(stderr, "Arquivo de senhas SSH ilegível: %s\n", path.c_str());
  }
  return result;
}

bool SaveServers(const std::vector<Server> &servers) {
  auto json = nlohmann::json::array();
  for(const auto &s : servers)
    json.push_back({{"owner", s.owner}, {"host", s.host}, {"sshUser", s.sshUser}, {"keyPath", s.keyPath}, {"passwordEnc", s.passwordEnc}});
  auto data = json.dump();
  auto path = AppSettings::GetBaseDir() / SERVERS_FILE;
  auto tmp = path;
  tmp += ".tmp";
  unlink(tmp.c_str());
  int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
  if(fd < 0)
    return false;
  bool ok = fchown(fd, 0, 0) == 0 && fchmod(fd, 0600) == 0;
  size_t done = 0;
  while(ok && done < data.size()) {
    auto w = write(fd, data.data() + done, data.size() - done);
    if(w <= 0)
      ok = false;
    else
      done += static_cast<size_t>(w);
  }
  ok = ok && fsync(fd) == 0;
  close(fd);
  if(!ok || rename(tmp.c_str(), path.c_str()) != 0) {
    unlink(tmp.c_str());
    return false;
  }
  return true;
}

// ---- Terminal (sem eco) ----
std::optional<std::string> ReadSecretFromTty(const std::string &prompt) {
  int fd = open("/dev/tty", O_RDWR | O_CLOEXEC);
  if(fd < 0)
    return {};
  termios saved{};
  bool restore = tcgetattr(fd, &saved) == 0;
  if(restore) {
    auto raw = saved;
    raw.c_lflag &= ~ECHO;
    tcsetattr(fd, TCSAFLUSH, &raw);
  }
  auto shown = prompt.empty() ? std::string("Senha: ") : prompt;
  if(shown.back() != ' ')
    shown += ' ';
  bool ok = write(fd, shown.data(), shown.size()) >= 0;
  std::string line{};
  std::array<char, 256> buf{};
  bool done = false;
  while(ok && !done) {
    auto n = read(fd, buf.data(), buf.size());
    if(n <= 0) {
      ok = false;
      break;
    }
    for(ssize_t i = 0; i < n; i++) {
      if(buf[i] == '\n' || buf[i] == '\r') {
        done = true;
        break;
      }
      line.push_back(buf[i]);
    }
  }
  if(write(fd, "\n", 1) < 0) {
  }
  if(restore)
    tcsetattr(fd, TCSAFLUSH, &saved);
  close(fd);
  explicit_bzero(buf.data(), buf.size());
  if(!ok)
    return {};
  return line;
}

// Fallback do askpass: pergunta no terminal como o ssh faria.
int AskOnTty(const std::string &prompt) {
  auto secret = ReadSecretFromTty(prompt);
  if(!secret)
    return 1;
  fprintf(stdout, "%s\n", secret->c_str());
  explicit_bzero(secret->data(), secret->size());
  return 0;
}

// ---- Pergunta do ssh ----
struct Prompt {
  enum class Kind { Unknown, Password, Key } kind = Kind::Unknown;
  std::string user{}, host{}, keyPath{};

  static Prompt Parse(std::string raw) {
    raw = StringUtils::Trim(raw);
    Prompt p{};
    const std::string passwordSuffix = "'s password:";
    auto lower = StringUtils::ToLower(raw);
    if(lower.size() > passwordSuffix.size() && lower.ends_with(passwordSuffix)) {
      auto ident = raw.substr(0, raw.size() - passwordSuffix.size());
      p.kind = Kind::Password;
      if(auto at = ident.rfind('@'); at != std::string::npos) {
        p.user = ident.substr(0, at);
        p.host = ident.substr(at + 1);
      } else {
        p.host = ident;
      }
      return p;
    }
    if(!lower.starts_with("enter passphrase for"))
      return {};
    p.kind = Kind::Key;
    if(auto a = raw.find('\''); a != std::string::npos)
      if(auto b = raw.find('\'', a + 1); b != std::string::npos)
        p.keyPath = raw.substr(a + 1, b - a - 1);
    if(p.keyPath.empty()) {
      auto s = raw;
      if(s.ends_with(':'))
        s.pop_back();
      auto sp = s.rfind(' ');
      auto token = sp == std::string::npos ? s : s.substr(sp + 1);
      if(token.starts_with('/') || token.starts_with('~') || token.starts_with('.'))
        p.keyPath = token;
    }
    if(p.keyPath.empty())
      return {};
    return p;
  }

  // Texto que aparece no celular: "SSH para usuario@servidor" / "Chave SSH id_ed25519"
  std::string DisplayName() const {
    if(kind == Kind::Password)
      return "SSH para " + Clean(user.empty() ? host : user + "@" + host, 96);
    auto name = std::filesystem::path(keyPath).filename().string();
    return "Chave SSH " + Clean(name.empty() ? keyPath : name, 96);
  }

  bool Matches(const Server &s, const std::string &home) const {
    if(kind == Kind::Password)
      return !s.host.empty() && StringUtils::ToLower(s.host) == StringUtils::ToLower(host) && (s.sshUser.empty() || s.sshUser == user);
    if(kind == Kind::Key)
      return !s.keyPath.empty() && ExpandPath(s.keyPath, home) == ExpandPath(keyPath, home);
    return false;
  }
};

// Pede a digital no celular e devolve a senha do usuario neste computador (chave das senhas guardadas).
std::optional<std::string> ApproveOnPhone(const std::string &userName, const std::string &program) {
  auto handler = UnlockHandler([](const std::string &s) { fprintf(stderr, "%s\n", s.c_str()); });
  RootGuard root{};
  auto result = handler.GetResult(userName, program);
  if(result.state != UnlockState::SUCCESS)
    return {};
  if(result.device.userName != userName || PlatformHelper::CheckLogin(userName, result.password).result != PlatformLoginResult::SUCCESS) {
    fprintf(stderr, "%s\n", I18n::Get("error_password").c_str());
    return {};
  }
  return result.password;
}

int RunAskpass(const std::string &raw, const std::string &userName, const std::string &home) {
  auto prompt = Prompt::Parse(raw);
  if(prompt.kind == Prompt::Kind::Unknown)
    return AskOnTty(raw); // pergunta que nao e de senha (ex.: confirmar a chave do servidor): o usuario responde
  std::vector<Server> mine{};
  {
    RootGuard root{};
    for(auto &s : LoadServers())
      if(s.owner == userName && prompt.Matches(s, home))
        mine.emplace_back(s);
  }
  if(mine.empty())
    return AskOnTty(raw);
  // Senha com usuario especifico tem preferencia sobre a generica do mesmo servidor
  std::stable_sort(mine.begin(), mine.end(), [](const Server &a, const Server &b) { return !a.sshUser.empty() && b.sshUser.empty(); });
  auto key = ApproveOnPhone(userName, prompt.DisplayName());
  if(!key)
    return AskOnTty(raw);
  auto secret = CryptUtils::DecryptAES(mine.front().passwordEnc, *key);
  explicit_bzero(key->data(), key->size());
  if(!secret) {
    fprintf(stderr, "Não consegui abrir a senha guardada (a senha deste computador mudou?). Guarde de novo com: italock-ssh adicionar\n");
    return AskOnTty(raw);
  }
  fprintf(stdout, "%s\n", secret->c_str());
  explicit_bzero(secret->data(), secret->size());
  return 0;
}

int RunAdd(const std::vector<std::string> &args, const std::string &userName, const std::string &home) {
  Server s{};
  s.owner = userName;
  if(args.size() == 2 && args[0] == "chave") {
    s.keyPath = ExpandPath(Clean(args[1], 512), home);
    if(!std::filesystem::exists(s.keyPath)) {
      fprintf(stderr, "Chave não encontrada: %s\n", s.keyPath.c_str());
      return 1;
    }
  } else if(args.size() == 1) {
    auto target = Clean(args[0], 256);
    if(auto at = target.rfind('@'); at != std::string::npos) {
      s.sshUser = target.substr(0, at);
      s.host = target.substr(at + 1);
    } else {
      s.host = target;
    }
    if(s.host.empty() || s.host.find_first_of(" /'\"") != std::string::npos) {
      fprintf(stderr, "Servidor inválido.\n");
      return 1;
    }
  } else {
    fprintf(stderr, "Uso: italock-ssh adicionar usuario@servidor | italock-ssh adicionar chave <caminho da chave>\n");
    return 1;
  }
  auto first = ReadSecretFromTty(s.keyPath.empty() ? "Senha de " + (s.sshUser.empty() ? s.host : s.sshUser + "@" + s.host) + ":"
                                                   : "Frase secreta da chave:");
  if(!first || first->empty())
    return 1;
  auto again = ReadSecretFromTty("Repita:");
  if(!again || *again != *first) {
    fprintf(stderr, "As duas não conferem. Nada foi guardado.\n");
    return 1;
  }
  explicit_bzero(again->data(), again->size());
  fprintf(stderr, "Confirme no celular...\n");
  auto key = ApproveOnPhone(userName, "Guardar senha SSH");
  if(!key) {
    fprintf(stderr, "Celular não confirmou. Nada foi guardado.\n");
    return 1;
  }
  auto enc = CryptUtils::EncryptAES(*first, *key);
  explicit_bzero(first->data(), first->size());
  explicit_bzero(key->data(), key->size());
  if(!enc) {
    fprintf(stderr, "Não consegui cifrar a senha. Nada foi guardado.\n");
    return 1;
  }
  s.passwordEnc = *enc;
  RootGuard root{};
  auto all = LoadServers();
  std::erase_if(all, [&](const Server &o) { return o.owner == s.owner && o.host == s.host && o.sshUser == s.sshUser && o.keyPath == s.keyPath; });
  all.emplace_back(s);
  if(!SaveServers(all)) {
    fprintf(stderr, "Não consegui gravar. Nada foi guardado.\n");
    return 1;
  }
  fprintf(stderr, "Guardado. Da próxima vez, o ssh pede a digital no celular.\n");
  return 0;
}

int RunList(const std::string &userName) {
  std::vector<Server> all{};
  {
    RootGuard root{};
    all = LoadServers();
  }
  int n = 0;
  for(const auto &s : all) {
    if(s.owner != userName)
      continue;
    n++;
    if(s.keyPath.empty())
      printf("%d  %s\n", n, (s.sshUser.empty() ? s.host : s.sshUser + "@" + s.host).c_str());
    else
      printf("%d  chave %s\n", n, s.keyPath.c_str());
  }
  if(n == 0)
    printf("Nenhuma senha SSH guardada.\n");
  return 0;
}

// ---- Ligar/desligar no ambiente do usuario (roda SEM privilegio: so mexe em arquivos do proprio usuario) ----
// Dois lugares, porque cada ambiente grafico le um:
//   ~/.config/environment.d/italock-ssh.conf  (systemd do usuario: GNOME, KDE e sessoes Wayland)
//   bloco marcado no ~/.xsessionrc            (sessoes X do Debian/Ubuntu/Mint: Cinnamon, MATE, XFCE)
// O bloco do .xsessionrc so exporta se o italock-ssh existir: desinstalado, o ssh volta ao normal sozinho.
constexpr const char *ENV_REL = ".config/environment.d/italock-ssh.conf";
constexpr const char *XRC_BEGIN = "# Ítalock SSH (início, não edite)";
constexpr const char *XRC_END = "# Ítalock SSH (fim)";

bool WriteUserFile(const std::filesystem::path &path, const std::string &data) {
  auto tmp = path;
  tmp += ".italock-tmp";
  FILE *f = fopen(tmp.c_str(), "w");
  if(!f)
    return false;
  bool ok = fwrite(data.data(), 1, data.size(), f) == data.size();
  ok = fclose(f) == 0 && ok;
  if(ok) {
    struct stat st{};
    chmod(tmp.c_str(), stat(path.c_str(), &st) == 0 ? (st.st_mode & 0777) : 0644);
    ok = rename(tmp.c_str(), path.c_str()) == 0;
  }
  if(!ok)
    unlink(tmp.c_str());
  return ok;
}

std::string ReadUserFile(const std::filesystem::path &path) {
  FILE *f = fopen(path.c_str(), "r");
  if(!f)
    return {};
  std::string data{};
  char buf[4096];
  size_t n;
  while((n = fread(buf, 1, sizeof(buf), f)) > 0)
    data.append(buf, n);
  fclose(f);
  return data;
}

std::string RemoveXrcBlock(const std::string &text) {
  std::string out{};
  bool inside = false;
  size_t pos = 0;
  while(pos < text.size()) {
    auto end = text.find('\n', pos);
    auto line = text.substr(pos, end == std::string::npos ? std::string::npos : end - pos);
    pos = end == std::string::npos ? text.size() : end + 1;
    if(line == XRC_BEGIN)
      inside = true;
    else if(line == XRC_END)
      inside = false;
    else if(!inside)
      out += line + "\n";
  }
  return out;
}

int RunToggle(bool enable, const std::string &home) {
  if(home.empty())
    return 1;
  std::error_code ec{};
  auto envPath = std::filesystem::path(home) / ENV_REL;
  auto xrcPath = std::filesystem::path(home) / ".xsessionrc";
  auto xrc = RemoveXrcBlock(ReadUserFile(xrcPath));
  bool ok = true;
  if(enable) {
    std::filesystem::create_directories(envPath.parent_path(), ec);
    ok = WriteUserFile(envPath, "# Ítalock: o ssh pede ao celular as senhas guardadas (italock-ssh adicionar usuario@servidor). Apague para desligar.\n"
                                "SSH_ASKPASS=/usr/local/sbin/italock-ssh\nSSH_ASKPASS_REQUIRE=force\n");
    xrc += std::string(XRC_BEGIN) + "\n" +
           // if/fi (nunca "[ ] && ..."): o Xsession le este arquivo com "set -e", e um bloco que termina com status 1
           // derrubaria a entrada no computador
           "if [ -x /usr/local/sbin/italock-ssh ]; then export SSH_ASKPASS=/usr/local/sbin/italock-ssh SSH_ASKPASS_REQUIRE=force; fi\n" +
           XRC_END + "\n";
    ok = WriteUserFile(xrcPath, xrc) && ok;
  } else {
    std::filesystem::remove(envPath, ec);
    if(std::filesystem::exists(xrcPath, ec))
      ok = WriteUserFile(xrcPath, xrc);
  }
  if(ok)
    printf(enable ? "SSH pelo celular ligado. Vale a partir da próxima vez que entrar no computador.\n" : "SSH pelo celular desligado.\n");
  return ok ? 0 : 1;
}

int RunRemove(const std::string &which, const std::string &userName) {
  int index = 0;
  try {
    index = std::stoi(which);
  } catch(...) {
    fprintf(stderr, "Use o número mostrado em: italock-ssh listar\n");
    return 1;
  }
  RootGuard root{};
  auto all = LoadServers();
  int n = 0;
  for(auto it = all.begin(); it != all.end(); ++it) {
    if(it->owner != userName)
      continue;
    if(++n == index) {
      all.erase(it);
      if(!SaveServers(all)) {
        fprintf(stderr, "Não consegui gravar.\n");
        return 1;
      }
      printf("Removido.\n");
      return 0;
    }
  }
  fprintf(stderr, "Não existe o número %s.\n", which.c_str());
  return 1;
}
} // namespace

int main(int argc, char *argv[]) {
  setvbuf(stdout, nullptr, _IONBF, 0);
  setvbuf(stderr, nullptr, _IONBF, 0);
  auto *pw = getpwuid(getuid());
  if(!pw || !pw->pw_name) {
    fprintf(stderr, "Usuário desconhecido.\n");
    return 1;
  }
  const std::string userName = pw->pw_name;
  const std::string home = pw->pw_dir ? pw->pw_dir : "";
  // ligar/desligar: so arquivos do proprio usuario, sem nunca usar root
  if(argc == 2 && (std::string(argv[1]) == "ligar" || std::string(argv[1]) == "desligar")) {
    if(setuid(getuid()) != 0 || geteuid() != getuid())
      return 1; // larga o root de vez
    return RunToggle(std::string(argv[1]) == "ligar", home);
  }
  if(geteuid() != 0 || !RootGuard::Drop()) {
    // Sem setuid (instalacao incompleta): ainda assim nao atrapalha o ssh
    if(argc >= 2 && std::string(argv[1]) != "adicionar" && std::string(argv[1]) != "listar" && std::string(argv[1]) != "remover")
      return AskOnTty(argv[1]);
    fprintf(stderr, "O italock-ssh não está instalado direito (falta permissão). Reinstale o Ítalock.\n");
    return 1;
  }
  {
    RootGuard root{};
    LoggingSystem::Init("module", false);
  }
  int rc = 1;
  const std::string cmd = argc >= 2 ? argv[1] : "";
  std::vector<std::string> rest{};
  for(int i = 2; i < argc; i++)
    rest.emplace_back(argv[i]);
  if(cmd == "adicionar")
    rc = RunAdd(rest, userName, home);
  else if(cmd == "listar")
    rc = RunList(userName);
  else if(cmd == "remover" && rest.size() == 1)
    rc = RunRemove(rest[0], userName);
  else
    rc = RunAskpass(cmd, userName, home); // chamado pelo ssh com a pergunta
  {
    RootGuard root{};
    LoggingSystem::Destroy();
  }
  return rc;
}
