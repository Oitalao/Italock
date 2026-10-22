#include <QGuiApplication>
#include <QIcon>
#include <QQmlAbstractUrlInterceptor>
#include <QQmlApplicationEngine>
#include <QQuickWindow>
#include <QSettings>
#include <QStyleHints>
#include <QTimer>

#ifndef _WIN32
#include <unistd.h>
#endif
#include "storage/LoggingSystem.h"
#include "ui/MainWindow.h"
#include "ui/helpers/I18nWrapper.h"
#include "utils/I18n.h"

// Texto no idioma escolhido (o original em portugues do Brasil e a chave).
[[maybe_unused]] static QString Tr(const char *text) {
  return QString::fromStdString(I18n::Tr(text));
}

#ifdef _WIN32
// Italock (Windows): o app mora na bandeja (perto do relogio, como o Tailscale), sem ocupar a barra de tarefas.
// A bandeja roda SEM administrador (tarefa "Italock Bandeja" no logon de qualquer usuario): trava o PC, avisa quando
// o celular travou ou destravou e abre a janela. A janela mexe em coisas do sistema (pareamentos protegidos, modulo da
// tela de bloqueio, tarefas agendadas), entao roda num processo a parte com administrador: a bandeja abre
// "Italock.exe --janela" pedindo elevacao (UAC) so nessa hora. Se o proprio processo ja for administrador (aberto
// pelo instalador, por exemplo), a janela abre ali mesmo e, ao fechar, a interface (QML) e descarregada.
// Nomes da instancia unica incluem o SID do usuario e a sessao: dois usuarios logados (troca rapida de usuario ou
// area de trabalho remota) tem cada um a sua bandeja.
#include <QAction>
#include <QApplication>
#include <QDir>
#include <QFile>
#include <QAbstractNativeEventFilter>
#include <QFileSystemWatcher>
#include <QImage>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QPixmap>
#include <QLocalServer>
#include <QLocalSocket>
#include <QMenu>
#include <QPointer>
#include <QSystemTrayIcon>
#include <QTimer>
#include <QDateTime>
#include <Windows.h>
#include <mmsystem.h>
#include <sddl.h>
#include <shellapi.h>
#include <thread>

#include "installer/ServiceInstaller.h"
#include "storage/AppSettings.h"

#pragma comment(lib, "winmm.lib")

static QString CurrentUserSid() {
  QString result = "desconhecido";
  HANDLE token = nullptr;
  if(!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token))
    return result;
  DWORD size = 0;
  GetTokenInformation(token, TokenUser, nullptr, 0, &size);
  std::vector<uint8_t> buf(size ? size : 1);
  if(size && GetTokenInformation(token, TokenUser, buf.data(), size, &size)) {
    LPWSTR sidStr = nullptr;
    if(ConvertSidToStringSidW(reinterpret_cast<TOKEN_USER *>(buf.data())->User.Sid, &sidStr)) {
      result = QString::fromWCharArray(sidStr);
      LocalFree(sidStr);
    }
  }
  CloseHandle(token);
  return result;
}

static QString InstanceSuffix() {
  DWORD session = 0;
  ProcessIdToSessionId(GetCurrentProcessId(), &session);
  return CurrentUserSid() + "-" + QString::number(session);
}

static bool IsElevated() {
  HANDLE token = nullptr;
  if(!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token))
    return false;
  TOKEN_ELEVATION elevation{};
  DWORD size = 0;
  bool elevated = GetTokenInformation(token, TokenElevation, &elevation, sizeof(elevation), &size) && elevation.TokenIsElevated;
  CloseHandle(token);
  return elevated;
}

// Evento "Local\" (por sessao) que a janela elevada espera para vir para a frente. Rotulo de integridade baixo (NW;LW)
// para a bandeja sem administrador conseguir sinalizar; so o proprio usuario tem acesso.
static std::wstring WindowEventName() {
  return (QString("Local\\ItalockJanela-") + CurrentUserSid()).toStdWString();
}

static bool RaiseElevatedWindow() {
  HANDLE ev = OpenEventW(EVENT_MODIFY_STATE, FALSE, WindowEventName().c_str());
  if(!ev)
    return false;
  bool ok = SetEvent(ev) != FALSE;
  CloseHandle(ev);
  return ok;
}

