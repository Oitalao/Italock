#include "KeyScanner.h"

#include <spdlog/spdlog.h>

#ifdef WINDOWS
#include <Windows.h>
#elif LINUX
#include <chrono>
#include <fcntl.h>
#include <filesystem>
#include <linux/input.h>
#include <thread>
#include <unistd.h>
#elif APPLE
#include <Carbon/Carbon.h>
#endif

KeyScanner::~KeyScanner() {
  Stop();
}

bool KeyScanner::GetKeyState(int key) {
#ifdef _WIN32
  return GetAsyncKeyState(key) < 0;
#endif
#ifdef LINUX
  std::lock_guard<std::mutex> lock(m_ScanMutex);
  for(const auto &keyMap : m_KeyMaps) {
    auto it = keyMap.find(key);
    if(it != keyMap.end() && it->second)
      return true;
  }
  return false;
#endif
#ifdef APPLE
  unsigned char keyMap[16];
  GetKeys((BigEndianUInt32 *)&keyMap);
  return (0 != ((keyMap[key >> 3] >> (key & 7)) & 1));
#endif
}

std::map<int, bool> KeyScanner::GetAllKeys() {
#ifdef _WIN32
  auto map = std::map<int, bool>();
  for(int i = 0; i < 0xA6; i++) {
    map[i] = GetKeyState(i);
  }
  return map;
#endif
#ifdef LINUX
  std::lock_guard<std::mutex> lock(m_ScanMutex);
  if(m_KeyMaps.empty())
    return {};
  return m_KeyMaps[0]; // ToDo
#endif
#ifdef APPLE
  auto map = std::map<int, bool>();
  for(int i = 0; i < 0x7E; i++) {
    map[i] = GetKeyState(i);
  }
  return map;
#endif
}

void KeyScanner::Start() {
#ifdef LINUX
  if(m_IsRunning)
    return;

  m_IsRunning = true;
  for(const auto &keyboard : GetKeyboards()) {
    // Italock: O_NONBLOCK ja no open e O_CLOEXEC (o descritor nao vaza para scripts filhos); falha fecha o que abriu.
    int kbd = open(keyboard.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if(kbd == -1) {
      spdlog::error("Keyboard open() failed. (Code={})", errno);
      continue;
    }
    m_KeyboardFds.push_back(kbd);
  }
  if(m_KeyboardFds.empty()) {
    spdlog::error("No keyboards found.");
    return;
  }
  // Italock: todos os mapas existem ANTES da primeira thread. Antes o emplace_back de um mapa novo realocava o vetor
  // enquanto a thread do teclado anterior escrevia no mapa dela = memoria liberada em uso (heap corrompido, "double
  // free" no pcbu_auth em maquina com mais de um teclado).
  {
    std::lock_guard<std::mutex> lock(m_ScanMutex);
    m_KeyMaps.assign(m_KeyboardFds.size(), {});
  }
  for(size_t i = 0; i < m_KeyboardFds.size(); i++)
    m_ScanThreads.emplace_back(&KeyScanner::ScanThread, this, static_cast<int>(i));
#endif
}

void KeyScanner::Stop() {
#ifdef LINUX
  if(!m_IsRunning)
    return;

  m_IsRunning = false;
  // Italock: espera as threads ANTES de fechar. Fechando antes, o numero do descritor podia ser reaproveitado por outra
  // thread (um socket do destravar) e a thread do teclado lia dados dela.
  for(auto &thread : m_ScanThreads)
    if(thread.joinable())
      thread.join();
  for(auto fd : m_KeyboardFds)
    close(fd);
  m_KeyboardFds.clear();
  m_ScanThreads.clear();
#endif
}

#ifdef LINUX
void KeyScanner::ScanThread(int idx) {
  while(m_IsRunning) {
    struct input_event ie{};
    if(read(m_KeyboardFds[idx], &ie, sizeof(ie)) == sizeof(ie) && ie.type == EV_KEY) {
      std::lock_guard<std::mutex> lock(m_ScanMutex);
      m_KeyMaps[idx][ie.code] = ie.value != 0;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }
}

std::vector<std::string> KeyScanner::GetKeyboards() {
  auto keyboards = std::vector<std::string>();
  // Italock: nem toda maquina tem /dev/input/by-path (ex.: servidor sem teclado com caminho fisico) - sem isso o
  // auxiliar abortava com filesystem_error e o desbloqueio falhava. Tenta by-path e by-id; se nao houver, segue sem teclado.
  for(const auto *dir : {"/dev/input/by-path/", "/dev/input/by-id/"}) {
    std::error_code ec{};
    if(!std::filesystem::is_directory(dir, ec))
      continue;
    for(const auto &entry : std::filesystem::directory_iterator(dir, ec)) {
      if(entry.path().filename().string().ends_with("-event-kbd"))
        keyboards.push_back(entry.path().string());
    }
    if(!keyboards.empty())
      break;
  }
  return keyboards;
}
#endif
