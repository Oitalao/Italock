import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

// Cabeçalho de página interna: voltar + título.
RowLayout {
    id: root
    property string title: ""
    signal back()
    spacing: 8
    Layout.fillWidth: true

    Btn {
        kind: "icon"
        iconName: "back"
        ToolTip.text: "Voltar"
        ToolTip.visible: hovered
        ToolTip.delay: 600
        Accessible.name: "Voltar"
        onClicked: root.back()
    }
    Text {
        Layout.fillWidth: true
        text: root.title
        color: Theme.text
        font.family: Theme.fontFamily
        font.pixelSize: Theme.titleSize
        font.weight: Font.DemiBold
        elide: Text.ElideRight
        Accessible.role: Accessible.Heading
    }
}