static bool LaunchElevatedWindow() {
  wchar_t exePath[MAX_PATH]{};
  GetModuleFileNameW(nullptr, exePath, MAX_PATH);
  SHELLEXECUTEINFOW sei{sizeof(sei)};
  sei.fMask = SEE_MASK_NOASYNC;
  sei.lpVerb = L"runas";
  sei.lpFile = exePath;
  sei.lpParameters = L"--janela";
  sei.nShow = SW_SHOWNORMAL;
  return ShellExecuteExW(&sei) != FALSE; // falso tambem quando a pessoa recusa o UAC
}
#endif

// Italock: estilo Basic, redesenhado nas telas (qml/base): nada do Material/Universal padrao.
static void SetQuickEnv() {
  qputenv("QT_QUICK_CONTROLS_STYLE", QByteArray("Basic"));
}

// Desenvolvimento: ITALOCK_QML_DIR=<pasta desktop/qml> carrega as telas do disco em vez do executavel.
class DevQmlInterceptor : public QQmlAbstractUrlInterceptor {
public:
  explicit DevQmlInterceptor(QString dir) : m_Dir(std::move(dir)) {}
  QUrl intercept(const QUrl &url, DataType) override {
    if(url.scheme() == "qrc" && url.path().startsWith("/ui/"))
      return QUrl::fromLocalFile(m_Dir + url.path().mid(3));
    return url;
  }

private:
  QString m_Dir;
};

// Variavel de desenvolvimento (ITALOCK_QML_DIR carrega telas de qualquer pasta = codigo) so vale sem
// administrador/root: no processo elevado e ignorada.
static bool DevEnvAllowed() {
#ifdef _WIN32
  return !IsElevated();
#else
  return geteuid() != 0;
#endif
}

static void MaybeUseDevQml(QQmlApplicationEngine *engine) {
  if(!DevEnvAllowed())
    return;
  auto dir = qEnvironmentVariable("ITALOCK_QML_DIR");
  if(!dir.isEmpty())
    engine->addUrlInterceptor(new DevQmlInterceptor(dir));
}

#ifdef _WIN32
// Bandeja (sem administrador). Tudo por evento, nada de laco apertado:
//  - uso.json mudou (agente/tela de bloqueio gravaram): aviso "Destravado pelo <celular>, pelo <caminho>" e dica do
//    icone com o ultimo destravar;
//  - uma vez por minuto: icone ligado/desligado (modulo da tela de bloqueio instalado?), a hora da dica ("ontem") e o
//    atalho de teclado (tambem relido na hora quando a janela muda a preferencia);
//  - atalho de teclado opcional (RegisterHotKey nesta thread; WM_HOTKEY chega pelo filtro de eventos nativos): trava
//    na hora, e a tela de bloqueio do Italock fica esperando o celular. Win+L e do Windows e nao pode ser usado.
class TrayController : public QObject, public QAbstractNativeEventFilter {
public:
  TrayController(QSystemTrayIcon *tray, QObject *parent) : QObject(parent), m_Tray(tray) {
    for(int size : {16, 20, 24, 32, 48, 64}) {
      auto file = QString(":/res/icons/bandeja-%1.png").arg(size);
      m_OnIcon.addFile(file, QSize(size, size));
      m_OffIcon.addPixmap(Faded(QPixmap(file)));
    }
    m_Dir = QString::fromStdWString(AppSettings::GetBaseDir().wstring());
    auto use = ReadUse();
    m_LastTs = use.value("_ultimo").toObject().value("ts").toInteger();
    ReadLastUnlock(use);
    m_Watcher.addPath(m_Dir);
    connect(&m_Watcher, &QFileSystemWatcher::directoryChanged, this, [this]() { m_Debounce.start(); });
    m_Debounce.setSingleShot(true);
    m_Debounce.setInterval(400);
    connect(&m_Debounce, &QTimer::timeout, this, [this]() { OnUseChanged(); });
    m_Minute.setInterval(60000);
    connect(&m_Minute, &QTimer::timeout, this, [this]() {
      if(!m_Watcher.directories().contains(m_Dir))
        m_Watcher.addPath(m_Dir); // pasta criada depois (primeira instalacao)
      ReloadPrefs();
      ReloadLanguage();
      UpdateLook();
    });
    m_Minute.start();
    QCoreApplication::instance()->installNativeEventFilter(this);
    ReloadPrefs();
    UpdateLook();
  }
  ~TrayController() override {
    QCoreApplication::instance()->removeNativeEventFilter(this);
    if(m_HotkeyOn)
      UnregisterHotKey(nullptr, HOTKEY_ID);
  }

