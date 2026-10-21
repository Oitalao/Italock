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
    title: "Registros · Ítalock"
    color: Theme.bg
    font.family: Theme.fontFamily

    property string appLog: "Carregando…"
    property string moduleLog: "Carregando…"
    property string agentLog: "Carregando…"
    property int tab: 0

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 10

        RowLayout {
            spacing: 4
            Repeater {
                model: [MainWindow.IsWindows() ? "Tela de bloqueio" : "Proteção de tela (PAM)", "Aplicativo", "Agente (travar pelo celular)"]
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
                text: "Atualizar"
                onClicked: { logsWindow.appLog = "Carregando…"; logsWindow.moduleLog = "Carregando…"; logsWindow.agentLog = "Carregando…"; LogsWindow.LoadLogs(logsWindow); }
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
        appLog = desktopLogs !== "" ? desktopLogs : "Nada registrado ainda.";
        moduleLog = moduleLogs !== "" ? moduleLogs : "Nada registrado ainda.";
        agentLog = agentLogs !== "" ? agentLogs : "Nada registrado ainda.";
    }
    // Fechou: a janela some de verdade (antes ficava na memória até sair do app).
    onClosing: destroy()
}
