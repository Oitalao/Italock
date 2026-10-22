#include "MainWindow.h"

#include <QCoreApplication>
#include <QGuiApplication>
#include <QSettings>
#include <QStyleHints>
#include <QThread>
#include <QVariantMap>
#include <atomic>

#include "installer/ServiceInstaller.h"
#include "platform/NetworkHelper.h"
#include "platform/PlatformHelper.h"
#include "shell/Shell.h"
#include "storage/AppSettings.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/AppInfo.h"
#include "utils/I18n.h"
#include "utils/ResourceHelper.h"
#include "utils/StringUtils.h"

static std::atomic<int> g_BusyCount{0};
static std::function<void(const QString &)> g_PrefChanged{};
bool MainWindow::s_PrintMode = false;

void MainWindow::SetPrefChangedHandler(std::function<void(const QString &)> handler) {
  g_PrefChanged = std::move(handler);
}

void MainWindow::NotifyPrefChanged(const QString &key) {
  if(g_PrefChanged)
    g_PrefChanged(key);
}

bool MainWindow::IsBusy() {
  return g_BusyCount.load() > 0;
}

void MainWindow::WaitIdle() {
  // Espera ate 2 min o trabalho em andamento (reinstalar o modulo pode levar alguns segundos).
  for(int i = 0; i < 1200 && IsBusy(); i++) {
    QCoreApplication::processEvents(QEventLoop::AllEvents, 50);
    QThread::msleep(100);
  }
}

static QSettings Prefs() {
  return QSettings("Italock", "Italock");
}

QVariant MainWindow::GetPref(const QString &key) {
  auto prefs = Prefs();
  if(key == "aparencia")
    return prefs.value(key, "escuro");
  if(key == "avisos")
    return prefs.value(key, true).toBool();
  if(key == "som" || key == "atalho" || key == "atalhoErro")
    return prefs.value(key, false).toBool();
  if(key == "atalhoTecla")
    return prefs.value(key, "ctrl+alt+l");
  return prefs.value(key);
}

QString MainWindow::GetAppearance() {
  auto tema = qEnvironmentVariable("ITALOCK_TEMA");
  if(tema == "escuro" || tema == "claro")
    return tema;
  auto v = Prefs().value("aparencia", "escuro").toString();
  return v == "claro" || v == "sistema" ? v : QString("escuro");
}

void MainWindow::SetPref(const QString &key, const QVariant &value) {
  auto prefs = Prefs();
  prefs.setValue(key, value);
  prefs.sync();
  if(g_PrefChanged)
    g_PrefChanged(key);
  if(key == "aparencia") {
    if(qEnvironmentVariableIsEmpty("ITALOCK_TEMA"))
      ApplyAppearanceSetting();
    emit appearanceChanged();
  }
}

void MainWindow::ApplyAppearanceSetting() {
  auto value = Prefs().value("aparencia", "escuro").toString();
  auto hints = QGuiApplication::styleHints();
  if(value == "claro")
    hints->setColorScheme(Qt::ColorScheme::Light);
  else if(value == "sistema")
    hints->setColorScheme(Qt::ColorScheme::Unknown);
  else
    hints->setColorScheme(Qt::ColorScheme::Dark);
}

MainWindow::~MainWindow() {
  if(m_LoadingThread.joinable())
    m_LoadingThread.join();
}

bool MainWindow::IsInstalled() {
  return ServiceInstaller::IsInstalled();
}

bool MainWindow::IsProtectionOn() {
  return ServiceInstaller::IsProtectionOn();
}

bool MainWindow::IsWindows() {
#ifdef WINDOWS
  return true;
#else
  return false;
#endif
}

QString MainWindow::DevPage() {
  return qEnvironmentVariable("ITALOCK_PAGINA");
}

QString MainWindow::GetHostName() {
  return QString::fromUtf8(NetworkHelper::GetHostName());
}

// Italock: endereco do codigo do programa (GPL). Unico lugar a mudar quando o projeto for para o GitHub.
static constexpr auto ITALOCK_SOURCE_URL = "https://github.com/Oitalao/Italock";

QString MainWindow::GetSourceUrl() {
  return QString::fromUtf8(ITALOCK_SOURCE_URL);
}

QString MainWindow::GetLicenseText() {
  try {
    return QString::fromUtf8(ResourceHelper::GetResource(":/res/licenses.txt"));
  } catch(...) {
  }
  return {};
}

// Nome do metodo em linguagem comum (nada de UDP/TCP na tela).
static QString MethodLabel(const PairedDevice &device) {
  bool hasTailscale = false;
  for(const auto &ip : device.ipAddresses) {
    unsigned a = 0, b = 0;
    if(sscanf(ip.c_str(), "%u.%u", &a, &b) == 2 && a == 100 && b >= 64 && b <= 127)
      hasTailscale = true;
  }
  switch(device.pairingMethod) {
    case PairingMethod::BLUETOOTH:
      return "Bluetooth";
    case PairingMethod::UDP:
    case PairingMethod::MANUAL_UDP:
      return hasTailscale ? QString::fromStdString(I18n::Tr("Mesma rede e internet (Tailscale)")) : QString::fromStdString(I18n::Tr("Mesma rede"));
    case PairingMethod::TCP:
      return QString::fromStdString(I18n::Tr("Mesma rede (conexão direta)"));
    case PairingMethod::CLOUD_TCP:
    case PairingMethod::CLOUD_BT:
      return QString::fromStdString(I18n::Tr("Pela internet"));
  }
  return QString::fromStdString(I18n::Tr("Mesma rede"));
}

