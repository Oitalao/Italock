#ifndef PCBU_DESKTOP_PAIRINGFORM_H
#define PCBU_DESKTOP_PAIRINGFORM_H

#include <QVariantMap>
#include <QtQmlIntegration>
#include <atomic>

#include "connection/pairing/PairingServer.h"
#include "connection/pairing/UDPPairingBroadcaster.h"

struct BluetoothDeviceModel {
  Q_GADGET
public:
  QString name{};
  QString address{};
  Q_PROPERTY(QString name MEMBER name)
  Q_PROPERTY(QString address MEMBER address)
};

// Italock: assistente de pareamento. A tela (QML) controla os passos; aqui ficam a conferencia da senha, a busca
// Bluetooth e o servidor de pareamento. Metodos: "wifi" (UDP com descoberta), "bluetooth", "tailscale" (QR com o IP do
// Tailscale deste computador - o celular conecta direto).
class PairingForm : public QObject {
  Q_OBJECT
  QML_ELEMENT
  QML_SINGLETON
public:
  ~PairingForm() override;

  // "" = pode comecar, senao o motivo
  Q_INVOKABLE QString Begin();
  // {ok, error, canSkip}
  Q_INVOKABLE QVariantMap CheckAccount(const QString &userName, const QString &password, bool isManualUserName, bool skipCheck);
  // Mesmo que CheckAccount, numa thread (a conferencia da senha leva ~1 s e travava a tela); o resultado chega em
  // window.accountChecked(resultado).
  Q_INVOKABLE void CheckAccountAsync(QObject *window, const QString &userName, const QString &password, bool isManualUserName, bool skipCheck);
  Q_INVOKABLE bool HasBluetooth();
  Q_INVOKABLE QString GetTailscaleIP();

  Q_INVOKABLE void StartBluetoothScan(QObject *window);
  Q_INVOKABLE void StopBluetoothScan();
  Q_INVOKABLE void PairBluetooth(QObject *window, const QString &address);

  // Liga o servidor de pareamento e devolve {qr (data URL do SVG), code, error}.
  Q_INVOKABLE QVariantMap StartQR(QObject *window, const QString &method, const QString &bluetoothAddress);
  // Para o servidor de pareamento (voltar do passo do QR), mantendo a conta ja conferida.
  Q_INVOKABLE void StopQR();
  Q_INVOKABLE void Cancel();

private:
  std::string BuildPayload();
  static QVariantMap DoCheckAccount(const std::string &userName, const std::string &password, bool isManualUserName, bool skipCheck);

  std::string m_UserName{};
  std::string m_Password{};
  std::string m_Method{};
  std::string m_LegacyIP{};
  std::string m_EncKey{};
  std::string m_ServerId{};

  std::atomic<bool> m_IsBluetoothScanRunning{};
  std::thread m_BluetoothScanThread{};
  std::thread m_BluetoothPairThread{};
  std::thread m_CheckThread{};
  std::unique_ptr<PairingServer> m_PairingServer = nullptr;
  std::unique_ptr<UDPPairingBroadcaster> m_DiscoveryBeacon = nullptr;
};

#endif // PCBU_DESKTOP_PAIRINGFORM_H
