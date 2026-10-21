import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Window

import PCBioUnlock
import 'qrc:/ui/base'

ApplicationWindow {
    id: window
    width: 760
    height: 620
    minimumWidth: 560
    minimumHeight: 480
    visible: true
    title: "Ítalock"
    color: Theme.bg
    font.family: Theme.fontFamily
    font.pixelSize: Theme.bodySize

    property bool busy: false
    onClosing: function(close) { close.accepted = !window.busy }

    StackView {
        id: stack
        anchors.fill: parent
        initialItem: "qrc:/ui/pages/HomePage.qml"
        // Troca de página sem firula: um deslize curto.
        pushEnter: Transition { NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 120 } }
        pushExit: Transition { NumberAnimation { property: "opacity"; from: 1; to: 0; duration: 80 } }
        popEnter: Transition { NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 120 } }
        popExit: Transition { NumberAnimation { property: "opacity"; from: 1; to: 0; duration: 80 } }
    }

    // ---- Diálogos ----
    AppDialog {
        id: dialog
        property var acceptFn: null
        property var extraFn: null
        onAccepted: if (acceptFn) acceptFn()
        onExtra: if (extraFn) extraFn()
    }

    function ask(title, text, confirmText, danger, onAccept) {
        dialog.title = title;
        dialog.text = text;
        dialog.confirmText = confirmText;
        dialog.cancelText = "Cancelar";
        dialog.extraText = "";
        dialog.danger = danger;
        dialog.acceptFn = onAccept;
        dialog.extraFn = null;
        dialog.open();
    }
    function showErrorMessage(text) {
        dialog.title = "Não deu certo";
        dialog.text = text;
        dialog.confirmText = "OK";
        dialog.cancelText = "";
        dialog.extraText = "";
        dialog.danger = false;
        dialog.acceptFn = null;
        dialog.extraFn = null;
        dialog.open();
    }
    function showFatalErrorMessage(text) {
        showErrorMessage(text);
        dialog.acceptFn = function() { Qt.exit(1); };
    }
    // Senha que o sistema recusou mas pode estar certa (ex.: conta Microsoft com PIN): deixa seguir, por conta da pessoa.
    function showSkipPasswordCheck(text, onSkip) {
        dialog.title = "Senha não conferida";
        dialog.text = text + "\n\nSe tiver certeza de que a senha está certa, dá para seguir sem conferir. Com a senha errada, o celular não vai conseguir destravar.";
        dialog.confirmText = "Corrigir";
        dialog.cancelText = "";
        dialog.extraText = "Seguir sem conferir";
        dialog.danger = false;
        dialog.acceptFn = null;
        dialog.extraFn = onSkip;
        dialog.open();
    }

    function confirmRemove(id, name) {
        ask("Remover “" + name + "”?",
            "Este celular não vai mais destravar este computador. Para usar de novo, é preciso parear outra vez.",
            "Remover", true, function() {
                let err = MainWindow.RemoveDevice(id);
                if (err !== "") showErrorMessage(err);
                if (stack.currentItem && stack.currentItem.refresh) stack.currentItem.refresh();
            });
    }

    function startPairing() {
        let err = PairingForm.Begin();
        if (err !== "") { showErrorMessage(err); return; }
        stack.push("qrc:/ui/pages/PairingPage.qml");
    }

    function openUnlockTest(id) {
        let win = Qt.createComponent("qrc:/ui/UnlockTestWindow.qml").createObject(window, {deviceId: id});
        if (win) win.show();
    }
    function openLogs() {
        let win = Qt.createComponent("qrc:/ui/LogsWindow.qml").createObject(window);
        if (win) win.show();
    }

    // ---- Chamadas vindas do C++ ----
    function updateBluetoothDeviceList(devices) {
        if (stack.currentItem && stack.currentItem.updateBluetoothDeviceList) stack.currentItem.updateBluetoothDeviceList(devices);
    }
    function finishBluetoothPairing(ok) {
        if (stack.currentItem && stack.currentItem.finishBluetoothPairing) stack.currentItem.finishBluetoothPairing(ok);
    }
    function accountChecked(result) {
        if (stack.currentItem && stack.currentItem.accountChecked) stack.currentItem.accountChecked(result);
    }
    function pairingSucceeded(name) {
        if (stack.currentItem && stack.currentItem.pairingSucceeded) stack.currentItem.pairingSucceeded(name);
    }

    // ---- Aplicando mudança no sistema (ligar/desligar a proteção) ----
    function showLoadingScreen(text) {
        window.busy = true;
        working.title = text;
        working.log = "";
        working.done = false;
        working.ok = true;
        working.showLog = false;
        working.open();
    }
    function appendLoadingOutput(text) {
        working.log += (working.log === "" ? "" : "\n") + text;
    }
    function finishLoadingScreen(ok, text) {
        window.busy = false;
        working.done = true;
        working.ok = ok;
        working.title = text;
        if (ok) closeTimer.start();
        else working.showLog = true;
        if (stack.currentItem && stack.currentItem.refresh) stack.currentItem.refresh();
    }
    Timer {
        id: closeTimer
        interval: 900
        onTriggered: working.close()
    }

    Popup {
        id: working
        property string title: ""
        property string log: ""
        property bool done: false
        property bool ok: true
        property bool showLog: false
        parent: Overlay.overlay
        anchors.centerIn: parent
        width: Math.min(480, window.width - 48)
        modal: true
        closePolicy: Popup.NoAutoClose
        padding: 24
        Overlay.modal: Rectangle { color: Theme.dark ? "#99000000" : "#66111827" }
        background: Rectangle {
            radius: Theme.radius
            color: Theme.surface
            border.width: Theme.dark ? 1 : 0
            border.color: Theme.divider
        }
        contentItem: ColumnLayout {
            spacing: 14
            RowLayout {
                spacing: 12
                Busy { running: !working.done; size: 22 }
                Icon {
                    visible: working.done
                    name: working.ok ? "check" : "warning"
                    color: working.ok ? Theme.brandOn : Theme.danger
                    size: 22
                }
                Text {
                    Layout.fillWidth: true
                    text: working.title
                    color: working.done && !working.ok ? Theme.danger : Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.bodySize
                    font.weight: Font.DemiBold
                    wrapMode: Text.WordWrap
                }
            }
            Btn {
                visible: working.log !== "" && !working.showLog
                kind: "text"
                text: "Ver detalhes"
                onClicked: working.showLog = true
            }
            ScrollView {
                visible: working.showLog && working.log !== ""
                Layout.fillWidth: true
                Layout.preferredHeight: 160
                TextArea {
                    readOnly: true
                    text: working.log
                    wrapMode: Text.WrapAnywhere
                    color: Theme.text2
                    font.family: Theme.monoFamily
                    font.pixelSize: Theme.smallSize
                    background: Rectangle { color: Theme.bg; radius: 8 }
                }
            }
            Btn {
                visible: working.done && !working.ok
                Layout.alignment: Qt.AlignRight
                kind: "secondary"
                text: "Fechar"
                onClicked: working.close()
            }
        }
    }

    Component.onCompleted: {
        if (!MainWindow.PerformStartupChecks(window)) return;
        let dev = MainWindow.DevPage(); // só para conferir telas: "configuracoes", "experimentais", "sobre", "historico", "parear[:metodo]"
        let pages = { configuracoes: "SettingsPage", experimentais: "ExperimentalPage", sobre: "AboutPage", historico: "HistoryPage" };
        if (pages[dev]) stack.push("qrc:/ui/pages/" + pages[dev] + ".qml", {}, StackView.Immediate);
        else if (dev.startsWith("parear")) {
            stack.push("qrc:/ui/pages/PairingPage.qml", {}, StackView.Immediate);
            if (dev === "parear:metodo") stack.currentItem.step = "method";
        }
    }
}
