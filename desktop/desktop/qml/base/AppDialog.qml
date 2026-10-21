import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

// Diálogo modal: título, texto e até três botões (confirmar, cancelar e um extra discreto à esquerda).
Popup {
    id: root
    property string title: ""
    property string text: ""
    property string confirmText: "OK"
    property string cancelText: ""
    property string extraText: ""
    property bool danger: false
    signal accepted()
    signal rejected()
    signal extra()

    parent: Overlay.overlay
    anchors.centerIn: parent
    width: Math.min(460, parent ? parent.width - 48 : 460)
    modal: true
    focus: true
    padding: 24
    closePolicy: cancelText !== "" ? Popup.CloseOnEscape : Popup.NoAutoClose
    onClosed: if (!_done) rejected()
    onOpened: { _done = false; confirmBtn.forceActiveFocus(); }
    property bool _done: false

    Overlay.modal: Rectangle { color: Theme.dark ? "#99000000" : "#66111827" }

    background: Rectangle {
        radius: Theme.radius
        color: Theme.surface
        border.width: Theme.dark ? 1 : 0
        border.color: Theme.divider
    }

    contentItem: ColumnLayout {
        spacing: 12
        Text {
            Layout.fillWidth: true
            text: root.title
            visible: text !== ""
            color: root.danger ? Theme.danger : Theme.text
            font.family: Theme.fontFamily
            font.pixelSize: 17
            font.weight: Font.DemiBold
            wrapMode: Text.WordWrap
        }
        Text {
            Layout.fillWidth: true
            text: root.text
            visible: text !== ""
            color: Theme.text
            font.family: Theme.fontFamily
            font.pixelSize: Theme.bodySize
            wrapMode: Text.WordWrap
            lineHeight: 1.2
            textFormat: Text.PlainText
        }
        RowLayout {
            Layout.fillWidth: true
            Layout.topMargin: 8
            spacing: 8
            Btn {
                visible: root.extraText !== ""
                kind: "text"
                text: root.extraText
                onClicked: { root._done = true; root.close(); root.extra(); }
            }
            Item { Layout.fillWidth: true }
            Btn {
                visible: root.cancelText !== ""
                kind: "secondary"
                text: root.cancelText
                onClicked: { root._done = true; root.close(); root.rejected(); }
            }
            Btn {
                id: confirmBtn
                kind: root.danger ? "danger" : "primary"
                text: root.confirmText
                onClicked: { root._done = true; root.close(); root.accepted(); }
            }
        }
    }
}