  // Preferencia do atalho mudou na janela (ou passou um minuto): registra de novo se precisar.
  void ReloadPrefs() {
    QSettings prefs("Italock", "Italock");
    bool want = prefs.value("atalho", false).toBool();
    auto combo = prefs.value("atalhoTecla", "ctrl+alt+l").toString();
    if(want == m_HotkeyWanted && combo == m_HotkeyCombo && (m_HotkeyOn || !want || m_HotkeyFailed))
      return; // nada mudou (e se outro programa usa a combinacao, nao insiste a cada minuto)
    if(m_HotkeyOn) {
      UnregisterHotKey(nullptr, HOTKEY_ID);
      m_HotkeyOn = false;
    }
    m_HotkeyWanted = want;
    m_HotkeyCombo = combo;
    m_HotkeyFailed = false;
    if(want) {
      UINT mods = 0, vk = 0;
      if(ParseCombo(combo, mods, vk) && RegisterHotKey(nullptr, HOTKEY_ID, mods | MOD_NOREPEAT, vk)) {
        m_HotkeyOn = true;
      } else {
        m_HotkeyFailed = true;
        spdlog::warn("Atalho {} nao registrado ({}).", combo.toStdString(), GetLastError());
        m_Tray->showMessage("Ítalock", Tr("Outro programa já usa essa combinação de teclas. Escolha outra nas Configurações."),
                            QSystemTrayIcon::Warning, 5000);
      }
    }
    if(prefs.value("atalhoErro", false).toBool() != m_HotkeyFailed)
      prefs.setValue("atalhoErro", m_HotkeyFailed);
  }

  // Idioma: a janela mudou (aviso pelo canal local) ou outra pessoa mudou (conferido a cada minuto). Traduz o menu e
  // a dica do icone.
  void SetRetranslate(std::function<void()> fn) {
    m_Retranslate = std::move(fn);
    m_Language = QString::fromStdString(I18n::CurrentLanguage());
    if(m_Retranslate)
      m_Retranslate();
  }
  void ReloadLanguage() {
    AppSettings::InvalidateCache();
    auto lang = QString::fromStdString(I18n::CurrentLanguage());
    if(lang == m_Language)
      return;
    m_Language = lang;
    QI18n::NotifyLanguageChanged(); // janela aberta neste mesmo processo (administrador)
    if(m_Retranslate)
      m_Retranslate();
    m_LookSet = false;
    UpdateLook();
  }

  bool nativeEventFilter(const QByteArray &, void *message, qintptr *) override {
    auto msg = static_cast<MSG *>(message);
    if(msg && msg->message == WM_HOTKEY && msg->wParam == HOTKEY_ID) {
      spdlog::info("Atalho de teclado: travando.");
      LockWorkStation();
      return true;
    }
    return false;
  }

private:
  static constexpr int HOTKEY_ID = 0x4C4B; // "LK"

  // Caminho em palavras para o aviso ("pela mesma rede"...); vazio quando nao se sabe.
  static QString PathPhrase(const QString &path) {
    if(path == "rede local")
      return Tr("pela mesma rede");
    if(path == "Tailscale")
      return Tr("pelo Tailscale");
    if(path == "Bluetooth")
      return Tr("pelo Bluetooth");
    return {};
  }

  // Versao "desligada" do cadeado: cinza medio (aparece em barra clara e escura), um pouco transparente.
  static QPixmap Faded(const QPixmap &src) {
    auto img = src.toImage().convertToFormat(QImage::Format_ARGB32);
    for(int y = 0; y < img.height(); y++) {
      auto line = reinterpret_cast<QRgb *>(img.scanLine(y));
      for(int x = 0; x < img.width(); x++) {
        int g = 110 + qGray(line[x]) / 3;
        line[x] = qRgba(g, g, g, qAlpha(line[x]) * 3 / 4);
      }
    }
    return QPixmap::fromImage(img);
  }

