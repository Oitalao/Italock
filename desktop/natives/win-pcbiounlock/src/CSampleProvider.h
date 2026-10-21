//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//

// clang-format off
#include "helpers.h"

#include <mutex>
#include <string>

#include <windows.h>
#include <strsafe.h>

#include "CUnlockCredential.h"
#include "CUnlockListener.h"
#include "ProviderLink.h"
// clang-format on

class CSampleProvider : public ICredentialProvider, public ICredentialProviderSetUserArray {
public:
  // Italock: tenta pegar uma referencia so se o objeto ainda estiver vivo (contagem > 0). Nunca ressuscita um provider
  // que ja chegou a zero e esta sendo destruido.
  bool TryAddRef() {
    long cur = _cRef;
    while(cur > 0) {
      long prev = InterlockedCompareExchange(&_cRef, cur + 1, cur);
      if(prev == cur)
        return true;
      cur = prev;
    }
    return false;
  }
  std::shared_ptr<ProviderLink> GetLink() const {
    return _link;
  }
  // Avisa a LogonUI (CredentialsChanged) se o provider ainda existir; seguro de chamar de qualquer thread.
  static void NotifyCredentialsChanged(const std::shared_ptr<ProviderLink> &link);

  // IUnknown
  // Italock: contagem atomica (a thread do CUnlockListener tambem segura referencia ao provider).
  IFACEMETHODIMP_(ULONG) AddRef() {
    return InterlockedIncrement(&_cRef);
  }

  IFACEMETHODIMP_(ULONG) Release() {
    long cRef = InterlockedDecrement(&_cRef);
    if(!cRef) {
      delete this;
    }
    return cRef;
  }

  IFACEMETHODIMP QueryInterface(_In_ REFIID riid, _COM_Outptr_ void **ppv) {
    static const QITAB qit[] = {
        QITABENT(CSampleProvider, ICredentialProvider),             // IID_ICredentialProvider
        QITABENT(CSampleProvider, ICredentialProviderSetUserArray), // IID_ICredentialProviderSetUserArray
        {0},
    };
    return QISearch(this, qit, riid, ppv);
  }

public:
  IFACEMETHODIMP SetUsageScenario(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, DWORD dwFlags);
  IFACEMETHODIMP SetSerialization(_In_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION const *pcpcs);

  IFACEMETHODIMP Advise(_In_ ICredentialProviderEvents *pcpe, _In_ UINT_PTR upAdviseContext);
  IFACEMETHODIMP UnAdvise();

  IFACEMETHODIMP GetFieldDescriptorCount(_Out_ DWORD *pdwCount);
  IFACEMETHODIMP GetFieldDescriptorAt(DWORD dwIndex, _Outptr_result_nullonfailure_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR **ppcpfd);

  IFACEMETHODIMP GetCredentialCount(_Out_ DWORD *pdwCount, _Out_ DWORD *pdwDefault, _Out_ BOOL *pbAutoLogonWithDefault);
  IFACEMETHODIMP GetCredentialAt(DWORD dwIndex, _Outptr_result_nullonfailure_ ICredentialProviderCredential **ppcpc);

  IFACEMETHODIMP SetUserArray(_In_ ICredentialProviderUserArray *users);

  friend HRESULT CSample_CreateInstance(_In_ REFIID riid, _Outptr_ void **ppv);

public:
  void AddFieldDescriptor(DWORD id, CREDENTIAL_PROVIDER_FIELD_TYPE type, const std::string &label, GUID guid = {});
  void UpdateCredsStatus() const;

protected:
  CSampleProvider();
  __override ~CSampleProvider();

private:
  void _ReleaseEnumeratedCredentials();
  void _CreateEnumeratedCredentials();
  HRESULT _EnumerateEmpty();
  HRESULT _EnumerateCredentials();
  HRESULT _EnumerateEmptyTileCredential();

private:
  volatile long _cRef; // Used for reference counting.
  std::vector<CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR> _rgCredProvFieldDescriptors;
  std::vector<CUnlockCredential *> _pCredentials;
  bool _fRecreateEnumeratedCredentials;
  CREDENTIAL_PROVIDER_USAGE_SCENARIO _cpus;
  ICredentialProviderUserArray *_pCredProviderUserArray;
  ICredentialProviderEvents *_pCredProvEvents;
  UINT_PTR _upAdviseContext; // Used to tell our owner who we are when asking to
  mutable std::mutex _eventsMutex{}; // Italock: protege _pCredProvEvents/_upAdviseContext (Advise/UnAdvise x thread do listener)
  std::shared_ptr<ProviderLink> _link{};
};