QVariantList MainWindow::GetDevices() {
  QVariantList result{};
  auto lastUses = PairedDevicesStorage::GetLastUses();
  for(const auto &device : PairedDevicesStorage::GetDevices()) {
    QVariantMap entry{};
    entry["id"] = QString::fromUtf8(device.id);
    entry["name"] = QString::fromUtf8(device.deviceName.empty() ? I18n::Tr("Celular") : device.deviceName);
    entry["user"] = QString::fromUtf8(device.userName);
    entry["method"] = MethodLabel(device);
    auto it = lastUses.find(device.id);
    entry["lastUsed"] = it != lastUses.end() ? static_cast<double>(it->second) : 0.0;
    result.append(entry);
  }
  return result;
}

QString MainWindow::RemoveDevice(const QString &pairingId) {
  if(!PairedDevicesStorage::RemoveDevice(pairingId.toStdString()))
    return QString::fromStdString(I18n::Tr("Não deu para remover agora: o arquivo de pareamentos está ocupado ou ilegível. Nada foi apagado; tente de novo."));
  return {};
}

QVariantList MainWindow::GetHistory() {
  QVariantList result{};
  auto events = PairedDevicesStorage::GetHistory();
  for(auto it = events.rbegin(); it != events.rend(); ++it) {
    QVariantMap entry{};
    entry["ts"] = static_cast<double>(it->ts);
    entry["name"] = QString::fromUtf8(StringUtils::SafeDeviceName(it->name));
    entry["action"] = QString::fromUtf8(it->action);
    entry["result"] = QString::fromUtf8(it->result);
    entry["path"] = QString::fromUtf8(it->path);
    entry["program"] = QString::fromUtf8(it->program);
    result.append(entry);
  }
  return result;
}

QString MainWindow::ClearHistory() {
  if(!PairedDevicesStorage::ClearHistory())
    return QString::fromStdString(I18n::Tr("Não deu para limpar agora: o arquivo está ocupado. Tente de novo em alguns segundos."));
  return {};
}

bool MainWindow::PerformStartupChecks(QObject *window) {
  if(s_PrintMode)
    return true; // print da tela para conferir o visual (sem administrador): nada de aviso de erro por cima
  if(!Shell::IsRunningAsAdmin()) {
    QMetaObject::invokeMethod(window, "showFatalErrorMessage", Q_ARG(QVariant, QString::fromUtf8(I18n::Get("error_not_admin"))));
    return false;
  }
#if defined(LINUX) || defined(APPLE)
  auto missing = [window](const char *what) {
    QMetaObject::invokeMethod(window, "showFatalErrorMessage", Q_ARG(QVariant, QString::fromUtf8(I18n::Get("error_unix_missing_dep", what))));
    return false;
  };
  if(Shell::RunUserCommand("command -v bash").exitCode != 0)
    return missing("bash");
#ifdef LINUX
  if(Shell::RunUserCommand("test -f /etc/shadow").exitCode != 0)
    return missing("/etc/shadow");
  if(!PlatformHelper::HasNativeLibrary("libcrypt.so.1"))
    return missing("libcrypt.so.1 (libxcrypt-compat)");
  if(!PlatformHelper::HasNativeLibrary("libcrypto.so.3") || !PlatformHelper::HasNativeLibrary("libssl.so.3"))
    return missing("OpenSSL 3");
  if(!PlatformHelper::HasNativeLibrary("libbluetooth.so") && !PlatformHelper::HasNativeLibrary("libbluetooth.so.3"))
    return missing("libbluetooth");
#endif
#endif
  // Italock: o instalador (Inno / .run) ja troca o modulo da tela de bloqueio e do PAM na atualizacao. Reinstalar daqui
  // apagava o modulo em uso (a LogonUI segura a dll) e mexia no PAM a cada versao nova - agora so anota a versao.
  const auto installedVersion = AppSettings::Get().installedVersion;
  if(ServiceInstaller::IsInstalled() && installedVersion != AppInfo::GetVersion())
    AppSettings::SetInstalledVersion(true);
  return true;
}

void MainWindow::SetProtection(QObject *window, bool enabled) {
  if(m_LoadingThread.joinable())
    m_LoadingThread.join();
  g_BusyCount++;
  m_LoadingThread = std::thread([window, enabled]() {
    struct BusyGuard {
      ~BusyGuard() { g_BusyCount--; }
    } busyGuard;
    QMetaObject::invokeMethod(window, "showLoadingScreen",
                              Q_ARG(QVariant, QString::fromStdString(enabled ? I18n::Tr("Ligando a proteção da tela de bloqueio…") : I18n::Tr("Desligando a proteção…"))));
    auto logCallback = [window](const std::string &str) {
      spdlog::info(str);
      QMetaObject::invokeMethod(window, "appendLoadingOutput", Q_ARG(QVariant, QString::fromUtf8(str)));
    };
    auto installer = ServiceInstaller(logCallback);
    try {
      installer.SetProtection(enabled);
      AppSettings::SetInstalledVersion(ServiceInstaller::IsInstalled());
      QMetaObject::invokeMethod(window, "finishLoadingScreen", Q_ARG(QVariant, true),
                                Q_ARG(QVariant, QString::fromStdString(enabled ? I18n::Tr("Proteção ligada.") : I18n::Tr("Proteção desligada."))));
    } catch(const std::exception &ex) {
      logCallback(ex.what());
      QMetaObject::invokeMethod(window, "finishLoadingScreen", Q_ARG(QVariant, false), Q_ARG(QVariant, QString::fromUtf8(ex.what())));
    }
  });
}
