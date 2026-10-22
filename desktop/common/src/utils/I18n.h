#ifndef I18N_H
#define I18N_H

#include <spdlog/spdlog.h>
#include <string>
#include <vector>

// Italock: idiomas. O portugues do Brasil e a lingua de origem e o padrao para todo mundo (independente do idioma do
// sistema). A escolha fica em AppSettings ("idioma" no app_settings.json): "pt_BR" (padrao), "auto" (idioma do
// sistema) ou um dos codigos de Languages(). A tela de bloqueio (LogonUI roda como SYSTEM) e o PAM leem a mesma escolha.
//
// Duas formas de pedir um texto, as duas com volta ao portugues do Brasil quando falta a traducao (nunca ingles, nunca
// a chave crua):
//  - Get("chave"): textos antigos com chave (res/pt_BR.json tem o original);
//  - Tr("Texto em português"): o proprio texto em portugues e a chave (as telas QML usam qsTr, que cai aqui).
// Traducoes em res/i18n/<codigo>.json (as duas formas no mesmo arquivo). Placeholders {} (fmt) e %1 (Qt) ficam iguais.
class I18n {
public:
  struct Language {
    const char *code;       // "pt_BR", "en", ...
    const char *nativeName; // nome no proprio idioma (o seletor mostra assim)
    bool rtl;
  };

  template <typename... T> static std::string Get(const std::string &key, T &&...args) {
    return Format(Get(key), Original(key), args...);
  }
  static std::string Get(const std::string &key);

  template <typename... T> static std::string Tr(const std::string &text, T &&...args) {
    return Format(Tr(text), text, args...);
  }
  static std::string Tr(const std::string &text);
  // Sem volta ao original: vazio quando o idioma atual e o portugues do Brasil ou falta a traducao (QTranslator).
  static std::string Lookup(const std::string &text);

  // Idioma em uso, ja resolvido ("auto" vira o do sistema): sempre um codigo de Languages().
  static std::string CurrentLanguage();
  static bool IsRtl();
  // Escolha gravada ("pt_BR", "auto", "en"...). Invalido ou vazio = "pt_BR".
  static std::string SavedChoice();
  static bool IsValidChoice(const std::string &choice);
  // So para conferir telas (modo de print, sem administrador): forca um idioma neste processo.
  static void SetOverride(const std::string &code);
  static const std::vector<Language> &Languages();

private:
  // Texto em portugues do Brasil de uma chave (sem traducao).
  static std::string Original(const std::string &key);
  // Traducao com placeholder quebrado nao pode derrubar a tela de bloqueio: cai no original em portugues.
  template <typename... T> static std::string Format(const std::string &translated, const std::string &original, T &&...args) {
    try {
      return fmt::format(fmt::runtime(translated), args...);
    } catch(...) {
    }
    try {
      return fmt::format(fmt::runtime(original), args...);
    } catch(...) {
    }
    return original;
  }
  I18n() = default;
};

#endif // I18N_H
