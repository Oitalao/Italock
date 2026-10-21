#pragma once

#include <mutex>

class CSampleProvider;

// Italock: referencia fraca ao provider, compartilhada (shared_ptr) com as credenciais e seus listeners.
// O destrutor do provider zera "provider" sob a trava; quem quer usar o provider pega a trava e so segue se conseguir
// uma referencia de verdade (CSampleProvider::TryAddRef, que falha se a contagem ja chegou a zero).
struct ProviderLink {
  std::mutex mutex{};
  CSampleProvider *provider{};
};
