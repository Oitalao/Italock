#ifndef LOCALEHELPER_H
#define LOCALEHELPER_H

#include <string>

// Italock: idioma do sistema, so para a opcao "Automatico (idioma do sistema)". Devolve um codigo de
// I18n::Languages(); idioma sem traducao (ou nao detectado) = "pt_BR", o padrao do Italock.
class LocaleHelper {
public:
  static std::string GetSystemLanguage();
  // "pt-BR", "en_US.UTF-8", "zh-Hans-CN"... -> codigo do Italock ("pt_BR", "en", "zh_CN"); vazio se nao houver.
  static std::string MapLocale(const std::string &locale);

private:
  LocaleHelper() = default;
};

#endif
