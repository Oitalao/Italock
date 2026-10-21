#include "PairingForm.h"

#include <QCoreApplication>
#include <QPointer>

#include "installer/ServiceInstaller.h"
#include "platform/BluetoothHelper.h"
#include "platform/NetworkHelper.h"
#include "platform/PlatformHelper.h"
#include "storage/AppSettings.h"
#include "utils/QRUtils.h"
#include "utils/StringUtils.h"

// Resultado de uma thread de trabalho para a tela: a chamada vai enfileirada para a thread da interface e so la o
// QPointer e conferido (testar QPointer fora da thread dona nao e seguro).
template<typename... Args> static void PostToWindow(const QPointer<QObject> &win, const char *method, Args... args) {
  QMetaObject::invokeMethod(
      QCoreApplication::instance(),
      [win, method, args...]() {
        if(win)
          QMetaObject::invokeMethod(win.data(), method, Q_ARG(QVariant, QVariant::fromValue(args))...);
      },
      Qt::QueuedConnection);
}

PairingForm::~PairingForm() {
  Cancel();
  if(m_CheckThread.joinable())
    m_CheckThread.join();
}

QString PairingForm::Begin() {
  Cancel();
  m_UserName.clear();
  m_Password.clear();
  if(!ServiceInstaller::IsInstalled())
    return QString::fromUtf8(I18n::Get("error_pairing_not_installed"));
  return {};
}

bool PairingForm::HasBluetooth() {
  return BluetoothHelper::IsAvailable();
}

QString PairingForm::GetTailscaleIP() {
  for(const auto &netIf : NetworkHelper::GetLocalNetInterfaces(false)) {
    unsigned a = 0, b = 0;
    if(sscanf(netIf.ipAddress.c_str(), "%u.%u", &a, &b) == 2 && a == 100 && b >= 64 && b <= 127)
      return QString::fromUtf8(netIf.ipAddress);
  }
  return {};
}

QVariantMap PairingForm::CheckAccount(const QString &userNameQ, const QString &passwordQ, bool isManualUserName, bool skipCheck) {
  auto userName = userNameQ.trimmed().toStdString();
  auto password = passwordQ.toStdString();
  auto result = DoCheckAccount(userName, password, isManualUserName, skipCheck);
  if(result["ok"].toBool()) {
    m_UserName = userName;
    m_Password = password;
  }
  return result;
}

void PairingForm::CheckAccountAsync(QObject *window, const QString &userNameQ, const QString &passwordQ, bool isManualUserName, bool skipCheck) {
  if(m_CheckThread.joinable())
    m_CheckThread.join();
  auto userName = userNameQ.trimmed().toStdString();
  auto password = passwordQ.toStdString();
  QPointer<QObject> win(window);
  QPointer<PairingForm> self(this);
  m_CheckThread = std::thread([win, self, userName, password, isManualUserName, skipCheck]() {
    auto result = DoCheckAccount(userName, password, isManualUserName, skipCheck);
    QMetaObject::invokeMethod(
        QCoreApplication::instance(),
        [win, self, result, userName, password]() {
          if(self && result["ok"].toBool()) {
            self->m_UserName = userName;
            self->m_Password = password;
          }
          if(win)
            QMetaObject::invokeMethod(win.data(), "accountChecked", Q_ARG(QVariant, QVariant(result)));
        },
        Qt::QueuedConnection);
  });
}

