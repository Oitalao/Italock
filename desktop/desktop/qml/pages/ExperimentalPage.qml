import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

import PCBioUnlock
import 'qrc:/ui/base'

// Recursos experimentais: todos desligados por padrão; desligado = não roda nada.
Item {
    id: page
    property var features: []
    function load() { features = SettingsForm.GetExperimental(); }
    Component.onCompleted: load()

    readonly property var languages: [
        { text: "Automático (idioma do sistema)", value: "auto" },
        { text: "English", value: "en" },
        { text: "Deutsch", value: "de" },
        { text: "Português (Portugal)", value: "pt_PT" },
        { text: "Português (Brasil)", value: "pt_BR" },
        { text: "中文（简体）", value: "zh_CN" }
    ]
    function languageIndex() {
        let v = SettingsForm.GetLanguage();
        for (let i = 0; i < languages.length; i++) if (languages[i].value === v) return i;
        return 0;
    }

    // Linux: minutos sem o celular por perto até travar (Windows usa o tempo fixo do Bloqueio dinâmico).
    readonly property var proximityOptions: [
        { text: "Depois de 1 minuto", value: 1 },
        { text: "Depois de 2 minutos (padrão)", value: 2 },
        { text: "Depois de 5 minutos", value: 5 },
        { text: "Depois de 10 minutos", value: 10 }
    ]
    function proximityIndex() {
        let v = SettingsForm.GetOptions().proximityMinutes;
        for (let i = 0; i < proximityOptions.length; i++) if (proximityOptions[i].value === v) return i;
        return 1;
    }

    function setFeature(f, on) {
        let apply = function() {
            let err = SettingsForm.SetExperimental(f.id, on);
            if (err !== "") window.showErrorMessage(err);
            page.load();
        };
        if (on && f.needsConfirm) {
            window.ask("Ligar “" + f.name + "”?",
                       f.risk + "\n\nA senha continua valendo sempre: se o celular falhar ou não responder, o sistema pede a senha.",
                       "Ligar mesmo assim", true, apply);
            page.load(); // o interruptor só fica ligado depois de confirmar
        } else {
            apply();
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.gutter
        anchors.topMargin: 16
        spacing: 12

        PageHeader {
            Layout.maximumWidth: 680
            Layout.alignment: Qt.AlignHCenter
            title: "Recursos experimentais"
            onBack: stack.pop()
        }

        // Faixa âmbar fixa (não rola com a lista)
        Rectangle {
            Layout.fillWidth: true
            Layout.maximumWidth: 680
            Layout.alignment: Qt.AlignHCenter
            implicitHeight: bandRow.implicitHeight + 24
            radius: 10
            color: Theme.amberBg
            border.width: 1
            border.color: Theme.amberBorder
            RowLayout {
                id: bandRow
                anchors.fill: parent
                anchors.margins: 12
                spacing: 10
                Icon { name: "warning"; color: Theme.amberText; size: 20; Layout.alignment: Qt.AlignTop }
                Text {
                    Layout.fillWidth: true
                    text: "Experimental. Pode não funcionar direito, mudar ou sumir em versões futuras. Use por sua conta."
                    color: Theme.amberText
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.bodySize
                    font.weight: Font.Medium
                    wrapMode: Text.WordWrap
                }
            }
        }

        ScrollView {
            id: scroll
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            clip: true
            ColumnLayout {
                width: Math.min(scroll.availableWidth, 680)
                x: (scroll.availableWidth - width) / 2
                spacing: 0
                Repeater {
                    model: page.features
                    delegate: ColumnLayout {
                        id: feat
                        required property var modelData
                        required property int index
                        Layout.fillWidth: true
                        spacing: 0
                        SettingRow {
                            title: feat.modelData.name
                            subtitle: feat.modelData.available ? feat.modelData.desc : feat.modelData.desc + " (não disponível neste computador)"
                            risk: feat.modelData.risk
                            showDivider: !(feat.modelData.enabled && (feat.modelData.id === "language" || (feat.modelData.id === "proximity" && !MainWindow.IsWindows()))) && feat.index < page.features.length - 1
                            Toggle {
                                Accessible.name: feat.modelData.name
                                enabled: feat.modelData.available || feat.modelData.enabled
                                checked: feat.modelData.enabled
                                onToggled: page.setFeature(feat.modelData, checked)
                            }
                        }
                        Choice {
                            visible: feat.modelData.id === "proximity" && feat.modelData.enabled && !MainWindow.IsWindows()
                            Layout.fillWidth: true
                            Layout.bottomMargin: 14
                            model: page.proximityOptions
                            textRole: "text"
                            currentIndex: page.proximityIndex()
                            Accessible.name: "Travar depois de quanto tempo"
                            onActivated: function(i) {
                                let err = SettingsForm.SetOption("proximityMinutes", page.proximityOptions[i].value);
                                if (err !== "") window.showErrorMessage(err);
                            }
                        }
                        Choice {
                            visible: feat.modelData.id === "language" && feat.modelData.enabled
                            Layout.fillWidth: true
                            Layout.bottomMargin: 14
                            model: page.languages
                            textRole: "text"
                            currentIndex: page.languageIndex()
                            Accessible.name: "Idioma das mensagens"
                            onActivated: function(i) { SettingsForm.SetLanguage(page.languages[i].value); }
                        }
                    }
                }
                Text {
                    Layout.fillWidth: true
                    Layout.topMargin: 16
                    visible: !MainWindow.IsWindows()
                    text: "Os recursos que mexem no login do sistema guardam uma cópia do arquivo original em /root antes da primeira alteração."
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }
            }
        }
    }
}
