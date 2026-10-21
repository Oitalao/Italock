#include "I18n.h"

#include "storage/AppSettings.h"
#include "utils/LocaleHelper.h"

#include "generated/LANG_DE_DE.h"
#include "generated/LANG_EN_US.h"
#include "generated/LANG_PT_BR.h"
#include "generated/LANG_PT_PT.h"
#include "generated/LANG_ZH_CN.h"

std::string I18n::Get(const std::string &key) {
  // Italock: sempre em portugues do Brasil (inclusive na tela de bloqueio, onde o LogonUI roda como SYSTEM em outro
  // idioma). O idioma escolhido so vale com o recurso experimental ligado.
  const auto lang = AppSettings::Get().expLanguage ? LocaleHelper::GetUserLocale() : LocaleHelper::Locale::PORTUGUESE_BR;
  const nlohmann::json *langJson{};
  switch(lang) {
    case LocaleHelper::Locale::GERMAN:
      langJson = &LANG_DE_DE_DATA;
      break;
    case LocaleHelper::Locale::CHINESE_SIMPLIFIED:
      langJson = &LANG_ZH_CN_DATA;
      break;
    case LocaleHelper::Locale::PORTUGUESE_PT:
      langJson = &LANG_PT_PT_DATA;
      break;
    case LocaleHelper::Locale::PORTUGUESE_BR:
      langJson = &LANG_PT_BR_DATA;
      break;
    case LocaleHelper::Locale::ENGLISH:
    default:
      langJson = &LANG_EN_US_DATA;
      break;
  }
  if(langJson->count(key))
    return langJson->at(key);
  spdlog::warn("Missing I18n key '{}' for locale '{}'.", key, LocaleHelper::ToString(lang));
  if(lang != LocaleHelper::Locale::ENGLISH) {
    if(LANG_EN_US_DATA.count(key))
      return LANG_EN_US_DATA.at(key);
  }
  return key;
}
