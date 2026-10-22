import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Window

import PCBioUnlock
import 'qrc:/ui/base'

// Testar destravar: pede a confirmação ao celular como a tela de bloqueio faria, sem travar nada.
ApplicationWindow {
    id: unlockTestWindow
    property string deviceId: ""
    property bool running: true

    width: 460
    height: 260
    minimumWidth: width
    minimumHeight: height
    maximumWidth: width
    maximumHeight: height
    title: qsTr("Testar destravar · Ítalock")
    flags: Qt.Dialog
    modality: Qt.ApplicationModal
    color: Theme.bg
    font.family: Theme.fontFamily

    // Arabe: telas espelhadas (da direita para a esquerda)
    LayoutMirroring.enabled: QI18n.rtl
    LayoutMirroring.childrenInherit: true

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        Text {
            Layout.fillWidth: true
            text: qsTr("Testar destravar")
            color: Theme.text
            font.family: Theme.fontFamily
            font.pixelSize: 17
            font.weight: Font.DemiBold
        }
        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 12
            Busy { running: unlockTestWindow.running; size: 22; Layout.alignment: Qt.AlignVCenter }
            Text {
                id: lblUnlockMsg
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignVCenter
                text: qsTr("Preparando…")
                color: Theme.text
                font.family: Theme.fontFamily
                font.pixelSize: Theme.bodySize
                wrapMode: Text.WordWrap
            }
        }
        Text {
            Layout.fillWidth: true
            text: qsTr("Nada é destravado de verdade: é só para ver se o celular responde.")
            color: Theme.text2
            font.family: Theme.fontFamily
            font.pixelSize: Theme.smallSize
            wrapMode: Text.WordWrap
        }
        RowLayout {
            Item { Layout.fillWidth: true }
            Btn {
                id: btnUnlockAction
                kind: "secondary"
                text: qsTr("Cancelar")
                onClicked: {
                    if (UnlockTestWindow.IsRunning()) UnlockTestWindow.StopUnlock(unlockTestWindow);
                    else UnlockTestWindow.StartUnlock(unlockTestWindow, deviceId);
                }
            }
        }
    }

    Component.onCompleted: UnlockTestWindow.StartUnlock(unlockTestWindow, deviceId)
    onClosing: { UnlockTestWindow.StopUnlock(unlockTestWindow); destroy(); }

    function setUnlockMessage(text) {
        lblUnlockMsg.text = text;
    }
    function setUnlockButtonText(text) {
        // O C++ manda "Cancelar" ao começar e "Tentar de novo" ao terminar.
        btnUnlockAction.text = text;
        unlockTestWindow.running = text === QI18n.Get("cancel");
    }
}
