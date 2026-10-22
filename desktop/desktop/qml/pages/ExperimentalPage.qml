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

    // Linux: minutos sem o celular por perto até travar (Windows usa o tempo fixo do Bloqueio dinâmico).
    readonly property var proximityOptions: [
        { text: qsTr("Depois de 1 minuto"), value: 1 },
        { text: qsTr("Depois de 2 minutos (padrão)"), value: 2 },
        { text: qsTr("Depois de 5 minutos"), value: 5 },
        { text: qsTr("Depois de 10 minutos"), value: 10 }
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
            window.ask(qsTr("Ligar “%1”?").arg(f.name),
                       f.risk + "\n\n" + qsTr("A senha continua valendo sempre: se o celular falhar ou não responder, o sistema pede a senha."),
                       qsTr("Ligar mesmo assim"), true, apply);
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
            title: qsTr("Recursos experimentais")
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
                    text: qsTr("Experimental. Pode não funcionar direito, mudar ou sumir em versões futuras. Use por sua conta.")
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
                            subtitle: feat.modelData.available ? feat.modelData.desc : feat.modelData.desc + " " + qsTr("(não disponível neste computador)")
                            risk: feat.modelData.risk
                            showDivider: !(feat.modelData.enabled && feat.modelData.id === "proximity" && !MainWindow.IsWindows()) && feat.index < page.features.length - 1
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
                            Accessible.name: qsTr("Travar depois de quanto tempo")
                            onActivated: function(i) {
                                let err = SettingsForm.SetOption("proximityMinutes", page.proximityOptions[i].value);
                                if (err !== "") window.showErrorMessage(err);
                            }
                        }
                    }
                }
                Text {
                    Layout.fillWidth: true
                    Layout.topMargin: 16
                    visible: !MainWindow.IsWindows()
                    text: qsTr("Os recursos que mexem no login do sistema guardam uma cópia do arquivo original em /root antes da primeira alteração.")
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }
            }
        }
    }
}