QVariantMap PairingForm::DoCheckAccount(const std::string &userName, const std::string &password, bool isManualUserName, bool skipCheck) {
  QVariantMap result{{"ok", false}, {"error", QString()}, {"canSkip", false}};
  if(userName.empty()) {
    result["error"] = "Escolha a conta.";
    return result;
  }
#ifdef WINDOWS
  if(StringUtils::Split(userName, "\\").size() != 2 && userName.find('@') == std::string::npos) {
    result["error"] = QString::fromUtf8(I18n::Get("error_win_invalid_user"));
    return result;
  }
#endif
  if(!skipCheck) {
    if(!PlatformHelper::HasUserPassword(userName)) {
      result["error"] = QString::fromUtf8(isManualUserName ? I18n::Get("error_invalid_user_or_no_password") : I18n::Get("error_user_no_password"));
      return result;
    }
    auto loginStatus = PlatformHelper::CheckLogin(userName, password);
    std::string errorStr{};
    switch(loginStatus.result) {
      case PlatformLoginResult::INVALID_USER:
        errorStr = I18n::Get("error_invalid_user_input");
        break;
      case PlatformLoginResult::INVALID_PASSWORD:
        errorStr = isManualUserName ? I18n::Get("error_invalid_user_or_incorrect_password") : I18n::Get("error_incorrect_password");
        result["canSkip"] = true;
        break;
      case PlatformLoginResult::ACCOUNT_LOCKED:
        errorStr = I18n::Get("error_win_account_locked");
        break;
      case PlatformLoginResult::ACCOUNT_RESTRICTED:
        errorStr = I18n::Get("error_win_account_restricted");
        break;
      case PlatformLoginResult::PW_EXPIRED:
        errorStr = I18n::Get("error_win_password_expired");
        break;
      case PlatformLoginResult::ACCOUNT_DISABLED:
        errorStr = I18n::Get("error_account_disabled");
        break;
      case PlatformLoginResult::LOGON_TYPE_DENIED:
        errorStr = I18n::Get("error_win_logon_type_denied");
        break;
      case PlatformLoginResult::UNKNOWN_ERROR:
        errorStr = I18n::Get("error_login_unknown");
        if(loginStatus.errorCode != 0)
          errorStr = I18n::Get("error_with_code", errorStr, loginStatus.errorCode);
        break;
      case PlatformLoginResult::SUCCESS:
        break;
    }
    if(!errorStr.empty()) {
      result["error"] = QString::fromUtf8(errorStr);
      return result;
    }
  }
  result["ok"] = true;
  return result;
}

void PairingForm::StartBluetoothScan(QObject *window) {
  StopBluetoothScan();
  m_IsBluetoothScanRunning = true;
  QPointer<QObject> win(window);
  m_BluetoothScanThread = std::thread([this, win]() {
    BluetoothHelper::StartScan();
    while(m_IsBluetoothScanRunning) {
      auto devices = BluetoothHelper::ScanDevices();
      if(!m_IsBluetoothScanRunning)
        break;
      QVariantList uiDevices{};
      for(const auto &device : devices) {
        BluetoothDeviceModel entry = {QString::fromUtf8(device.name), QString::fromUtf8(device.address)};
        uiDevices.append(QVariant::fromValue(entry));
      }
      PostToWindow(win, "updateBluetoothDeviceList", uiDevices);
    }
    BluetoothHelper::StopScan();
  });
}

void PairingForm::StopBluetoothScan() {
  m_IsBluetoothScanRunning = false;
  if(m_BluetoothScanThread.joinable())
    m_BluetoothScanThread.join();
}

void PairingForm::PairBluetooth(QObject *window, const QString &address) {
  StopBluetoothScan();
  if(m_BluetoothPairThread.joinable())
    m_BluetoothPairThread.detach(); // pareamento anterior ainda esperando o sistema: segue sozinho
  auto device = BluetoothDevice();
  device.address = address.toStdString();
  QPointer<QObject> win(window);
  m_BluetoothPairThread = std::thread([win, device]() {
    auto result = BluetoothHelper::PairDevice(device);
    PostToWindow(win, "finishBluetoothPairing", result);
  });
}

