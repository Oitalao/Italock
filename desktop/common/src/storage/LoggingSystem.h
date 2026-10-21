#ifndef PCBU_DESKTOP_LOGGINGSYSTEM_H
#define PCBU_DESKTOP_LOGGINGSYSTEM_H

#include <memory>
#include <spdlog/sinks/rotating_file_sink.h>
#include <spdlog/spdlog.h>

class LoggingSystem {
public:
  static void Init(const std::string &logName, bool printToConsole = true);
  static void Destroy();
  static std::shared_ptr<spdlog::sinks::rotating_file_sink_mt> MakeFileSink(const std::string &path, size_t maxSize, size_t maxFiles);

private:
  LoggingSystem() = default;
  static std::string g_LogName;
};

#endif // PCBU_DESKTOP_LOGGINGSYSTEM_H
