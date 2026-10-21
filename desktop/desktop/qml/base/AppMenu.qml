import QtQuick
import QtQuick.Controls.Basic

// Menu suspenso no visual do Ítalock.
Menu {
    id: control
    padding: 4
    implicitWidth: 200
    background: Rectangle {
        implicitWidth: 200
        radius: 10
        color: Theme.surface
        border.width: 1
        border.color: Theme.divider
    }
}