std::string PairingForm::BuildPayload() {
  auto settings = AppSettings::Get();
  auto method = m_Method == "bluetooth" ? PairingMethod::BLUETOOTH : PairingMethod::UDP;
  if(!m_LegacyIP.empty())
    return LegacyPairingQRData(m_LegacyIP, settings.pairingServerPort, method, m_EncKey).ToJson().dump();
  return PairingQRData(m_ServerId, settings.pairingDiscoveryPort, method, m_EncKey).ToJson().dump();
}

QVariantMap PairingForm::StartQR(QObject *window, const QString &methodQ, const QString &bluetoothAddress) {
  QVariantMap result{{"qr", QString()}, {"code", QString()}, {"error", QString()}};
  if(m_UserName.empty()) {
    result["error"] = "Confirme a conta primeiro.";
    return result;
  }
  StopBluetoothScan();
  if(m_PairingServer) {
    m_PairingServer->Stop();
    m_PairingServer.reset();
  }
  if(m_DiscoveryBeacon) {
    m_DiscoveryBeacon->Stop();
    m_DiscoveryBeacon.reset();
  }

  m_Method = methodQ.toStdString();
  m_LegacyIP.clear();
  if(m_Method == "tailscale") {
    m_LegacyIP = GetTailscaleIP().toStdString();
    if(m_LegacyIP.empty()) {
      result["error"] = "O Tailscale não está ligado neste computador. Ligue e tente de novo.";
      return result;
    }
  }
  m_ServerId = StringUtils::RandomString(8);
  m_EncKey = StringUtils::RandomString(64);

  QPointer<QObject> win(window);
  m_PairingServer = std::make_unique<PairingServer>(
      [win](const std::string &error) { PostToWindow(win, "showErrorMessage", QString::fromUtf8(error)); },
      [win](const std::string &deviceName) { PostToWindow(win, "pairingSucceeded", QString::fromUtf8(deviceName)); });
  auto uiData = PairingUIData();
  uiData.userName = m_UserName;
  uiData.password = m_Password;
  uiData.encKey = m_EncKey;
  uiData.method = m_Method == "bluetooth" ? PairingMethod::BLUETOOTH : PairingMethod::UDP;
  uiData.macAddress = NetworkHelper::GetSavedNetworkInterface().macAddress;
  uiData.btAddress = m_Method == "bluetooth" ? bluetoothAddress.toStdString() : std::string{};
  uiData.useLegacy = !m_LegacyIP.empty();
  m_PairingServer->Start(uiData);
  if(m_LegacyIP.empty()) {
    m_DiscoveryBeacon = std::make_unique<UDPPairingBroadcaster>(m_ServerId, m_EncKey);
    m_DiscoveryBeacon->Start();
  }

  auto payload = BuildPayload();
  result["qr"] = QString::fromUtf8("data:image/svg+xml;utf8," + QRUtils::GenerateSVG(payload));
  result["code"] = QString::fromUtf8(StringUtils::WithSeperators(StringUtils::ToBase32String(payload), " ", 5));
  return result;
}

void PairingForm::StopQR() {
  if(m_PairingServer) {
    m_PairingServer->Stop();
    m_PairingServer.reset();
  }
  if(m_DiscoveryBeacon) {
    m_DiscoveryBeacon->Stop();
    m_DiscoveryBeacon.reset();
  }
  m_EncKey.clear();
  m_ServerId.clear();
}

void PairingForm::Cancel() {
  StopBluetoothScan();
  if(m_BluetoothPairThread.joinable())
    m_BluetoothPairThread.detach(); // o sistema pode estar esperando confirmacao no celular: nao prende a tela
  if(m_PairingServer) {
    m_PairingServer->Stop();
    m_PairingServer.reset();
  }
  if(m_DiscoveryBeacon) {
    m_DiscoveryBeacon->Stop();
    m_DiscoveryBeacon.reset();
  }
  if(!m_Password.empty()) {
    std::fill(m_Password.begin(), m_Password.end(), '\0');
    m_Password.clear();
  }
  m_EncKey.clear();
  m_ServerId.clear();
}
