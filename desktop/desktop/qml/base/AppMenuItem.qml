import QtQuick
import QtQuick.Controls.Basic

MenuItem {
    id: control
    property bool danger: false
    implicitHeight: 38
    hoverEnabled: true
    font.family: Theme.fontFamily
    font.pixelSize: Theme.bodySize
    contentItem: Text {
        leftPadding: 10
        text: control.text
        textFormat: Text.PlainText
        font: control.font
        color: control.danger ? Theme.danger : Theme.text
        verticalAlignment: Text.AlignVCenter
    }
    background: Rectangle {
        radius: 6
        color: control.highlighted || control.hovered ? Theme.hover : "transparent"
    }
}
