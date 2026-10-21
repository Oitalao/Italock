//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// CSampleCredential is our implementation of ICredentialProviderCredential.
// ICredentialProviderCredential is what LogonUI uses to let a credential
// provider specify what a user tile looks like and then tell it what the
// user has entered into the tile.  ICredentialProviderCredential is also
// responsible for packaging up the users credentials into a buffer that
// LogonUI then sends on to LSA.

#pragma once

// clang-format off
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <windows.h>
#include <strsafe.h>
#include <shlguid.h>
#include <propkey.h>

#include "common.h"
#include "CUnlockListener.h"
#include "Dll.h"
#include "resource.h"
#include "handler/UnlockHandler.h"
// clang-format on

class CUnlockCredential : public ICredentialProviderCredential2, ICredentialProviderCredentialWithFieldOptions {
public:
  // IUnknown
  // Italock: contagem atomica (a LogonUI chama de mais de uma thread).
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
        QITABENT(CUnlockCredential, ICredentialProviderCredential),                 // IID_ICredentialProviderCredential
        QITABENT(CUnlockCredential, ICredentialProviderCredential2),                // IID_ICredentialProviderCredential2
        QITABENT(CUnlockCredential, ICredentialProviderCredentialWithFieldOptions), // IID_ICredentialProviderCredentialWithFieldOptions
        {0},
    };
    return QISearch(this, qit, riid, ppv);
  }

public:
  // ICredentialProviderCredential
  IFACEMETHODIMP Advise(_In_ ICredentialProviderCredentialEvents *pcpce);
  IFACEMETHODIMP UnAdvise();

  IFACEMETHODIMP SetSelected(_Out_ BOOL *pbAutoLogon);
  IFACEMETHODIMP SetDeselected();

  IFACEMETHODIMP GetFieldState(DWORD dwFieldID, _Out_ CREDENTIAL_PROVIDER_FIELD_STATE *pcpfs,
                               _Out_ CREDENTIAL_PROVIDER_FIELD_INTERACTIVE_STATE *pcpfis);

  IFACEMETHODIMP GetStringValue(DWORD dwFieldID, _Outptr_result_nullonfailure_ PWSTR *ppwsz);
  IFACEMETHODIMP GetBitmapValue(DWORD dwFieldID, _Outptr_result_nullonfailure_ HBITMAP *phbmp);
  IFACEMETHODIMP GetCheckboxValue(DWORD dwFieldID, _Out_ BOOL *pbChecked, _Outptr_result_nullonfailure_ PWSTR *ppwszLabel);
  IFACEMETHODIMP GetComboBoxValueCount(DWORD dwFieldID, _Out_ DWORD *pcItems, _Deref_out_range_(<, *pcItems) _Out_ DWORD *pdwSelectedItem);
  IFACEMETHODIMP GetComboBoxValueAt(DWORD dwFieldID, DWORD dwItem, _Outptr_result_nullonfailure_ PWSTR *ppwszItem);
  IFACEMETHODIMP GetSubmitButtonValue(DWORD dwFieldID, _Out_ DWORD *pdwAdjacentTo);

  IFACEMETHODIMP SetStringValue(DWORD dwFieldID, _In_ PCWSTR pwz);
  IFACEMETHODIMP SetCheckboxValue(DWORD dwFieldID, BOOL bChecked);
  IFACEMETHODIMP SetComboBoxSelectedValue(DWORD dwFieldID, DWORD dwSelectedItem);
  IFACEMETHODIMP CommandLinkClicked(DWORD dwFieldID);

  IFACEMETHODIMP GetSerialization(_Out_ CREDENTIAL_PROVIDER_GET_SERIALIZATION_RESPONSE *pcpgsr,
                                  _Out_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION *pcpcs, _Outptr_result_maybenull_ PWSTR *ppwszOptionalStatusText,
                                  _Out_ CREDENTIAL_PROVIDER_STATUS_ICON *pcpsiOptionalStatusIcon);
  IFACEMETHODIMP ReportResult(NTSTATUS ntsStatus, NTSTATUS ntsSubstatus, _Outptr_result_maybenull_ PWSTR *ppwszOptionalStatusText,
                              _Out_ CREDENTIAL_PROVIDER_STATUS_ICON *pcpsiOptionalStatusIcon);

  // ICredentialProviderCredential2
  IFACEMETHODIMP GetUserSid(_Outptr_result_nullonfailure_ PWSTR *ppszSid);

  // ICredentialProviderCredentialWithFieldOptions
  IFACEMETHODIMP GetFieldOptions(DWORD dwFieldID, _Out_ CREDENTIAL_PROVIDER_CREDENTIAL_FIELD_OPTIONS *pcpcfo);

public:
  HRESULT Initialize(CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus, _In_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR const *rgcpfd,
                     _In_ FIELD_STATE_PAIR const *rgfsp, _In_ ICredentialProviderUser *pcpUser, _In_ CSampleProvider *pProvider,
                     _In_ const std::wstring &userDomain);
  CUnlockCredential();

  bool IsSelected() const;
  bool IsUnlockSuccess() const;
  void SetUnlockData(const UnlockResult &result);
  void UpdateMessage(const std::string &message);
  // Italock: a pessoa esta digitando a senha no campo do Italock (campo nao vazio): tecla nao chama o celular.
  bool IsPasswordTyped() const {
    return _passwordTyped;
  }

public:
  virtual ~CUnlockCredential();
  volatile long _cRef;
  CREDENTIAL_PROVIDER_USAGE_SCENARIO _cpus;                                           // The usage scenario for which we were enumerated.
  CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR _rgCredProvFieldDescriptors[SFI_NUM_FIELDS]{}; // An array holding the type and name of each field in the tile.
  FIELD_STATE_PAIR _rgFieldStatePairs[SFI_NUM_FIELDS]{};                              // An array holding the state of each field in the tile.
  PWSTR _rgFieldStrings[SFI_NUM_FIELDS]{}; // An array holding the string value of each field. This is different from the name of the field held in
                                           // _rgCredProvFieldDescriptors.
  PWSTR _pszUserSid;
  PWSTR _pszQualifiedUserName;                                      // The user name that's used to pack the authentication buffer
  ICredentialProviderCredentialEvents2 *_pCredProvCredentialEvents; // Used to update fields.
                                                                    // CredentialEvents2 for Begin and EndFieldUpdates.
  BOOL _fChecked;                                                   // Tracks the state of our checkbox.
  DWORD _dwComboIndex;                                              // Tracks the current index of our combobox.
  bool _fShowControls;                                              // Tracks the state of our show/hide controls link.
  bool _fIsLocalUser;                                               // If the cred prov is assosiating with a local user tile
  // Italock: referencia FRACA ao provider (ver ProviderLink em CSampleProvider.h). Guardar AddRef pela vida toda da
  // credencial criaria o ciclo provider -> credencial -> provider; o ponteiro cru (como era) podia ser usado pela thread
  // do listener depois de o provider morrer.
  std::shared_ptr<ProviderLink> _providerLink{};
  CUnlockListener *_pUnlockListener{};
  UnlockResult _unlockResult{};
  bool _isSelected{};
  std::atomic<bool> _passwordTyped{};
  mutable std::mutex _mutex{};
};
