#pragma once

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include <credentialprovider.h>

#include "ProviderLink.h"

class CUnlockCredential;
class CUnlockListener {
public:
  CUnlockListener() = default;
  void Initialize(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, std::shared_ptr<ProviderLink> providerLink, CUnlockCredential *pCredential,
                  const std::wstring &userDomain);
  void Release();

  void Start(bool ignoreWaitKeyPress = false);
  void Stop();

  bool HasResponse() const;

private:
  enum class WaitResult { STOPPED, KEY, WAKE, FOREGROUND, CANCELED, TIMEOUT };
  // O que libera a espera: tecla, a tela da LogonUI em primeiro plano, e sempre o Destravar do celular (evento).
  enum class WaitFor { KEY, FOREGROUND, WAKE_ONLY };
  void ListenThread();
  WaitResult Wait(WaitFor what, DWORD timeoutMs);
  void EnsureWakeEvent();

  std::thread m_ListenThread{};
  std::atomic<bool> m_IsRunning{};
  std::atomic<bool> m_HasResponse{};
  bool m_IgnoreWaitKeyPress{};
  std::mutex m_ControlMutex{}; // Start/Stop vindos de threads diferentes da LogonUI
  ULONGLONG m_LastKeyAttemptMs{}; // ultima tentativa disparada por tecla (ou cancelada): tecla so dispara de novo depois de 15 s

  CREDENTIAL_PROVIDER_USAGE_SCENARIO m_ProviderUsage{};
  std::shared_ptr<ProviderLink> m_ProviderLink{}; // referencia fraca ao provider
  CUnlockCredential *m_Credential{};
  std::wstring m_UserDomain{};
  HANDLE m_WakeEvent{}; // Global\ItalockWake-<sessao>, vive do inicio do ListenThread ate o Stop
};
