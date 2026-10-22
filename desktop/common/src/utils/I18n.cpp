#include "I18n.h"

#include <map>
#include <memory>
#include <mutex>
#include <nlohmann/json.hpp>
#include <string_view>

#include "storage/AppSettings.h"
#include "utils/LocaleHelper.h"

#include "generated/I18N_AR.h"
#include "generated/I18N_DE.h"
#include "generated/I18N_EN.h"
#include "generated/I18N_ES.h"
#include "generated/I18N_FR.h"
#include "generated/I18N_HI.h"
#include "generated/I18N_IT.h"
#include "generated/I18N_JA.h"
#include "generated/I18N_KO.h"
#include "generated/I18N_PL.h"
#include "generated/I18N_PT_BR.h"
#include "generated/I18N_RU.h"
#include "generated/I18N_ZH_CN.h"

namespace {
std::mutex g_Mutex{};
std::string g_Override{};
std::map<std::string, std::unique_ptr<const nlohmann::json>> g_Parsed{};

#define ITALOCK_LANG_DATA(NAME) std::string_view(reinterpret_cast<const char *>(NAME##_BYTES), NAME##_SIZE)

std::string_view RawData(const std::string &code) {
  if(code == "pt_BR")
    return ITALOCK_LANG_DATA(I18N_PT_BR);
  if(code == "en")
    return ITALOCK_LANG_DATA(I18N_EN);
  if(code == "es")
    return ITALOCK_LANG_DATA(I18N_ES);
  if(code == "fr")
    return ITALOCK_LANG_DATA(I18N_FR);
  if(code == "de")
    return ITALOCK_LANG_DATA(I18N_DE);
  if(code == "it")
    return ITALOCK_LANG_DATA(I18N_IT);
  if(code == "ja")
    return ITALOCK_LANG_DATA(I18N_JA);
  if(code == "ko")
    return ITALOCK_LANG_DATA(I18N_KO);
  if(code == "zh_CN")
    return ITALOCK_LANG_DATA(I18N_ZH_CN);
  if(code == "ru")
    return ITALOCK_LANG_DATA(I18N_RU);
  if(code == "pl")
    return ITALOCK_LANG_DATA(I18N_PL);
  if(code == "hi")
    return ITALOCK_LANG_DATA(I18N_HI);
  if(code == "ar")
    return ITALOCK_LANG_DATA(I18N_AR);
  return {};
}

// Lido na primeira vez que o idioma e usado; erro de leitura = objeto vazio (cai no portugues), nunca excecao.
const nlohmann::json &Data(const std::string &code) {
  std::lock_guard lock(g_Mutex);
  auto it = g_Parsed.find(code);
  if(it != g_Parsed.end())
    return *it->second;
  auto raw = RawData(code);
  auto parsed = nlohmann::json::parse(raw.begin(), raw.end(), nullptr, false);
  if(parsed.is_discarded() || !parsed.is_object()) {
    spdlog::error("I18n: dados do idioma '{}' ilegiveis.", code);
    parsed = nlohmann::json::object();
  }
  auto &slot = g_Parsed[code];
  slot = std::make_unique<const nlohmann::json>(std::move(parsed));
  return *slot;
}

std::string Find(const nlohmann::json &data, const std::string &key) {
  auto it = data.find(key);
  if(it == data.end() || !it->is_string())
    return {};
  return it->get<std::string>();
}

const std::string &SystemLanguage() {
  static const std::string lang = LocaleHelper::GetSystemLanguage();
  return lang;
}
} // namespace

const std::vector<I18n::Language> &I18n::Languages() {
  static const std::vector<Language> list = {
      {"pt_BR", "Português (Brasil)", false},
      {"en", "English", false},
      {"es", "Español", false},
      {"fr", "Français", false},
      {"de", "Deutsch", false},
      {"it", "Italiano", false},
      {"ja", "日本語", false},
      {"ko", "한국어", false},
      {"zh_CN", "简体中文", false},
      {"ru", "Русский", false},
      {"pl", "Polski", false},
      {"hi", "हिन्दी", false},
      {"ar", "العربية", true},
  };
  return list;
}

bool I18n::IsValidChoice(const std::string &choice) {
  if(choice == "auto")
    return true;
  for(const auto &l : Languages())
    if(choice == l.code)
      return true;
  return false;
}

std::string I18n::SavedChoice() {
  auto choice = AppSettings::Get().language;
  return IsValidChoice(choice) ? choice : std::string("pt_BR");
}

void I18n::SetOverride(const std::string &code) {
  std::lock_guard lock(g_Mutex);
  g_Override = code == "auto" || IsValidChoice(code) ? code : std::string();
}

std::string I18n::CurrentLanguage() {
  std::string choice{};
  {
    std::lock_guard lock(g_Mutex);
    choice = g_Override;
  }
  if(choice.empty())
    choice = SavedChoice();
  if(choice == "auto")
    return SystemLanguage();
  return choice;
}

bool I18n::IsRtl() {
  auto lang = CurrentLanguage();
  for(const auto &l : Languages())
    if(lang == l.code)
      return l.rtl;
  return false;
}

std::string I18n::Get(const std::string &key) {
  const auto lang = CurrentLanguage();
  if(lang != "pt_BR") {
    auto value = Find(Data(lang), key);
    if(!value.empty())
      return value;
  }
  auto value = Find(Data("pt_BR"), key);
  if(!value.empty())
    return value;
  spdlog::warn("I18n: chave '{}' sem texto.", key);
  return key;
}

std::string I18n::Original(const std::string &key) {
  auto value = Find(Data("pt_BR"), key);
  return value.empty() ? key : value;
}

std::string I18n::Lookup(const std::string &text) {
  const auto lang = CurrentLanguage();
  if(lang == "pt_BR" || text.empty())
    return {};
  return Find(Data(lang), text);
}

std::string I18n::Tr(const std::string &text) {
  auto value = Lookup(text);
  return value.empty() ? text : value;
}