  // "ctrl+alt+l", "ctrl+alt+shift+l", "ctrl+alt+espaco"... Exige pelo menos dois modificadores: uma tecla comum sozinha
  // (ou so com Shift) atrapalharia digitar.
  static bool ParseCombo(const QString &combo, UINT &mods, UINT &vk) {
    mods = 0;
    vk = 0;
    int count = 0;
    for(const auto &part : combo.toLower().split('+', Qt::SkipEmptyParts)) {
      auto p = part.trimmed();
      if(p == "ctrl") {
        mods |= MOD_CONTROL;
        count++;
      } else if(p == "alt") {
        mods |= MOD_ALT;
        count++;
      } else if(p == "shift") {
        mods |= MOD_SHIFT;
        count++;
      } else if(p.size() == 1 && ((p[0] >= 'a' && p[0] <= 'z') || (p[0] >= '0' && p[0] <= '9'))) {
        vk = p[0].toUpper().unicode();
      } else if(p == "espaco") {
        vk = VK_SPACE;
      } else {
        return false;
      }
    }
    return vk != 0 && count >= 2;
  }

  QJsonObject ReadUse() {
    QFile f(m_Dir + "/uso.json");
    if(!f.open(QIODevice::ReadOnly))
      return {};
    return QJsonDocument::fromJson(f.readAll()).object();
  }

  // Ultimo destravar que deu certo (historico; sem historico, o "_ultimo" de versoes anteriores).
  void ReadLastUnlock(const QJsonObject &use) {
    auto history = use.value("historico").toArray();
    for(auto i = history.size() - 1; i >= 0; i--) {
      auto e = history.at(i).toObject();
      if(e.value("acao").toString() == "destravar" && e.value("resultado").toString() == "ok") {
        m_UnlockTs = e.value("ts").toInteger();
        m_UnlockName = e.value("nome").toString();
        m_UnlockPath = e.value("caminho").toString();
        return;
      }
    }
    auto last = use.value("_ultimo").toObject();
    if(last.value("acao").toString() == "destravar") {
      m_UnlockTs = last.value("ts").toInteger();
      m_UnlockName = last.value("nome").toString();
      m_UnlockPath = last.value("caminho").toString();
    } else if(history.isEmpty() && !use.contains("_ultimo")) {
      m_UnlockTs = 0; // historico limpo
    }
  }

  void OnUseChanged() {
    auto use = ReadUse();
    ReadLastUnlock(use);
    UpdateLook();
    auto last = use.value("_ultimo").toObject();
    auto ts = last.value("ts").toInteger();
    if(ts <= m_LastTs)
      return;
    m_LastTs = ts;
    if(QDateTime::currentSecsSinceEpoch() - ts > 120)
      return; // aviso velho (PC estava desligado ou a bandeja fechada)
    auto action = last.value("acao").toString();
    if(action != "travar" && action != "destravar")
      return;
    auto name = last.value("nome").toString().left(40);
    if(name.isEmpty())
      name = Tr("celular");
    auto phrase = PathPhrase(last.value("caminho").toString());
    QSettings prefs("Italock", "Italock");
    if(prefs.value("avisos", true).toBool()) {
      auto text = (action == "travar" ? Tr("Travado pelo %1") : Tr("Destravado pelo %1")).arg(name);
      text += phrase.isEmpty() ? QString(".") : QString(", %1.").arg(phrase);
      m_Tray->showMessage("Ítalock", text, QSystemTrayIcon::Information, 4000);
    }
    if(action == "destravar" && prefs.value("som", false).toBool())
      if(!PlaySoundW(L"WindowsUnlock", nullptr, SND_ALIAS | SND_ASYNC | SND_NODEFAULT))
        MessageBeep(MB_OK);
  }

  // Icone (protecao ligada ou desligada) e dica com o ultimo destravar.
  void UpdateLook() {
    bool on = ServiceInstaller::IsProtectionOn();
    if(on != m_ProtectionOn || !m_LookSet) {
      m_Tray->setIcon(on ? m_OnIcon : m_OffIcon);
      m_ProtectionOn = on;
      m_LookSet = true;
    }
    QString tip = on ? Tr("Ítalock: proteção ligada") : Tr("Ítalock: proteção desligada");
    if(m_UnlockTs > 0) {
      auto when = QDateTime::fromSecsSinceEpoch(m_UnlockTs);
      auto today = QDate::currentDate();
      QString hour = when.toString("HH:mm");
      if(when.date() == today.addDays(-1))
        hour = Tr("ontem %1").arg(hour);
      else if(when.date() != today)
        hour = when.toString("dd/MM") + " " + hour;
      QString where = m_UnlockPath == "rede local" ? Tr("mesma rede") : QString::fromStdString(I18n::Tr(m_UnlockPath.toStdString()));
      QString name = m_UnlockName.isEmpty() ? Tr("celular") : m_UnlockName.left(30);
      tip += "\n" + Tr("Último destravar: %1").arg(name + ", " + hour + (where.isEmpty() ? QString() : ", " + where));
    }
    if(tip != m_Tray->toolTip())
      m_Tray->setToolTip(tip);
  }

