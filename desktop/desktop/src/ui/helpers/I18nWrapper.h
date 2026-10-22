#ifndef PCBU_DESKTOP_I18NWRAPPER_H
#define PCBU_DESKTOP_I18NWRAPPER_H

#include <QObject>
#include <QQmlEngine>
#include <QTranslator>
#include <QtQmlIntegration>

// Italock: idioma nas telas. As telas QML usam qsTr("texto em português"); o ItalockTranslator responde com a
// traducao do idioma escolhido (I18n::Lookup) e, sem traducao, o Qt mostra o proprio texto em portugues.
class ItalockTranslator : public QTranslator {
public:
  using QTranslator::QTranslator;
  QString translate(const char *context, const char *sourceText, const char *disambiguation, int n) const override;
  bool isEmpty() const override { return false; }
};

class QI18n : public QObject {
  Q_OBJECT
  QML_ELEMENT
  QML_SINGLETON
  // Idioma em uso ("pt_BR", "en", ...) e se escreve da direita para a esquerda (arabe: telas espelhadas).
  Q_PROPERTY(QString language READ GetLanguage NOTIFY languageChanged)
  Q_PROPERTY(bool rtl READ IsRtl NOTIFY languageChanged)
public:
  explicit QI18n(QObject *parent = nullptr);

  Q_INVOKABLE QString Get(const QString &key, const QVariantList &args = QVariantList());
  QString GetLanguage();
  bool IsRtl();

  // Opcoes do seletor: [{value, text}] (Portugues do Brasil, Automatico, depois cada idioma no proprio nome).
  Q_INVOKABLE QVariantList GetLanguageOptions();
  // Escolha gravada ("pt_BR", "auto", "en"...).
  Q_INVOKABLE QString GetChoice();
  // Grava a escolha (vale para as telas, a tela de bloqueio e o PAM) e traduz as telas abertas. "" ou o erro.
  Q_INVOKABLE QString SetChoice(const QString &choice);

  // Instala o tradutor no app (uma vez, antes de carregar o QML).
  static void InstallTranslator();
  // O idioma mudou (aqui ou em outro processo): avisa as telas e traduz de novo.
  static void NotifyLanguageChanged();

signals:
  void languageChanged();
};

#endif // PCBU_DESKTOP_I18NWRAPPER_H
