import QtQuick
import QtQuick.Controls.Basic

// Interruptor liga/desliga.
Switch {
    id: control
    implicitWidth: 48
    implicitHeight: 28
    padding: 0
    spacing: 0
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus

    indicator: Rectangle {
        width: 44
        height: 24
        x: control.width - width
        anchors.verticalCenter: parent.verticalCenter
        radius: 12
        color: control.checked ? (control.enabled ? Theme.brandOn : Theme.pressed) : (control.hovered ? Theme.pressed : Theme.divider)
        border.width: control.checked ? 0 : 1
        border.color: Theme.fieldBorder

        Rectangle {
            width: 18
            height: 18
            radius: 9
            anchors.verticalCenter: parent.verticalCenter
            x: control.checked ? parent.width - width - 3 : 3
            color: control.checked ? "#FFFFFF" : (Theme.dark ? "#C9CED8" : "#5B6475")
        }
        Rectangle {
            anchors.fill: parent
            anchors.margins: -3
            radius: 15
            color: "transparent"
            border.width: 2
            border.color: Theme.brandOn
            visible: control.visualFocus
        }
    }
    contentItem: Item {}
}
