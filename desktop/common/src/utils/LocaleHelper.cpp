#include "LocaleHelper.h"

#include <cstdlib>
#include <filesystem>
#include <fstream>

#include "utils/StringUtils.h"

#ifdef WINDOWS
#include <Windows.h>
#endif

std::string LocaleHelper::MapLocale(const std::string &locale) {
  auto l = StringUtils::Replace(StringUtils::ToLower(locale), "-", "_");
  if(l.size() >= 2 && l[0] == '"')
    l = l.substr(1);
  auto starts = [&l](const char *prefix) {
    auto n = std::char_traits<char>::length(prefix);
    return l.compare(0, n, prefix) == 0 && (l.size() == n || l[n] == '_' || l[n] == '.' || l[n] == '@');
  };
  if(starts("pt"))
    return "pt_BR"; // portugues de Portugal tambem fica no do Brasil (o Italock nao tem o de Portugal)
  for(const char *code : {"en", "es", "fr", "de", "it", "ja", "ko", "ru", "pl", "hi", "ar"})
    if(starts(code))
      return code;
  if(starts("zh"))
    return "zh_CN";
  return {};
}

std::string LocaleHelper::GetSystemLanguage() {
#ifdef WINDOWS
  // Idioma da interface do Windows (nao o formato regional). Na tela de bloqueio (SYSTEM) e o idioma do sistema.
  WCHAR name[LOCALE_NAME_MAX_LENGTH]{};
  if(LCIDToLocaleName(MAKELCID(GetUserDefaultUILanguage(), SORT_DEFAULT), name, LOCALE_NAME_MAX_LENGTH, 0) > 0) {
    auto code = MapLocale(StringUtils::FromWideString(name));
    if(!code.empty())
      return code;
  }
  return "pt_BR";
#else
  for(const char *var : {"LC_ALL", "LC_MESSAGES", "LANG"}) {
    auto value = std::getenv(var);
    if(!value || !*value)
      continue;
    std::string v = value;
    if(v == "C" || v == "POSIX" || v.starts_with("C."))
      continue;
    auto code = MapLocale(v);
    return code.empty() ? std::string("pt_BR") : code;
  }
  // Sem variavel (PAM, servico): o idioma do sistema nos arquivos de configuracao.
  for(const char *file : {"/etc/locale.conf", "/etc/default/locale"}) {
    try {
      if(!std::filesystem::exists(file))
        continue;
      std::ifstream in(file);
      std::string line{};
      while(std::getline(in, line)) {
        if(line.starts_with("LANG=")) {
          auto code = MapLocale(line.substr(5));
          return code.empty() ? std::string("pt_BR") : code;
        }
      }
    } catch(...) {
    }
  }
  return "pt_BR";
#endif
}
