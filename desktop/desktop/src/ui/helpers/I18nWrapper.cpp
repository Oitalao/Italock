#include "I18nWrapper.h"

#include <QCoreApplication>
#include <QList>
#include <QPointer>
#include <QVariantMap>

#include "storage/AppSettings.h"
#include "ui/MainWindow.h"
#include "utils/I18n.h"

namespace {
QList<QPointer<QI18n>> g_Instances{};
} // namespace

QString ItalockTranslator::translate(const char *, const char *sourceText, const char *, int) const {
  if(!sourceText || !*sourceText)
    return {};
  auto value = I18n::Lookup(sourceText);
  return value.empty() ? QString() : QString::fromStdString(value);
}

// Uma instancia por QQmlEngine (o QML cria o singleton); guardadas para avisar todas quando o idioma muda.
QI18n::QI18n(QObject *parent) : QObject(parent) {
  g_Instances.removeAll(nullptr);
  g_Instances.append(this);
}

QString QI18n::Get(const QString &key, const QVariantList &) {
  return QString::fromStdString(I18n::Get(key.toStdString()));
}

QString QI18n::GetLanguage() {
  return QString::fromStdString(I18n::CurrentLanguage());
}

bool QI18n::IsRtl() {
  return I18n::IsRtl();
}

QVariantList QI18n::GetLanguageOptions() {
  QVariantList result{};
  auto add = [&result](const QString &value, const QString &text) {
    QVariantMap entry{};
    entry["value"] = value;
    entry["text"] = text;
    result.append(entry);
  };
  const auto &langs = I18n::Languages();
  add(langs.front().code, QString::fromUtf8(langs.front().nativeName));
  add("auto", QString::fromStdString(I18n::Tr("Automático (idioma do sistema)")));
  for(size_t i = 1; i < langs.size(); i++)
    add(langs[i].code, QString::fromUtf8(langs[i].nativeName));
  return result;
}

QString QI18n::GetChoice() {
  AppSettings::InvalidateCache();
  return QString::fromStdString(I18n::SavedChoice());
}

QString QI18n::SetChoice(const QString &choice) {
  auto value = choice.toStdString();
  if(!I18n::IsValidChoice(value))
    return QString::fromStdString(I18n::Tr("Opção inválida."));
  AppSettings::InvalidateCache();
  auto settings = AppSettings::Get();
  if(settings.language != value) {
    settings.language = value;
    AppSettings::Save(settings);
    AppSettings::InvalidateCache();
    if(AppSettings::Get().language != value)
      return QString::fromStdString(I18n::Tr("Não deu para gravar as configurações."));
  }
  NotifyLanguageChanged();
  MainWindow::NotifyPrefChanged("idioma"); // a bandeja (outro processo) traduz o menu e os avisos
  return {};
}

void QI18n::InstallTranslator() {
  static ItalockTranslator *translator = nullptr;
  if(translator)
    return;
  translator = new ItalockTranslator(QCoreApplication::instance());
  QCoreApplication::installTranslator(translator);
}

void QI18n::NotifyLanguageChanged() {
  AppSettings::InvalidateCache();
  g_Instances.removeAll(nullptr);
  for(const auto &instance : g_Instances) {
    if(!instance)
      continue;
    emit instance->languageChanged();
    auto engine = qmlEngine(instance);
    if(!engine)
      engine = qobject_cast<QQmlEngine *>(qjsEngine(instance));
    if(engine)
      engine->retranslate(); // textos com qsTr nas telas abertas
  }
}
