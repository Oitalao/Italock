import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Window

import PCBioUnlock
import 'qrc:/ui/base'

// Registros do app e da tela de bloqueio, lado a lado por abas.
ApplicationWindow {
    id: logsWindow
    width: 820
    height: 600
    minimumWidth: 480
    minimumHeight: 360
    title: qsTr("Registros · Ítalock")
    color: Theme.bg
    font.family: Theme.fontFamily

    // Arabe: telas espelhadas (da direita para a esquerda)
    LayoutMirroring.enabled: QI18n.rtl
    LayoutMirroring.childrenInherit: true

    property string appLog: qsTr("Carregando…")
    property string moduleLog: qsTr("Carregando…")
    property string agentLog: qsTr("Carregando…")
    property int tab: 0

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 10

        RowLayout {
            spacing: 4
            Repeater {
                model: [MainWindow.IsWindows() ? qsTr("Tela de bloqueio") : qsTr("Proteção de tela (PAM)"), qsTr("Aplicativo"), qsTr("Agente (travar pelo celular)")]
                delegate: Btn {
                    required property string modelData
                    required property int index
                    kind: logsWindow.tab === index ? "secondary" : "text"
                    text: modelData
                    onClicked: logsWindow.tab = index
                }
            }
            Item { Layout.fillWidth: true }
            Btn {
                kind: "text"
                text: qsTr("Atualizar")
                onClicked: { logsWindow.appLog = qsTr("Carregando…"); logsWindow.moduleLog = qsTr("Carregando…"); logsWindow.agentLog = qsTr("Carregando…"); LogsWindow.LoadLogs(logsWindow); }
            }
        }

        ScrollView {
            id: scroll
            Layout.fillWidth: true
            Layout.fillHeight: true
            TextArea {
                id: area
                readOnly: true
                wrapMode: Text.WrapAnywhere
                text: logsWindow.tab === 0 ? logsWindow.moduleLog : (logsWindow.tab === 1 ? logsWindow.appLog : logsWindow.agentLog)
                color: Theme.text
                selectionColor: Theme.brandOn
                selectedTextColor: "#FFFFFF"
                selectByMouse: true
                font.family: Theme.monoFamily
                font.pixelSize: Theme.smallSize
                padding: 12
                background: Rectangle { color: Theme.surface; radius: 10; border.width: 1; border.color: Theme.divider }
                onTextChanged: cursorPosition = length
            }
        }
    }

    Component.onCompleted: LogsWindow.LoadLogs(logsWindow)

    function setLogs(desktopLogs, moduleLogs, agentLogs) {
        appLog = desktopLogs !== "" ? desktopLogs : qsTr("Nada registrado ainda.");
        moduleLog = moduleLogs !== "" ? moduleLogs : qsTr("Nada registrado ainda.");
        agentLog = agentLogs !== "" ? agentLogs : qsTr("Nada registrado ainda.");
    }
    // Fechou: a janela some de verdade (antes ficava na memória até sair do app).
    onClosing: destroy()
}
