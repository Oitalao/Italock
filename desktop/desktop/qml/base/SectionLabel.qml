import QtQuick
import QtQuick.Layouts

// Título de grupo dentro de uma página (texto pequeno, cor secundária).
Text {
    Layout.fillWidth: true
    Layout.topMargin: 20
    Layout.bottomMargin: 2
    color: Theme.text2
    font.family: Theme.fontFamily
    font.pixelSize: Theme.smallSize
    font.weight: Font.DemiBold
    font.letterSpacing: 0.3
    Accessible.role: Accessible.Heading
}
