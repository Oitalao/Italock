#ifndef PCBU_DESKTOP_MAINWINDOW_H
#define PCBU_DESKTOP_MAINWINDOW_H

#include <QObject>
#include <QQmlApplicationEngine>
#include <QVariantList>
#include <QtQmlIntegration>
#include <functional>
#include <thread>

// Italock: ponte da tela principal (lista de celulares e o interruptor da tela de bloqueio).
class MainWindow : public QObject {
  Q_OBJECT
  QML_ELEMENT
  QML_SINGLETON
  // Aparencia efetiva: "escuro", "claro" ou "sistema" (ITALOCK_TEMA tem prioridade). O Theme.qml le daqui porque no
  // Linux o setColorScheme do Qt nao tem efeito.
  Q_PROPERTY(QString appearance READ GetAppearance NOTIFY appearanceChanged)
public:
  QString GetAppearance();
signals:
  void appearanceChanged();
public:
  ~MainWindow() override;

  Q_INVOKABLE bool IsInstalled();
  Q_INVOKABLE bool IsProtectionOn();
  Q_INVOKABLE QString GetLicenseText();
  // Endereço público do código do Ítalock (constante ITALOCK_SOURCE_URL em MainWindow.cpp; vazia até ir para o GitHub).
  Q_INVOKABLE QString GetSourceUrl();
  Q_INVOKABLE QString GetHostName();
  Q_INVOKABLE bool IsWindows();
  // Desenvolvimento: ITALOCK_PAGINA abre direto numa tela (para conferir o visual).
  Q_INVOKABLE QString DevPage();

  // [{id, name, user, method, lastUsed (segundos desde 1970, 0 = nunca)}]
  Q_INVOKABLE QVariantList GetDevices();
  // "" = ok, senao a mensagem de erro
  Q_INVOKABLE QString RemoveDevice(const QString &pairingId);

  // Historico (ultimos 50 eventos, do mais novo ao mais antigo):
  // [{ts (segundos desde 1970), name, action ("destravar"|"travar"|"acordar"), result ("ok"|"recusado"|"expirou"|
  //   "sem_resposta"|"desligado"|"falhou"), path, program}]
  Q_INVOKABLE QVariantList GetHistory();
  // "" = ok, senao a mensagem de erro
  Q_INVOKABLE QString ClearHistory();

  Q_INVOKABLE bool PerformStartupChecks(QObject *window);

  // Preferencias da pessoa (por usuario, nao do sistema): "aparencia" = escuro|claro|sistema (padrao escuro),
  // "avisos" (padrao ligado), "som" (padrao desligado), "atalho" (padrao desligado), "atalhoTecla" (padrao
  // "ctrl+alt+l"), "atalhoErro" (a bandeja nao conseguiu registrar a combinacao). Guardadas em QSettings
  // (Windows: HKCU\Software\Italock).
  Q_INVOKABLE QVariant GetPref(const QString &key);
  Q_INVOKABLE void SetPref(const QString &key, const QVariant &value);

  // Aplica a aparencia salva no app inteiro (janelas e barras de titulo).
  static void ApplyAppearanceSetting();
  // Chamado quando uma preferencia muda (a bandeja relê o atalho de teclado). Definido pelo main.
  static void SetPrefChangedHandler(std::function<void(const QString &)> handler);
  // Desenvolvimento (ITALOCK_PRINT, so sem administrador/root): pula as checagens de inicio.
  static bool s_PrintMode;
  // Trabalho em andamento numa thread (ligar/desligar a protecao): a bandeja nao fecha e a saida espera.
  static bool IsBusy();
  static void WaitIdle();

public slots:
  // Liga/desliga a protecao da tela de bloqueio numa thread; a janela recebe showLoadingScreen/appendLoadingOutput/
  // finishLoadingScreen(ok, texto).
  void SetProtection(QObject *window, bool enabled);

private:
  std::thread m_LoadingThread{};
};

#endif // PCBU_DESKTOP_MAINWINDOW_H