  QSystemTrayIcon *m_Tray;
  QIcon m_OnIcon{};
  QIcon m_OffIcon{};
  QString m_Dir;
  qint64 m_LastTs = 0;
  qint64 m_UnlockTs = 0;
  QString m_UnlockName{};
  QString m_UnlockPath{};
  bool m_ProtectionOn = false;
  bool m_LookSet = false;
  bool m_HotkeyWanted = false;
  bool m_HotkeyOn = false;
  bool m_HotkeyFailed = false;
  QString m_HotkeyCombo{};
  QString m_Language{};
  std::function<void()> m_Retranslate{};
  QFileSystemWatcher m_Watcher{};
  QTimer m_Debounce{};
  QTimer m_Minute{};
};
#endif

// Aparencia escolhida pela pessoa (Escuro e o padrao de fabrica). ITALOCK_TEMA=claro|escuro forca um dos dois (para
// conferir as telas).
static void ApplyAppearance() {
  auto tema = qgetenv("ITALOCK_TEMA");
  if(tema == "escuro" || tema == "claro") {
    QGuiApplication::styleHints()->setColorScheme(tema == "escuro" ? Qt::ColorScheme::Dark : Qt::ColorScheme::Light);
    return;
  }
  MainWindow::ApplyAppearanceSetting();
}

// Desenvolvimento: ITALOCK_PRINT=<arquivo.png> abre a janela neste processo, tira um print dela depois de 2 s e sai.
// So sem administrador/root (DevEnvAllowed). Com QT_QPA_PLATFORM=offscreen e QT_QUICK_BACKEND=software funciona
// mesmo com a tela travada. Junto com ITALOCK_TEMA e ITALOCK_PAGINA serve para conferir as telas.
static int RunPrintMode(int argc, char *argv[], const QString &file) {
  QGuiApplication app(argc, argv);
  MainWindow::s_PrintMode = true;
  if(!qEnvironmentVariableIsEmpty("ITALOCK_IDIOMA"))
    I18n::SetOverride(qEnvironmentVariable("ITALOCK_IDIOMA").toStdString()); // so neste processo; nada e gravado
  QI18n::InstallTranslator();
  ApplyAppearance();
  QQmlApplicationEngine engine{};
  MaybeUseDevQml(&engine);
  engine.load(QUrl("qrc:/ui/MainWindow.qml"));
  QTimer::singleShot(2000, &app, [&engine, file]() {
    for(auto obj : engine.rootObjects())
      if(auto window = qobject_cast<QQuickWindow *>(obj)) {
        window->grabWindow().save(file);
        break;
      }
    QCoreApplication::quit();
  });
  return QGuiApplication::exec();
}

