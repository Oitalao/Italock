#include "LogsWindow.h"

#include <spdlog/spdlog.h>

#include "shell/Shell.h"
#include "storage/AppSettings.h"

LogsWindow::~LogsWindow() {
  if(m_LoadThread.joinable())
    m_LoadThread.join();
}

void LogsWindow::LoadLogs(QObject *window) {
  if(m_LoadThread.joinable())
    m_LoadThread.join();
  m_LoadThread = std::thread([window]() {
    spdlog::default_logger()->flush();
    auto desktopLogs = Shell::ReadBytes(AppSettings::GetBaseDir() / "desktop.log");
    auto moduleLogs = Shell::ReadBytes(AppSettings::GetBaseDir() / "module.log");
    auto agentLogs = Shell::ReadBytes(AppSettings::GetBaseDir() / "agent.log");
    auto toText = [](const std::vector<uint8_t> &bytes) { return QString::fromUtf8(reinterpret_cast<const char *>(bytes.data()), static_cast<qsizetype>(bytes.size())); };
    QMetaObject::invokeMethod(window, "setLogs", Q_ARG(QVariant, toText(desktopLogs)), Q_ARG(QVariant, toText(moduleLogs)), Q_ARG(QVariant, toText(agentLogs)));
  });
}
