import QtQuick
import QtQuick.Controls.Basic

// Campo de texto. mono: números, portas e códigos em fonte monoespaçada.
TextField {
    id: control
    property bool mono: false
    property bool invalid: false

    implicitHeight: 40
    leftPadding: 12
    rightPadding: 12
    font.family: mono ? Theme.monoFamily : Theme.fontFamily
    font.pixelSize: Theme.bodySize
    color: Theme.text
    placeholderTextColor: Theme.text2
    selectionColor: Theme.brandOn
    selectedTextColor: "#FFFFFF"
    selectByMouse: true
    verticalAlignment: TextInput.AlignVCenter

    background: Rectangle {
        radius: 8
        color: control.enabled ? Theme.field : Theme.bg
        border.width: control.activeFocus ? 2 : 1
        border.color: control.invalid ? Theme.danger : (control.activeFocus ? Theme.brandOn : Theme.fieldBorder)
    }
}