#ifdef _WIN32
int main(int argc, char *argv[]) {
  SetQuickEnv();
  if(DevEnvAllowed() && !qEnvironmentVariableIsEmpty("ITALOCK_PRINT"))
    return RunPrintMode(argc, argv, qEnvironmentVariable("ITALOCK_PRINT"));
  QApplication app(argc, argv);
  QApplication::setWindowIcon(QIcon(":/res/icons/icon.png"));
  QApplication::setQuitOnLastWindowClosed(false);
  QI18n::InstallTranslator();
  const auto args = QCoreApplication::arguments();
  const bool startHidden = args.contains("--bandeja");
  const bool windowRole = args.contains("--janela");
  const bool elevated = IsElevated();
  const auto trayName = "ItalockDesktop-" + InstanceSuffix();

  // ---- Processo da janela (com administrador) ----
  if(windowRole) {
    if(!elevated)
      return LaunchElevatedWindow() ? 0 : 1;
    if(RaiseElevatedWindow())
      return 0; // ja existe uma janela aberta: ela vem para a frente
    LoggingSystem::Init("desktop");
    ApplyAppearance();
    // Atalho de teclado mudou aqui: avisa a bandeja (outro processo, sem administrador) para registrar de novo.
    MainWindow::SetPrefChangedHandler([trayName](const QString &key) {
      QByteArray message{};
      if(key == "idioma")
        message = "idioma";
      else if(key.startsWith("atalho") && key != "atalhoErro")
        message = "preferencias";
      else
        return;
      QLocalSocket s{};
      s.connectToServer(trayName);
      if(s.waitForConnected(300)) {
        s.write(message);
        s.waitForBytesWritten(300);
      }
    });
    // Evento para a bandeja trazer esta janela para a frente
    PSECURITY_DESCRIPTOR sd = nullptr;
    auto sddl = (L"D:(A;;GA;;;" + CurrentUserSid().toStdWString() + L")(A;;GA;;;SY)S:(ML;;NW;;;LW)");
    HANDLE raiseEvent = nullptr;
    if(ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.c_str(), SDDL_REVISION_1, &sd, nullptr)) {
      SECURITY_ATTRIBUTES sa{sizeof(sa), sd, FALSE};
      raiseEvent = CreateEventW(&sa, FALSE, FALSE, WindowEventName().c_str());
      LocalFree(sd);
    }
    QQmlApplicationEngine engine{};
    MaybeUseDevQml(&engine);
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreated, &app, [](QObject *obj, const QUrl &) {
      auto window = qobject_cast<QQuickWindow *>(obj);
      if(!window)
        return;
      QObject::connect(window, &QQuickWindow::visibleChanged, window, [](bool visible) {
        if(!visible)
          QCoreApplication::quit();
      });
    });
    engine.load(QUrl("qrc:/ui/MainWindow.qml"));
    std::atomic<bool> stop{false};
    std::thread raiser([&]() {
      while(raiseEvent && !stop) {
        if(WaitForSingleObject(raiseEvent, 300) == WAIT_OBJECT_0)
          QMetaObject::invokeMethod(&app, [&engine]() {
            for(auto obj : engine.rootObjects())
              if(auto window = qobject_cast<QQuickWindow *>(obj)) {
                window->show();
                window->raise();
                window->requestActivate();
              }
          });
      }
    });
    auto result = QApplication::exec();
    MainWindow::WaitIdle(); // trabalho em andamento (ligar a protecao) termina antes de destruir a interface
    stop = true;
    raiser.join();
    if(raiseEvent)
      CloseHandle(raiseEvent);
    LoggingSystem::Destroy();
    return result;
  }

  // ---- Bandeja ----
  // Uma instancia so por usuario e sessao: se ja estiver na bandeja, pede para ela abrir a janela e sai.
  {
    if(!startHidden && RaiseElevatedWindow())
      return 0;
    QLocalSocket probe{};
    probe.connectToServer(trayName);
    if(probe.waitForConnected(300)) {
      if(!startHidden) {
        probe.write("abrir");
        probe.waitForBytesWritten(300);
      }
      return 0;
    }
  }
  LoggingSystem::Init("desktop");

  QPointer<QQmlApplicationEngine> engine{};
  auto url = QUrl("qrc:/ui/MainWindow.qml");
  auto showWindow = [&]() {
    if(!elevated) {
      // Sem administrador: a janela abre em outro processo, elevado (ou a que ja esta aberta vem para a frente).
      if(!RaiseElevatedWindow() && !LaunchElevatedWindow())
        spdlog::info("Janela nao aberta (elevacao recusada ou indisponivel).");
      return;
    }
    if(engine) {
      for(auto obj : engine->rootObjects())
        if(auto window = qobject_cast<QQuickWindow *>(obj)) {
          window->show();
          window->raise();
          window->requestActivate();
        }
      return;
    }
    ApplyAppearance();
    engine = new QQmlApplicationEngine();
    MaybeUseDevQml(engine);
    QObject::connect(engine, &QQmlApplicationEngine::objectCreated, engine, [&engine](QObject *obj, const QUrl &) {
      auto window = qobject_cast<QQuickWindow *>(obj);
      if(!window)
        return;
      // Fechou a janela (e ela permitiu fechar): descarrega a interface, fica so o icone.
      QObject::connect(window, &QQuickWindow::visibleChanged, window, [&engine](bool visible) {
        if(!visible && engine) {
          engine->deleteLater();
          QGuiApplication::styleHints()->setColorScheme(Qt::ColorScheme::Unknown); // o menu da bandeja volta a seguir o sistema
          // Devolve ao Windows a memoria que a interface usou (fica so o icone, ~20 MB).
          QTimer::singleShot(3000, []() { SetProcessWorkingSetSize(GetCurrentProcess(), static_cast<SIZE_T>(-1), static_cast<SIZE_T>(-1)); });
        }
      });
    });
    engine->load(url);
  };

  // Italock: icone proprio da bandeja (so o cadeado, legivel em 16 px); desbotado com a protecao desligada
  QSystemTrayIcon tray{};
  tray.setToolTip("Ítalock");
  TrayController trayController(&tray, &app);

  QLocalServer instanceServer{};
  instanceServer.setSocketOptions(QLocalServer::UserAccessOption);
  QLocalServer::removeServer(trayName);
  instanceServer.listen(trayName);
  QObject::connect(&instanceServer, &QLocalServer::newConnection, &app, [&]() {
    while(auto client = instanceServer.nextPendingConnection()) {
      QObject::connect(client, &QLocalSocket::readyRead, client, [client, &showWindow, &trayController]() {
        auto data = client->readAll();
        if(data.contains("abrir"))
          showWindow();
        else if(data.contains("preferencias"))
          trayController.ReloadPrefs(); // a janela (processo elevado) mudou o atalho
        else if(data.contains("idioma"))
          trayController.ReloadLanguage(); // a janela mudou o idioma
        client->deleteLater();
      });
    }
  });

  QMenu menu{};
  auto openAction = menu.addAction(QString());
  auto lockAction = menu.addAction(QString());
  QObject::connect(openAction, &QAction::triggered, &app, showWindow);
  QObject::connect(lockAction, &QAction::triggered, &app, []() { LockWorkStation(); });
  menu.addSeparator();
  auto quitAction = menu.addAction(QString());
  trayController.SetRetranslate([openAction, lockAction, quitAction]() {
    openAction->setText(Tr("Abrir o Ítalock"));
    lockAction->setText(Tr("Travar agora"));
    quitAction->setText(Tr("Fechar o ícone"));
  });
  QObject::connect(quitAction, &QAction::triggered, &app, [&]() {
    // Ligando/desligando a protecao ou pareando nesta mesma janela: espera terminar (nao fecha no meio).
    if(MainWindow::IsBusy()) {
      tray.showMessage("Ítalock", Tr("Espere terminar o que está em andamento na janela e tente de novo."), QSystemTrayIcon::Warning, 4000);
      return;
    }
    QCoreApplication::quit();
  });
  tray.setContextMenu(&menu);
  QObject::connect(&tray, &QSystemTrayIcon::activated, &app, [&](QSystemTrayIcon::ActivationReason reason) {
    if(reason == QSystemTrayIcon::Trigger || reason == QSystemTrayIcon::DoubleClick)
      showWindow();
  });
  tray.show();
  // Janela aberta neste mesmo processo mudou o atalho: vale na hora.
  MainWindow::SetPrefChangedHandler([&trayController](const QString &key) {
    if(key.startsWith("atalho") && key != "atalhoErro")
      trayController.ReloadPrefs();
    else if(key == "idioma")
      trayController.ReloadLanguage();
  });

  if(!startHidden)
    showWindow();

  auto result = QApplication::exec();
  MainWindow::WaitIdle();
  MainWindow::SetPrefChangedHandler(nullptr);
  delete engine;
  LoggingSystem::Destroy();
  return result;
}
#else
int main(int argc, char *argv[]) {
  SetQuickEnv();
  if(DevEnvAllowed() && !qEnvironmentVariableIsEmpty("ITALOCK_PRINT"))
    return RunPrintMode(argc, argv, qEnvironmentVariable("ITALOCK_PRINT"));
  LoggingSystem::Init("desktop");

  QGuiApplication app(argc, argv);
  QI18n::InstallTranslator();
  ApplyAppearance();
  QGuiApplication::setWindowIcon(QIcon(":/res/icons/icon.png"));

  auto url = QUrl("qrc:/ui/MainWindow.qml");
  QQmlApplicationEngine engine{};
  MaybeUseDevQml(&engine);
  QObject::connect(
      &engine, &QQmlApplicationEngine::objectCreated, &app,
      [url](QObject *obj, const QUrl &objUrl) {
        if(!obj && url == objUrl) {
          QCoreApplication::exit(-1);
        }
      },
      Qt::QueuedConnection);
  engine.load(url);

  auto result = QGuiApplication::exec();
  MainWindow::WaitIdle();
  LoggingSystem::Destroy();
  return result;
}
#endif
