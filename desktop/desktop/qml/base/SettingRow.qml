import QtQuick
import QtQuick.Layouts

// Linha de configuração: título, uma linha de explicação e o controle à direita (interruptor, botão...).
// Clicável quando "clickable" (vira item de menu com seta).
Item {
    id: root
    property string title: ""
    property string subtitle: ""
    property string risk: ""
    property bool clickable: false
    property bool showDivider: true
    default property alias trailing: trailingBox.data
    signal clicked()

    implicitHeight: Math.max(56, col.implicitHeight + 24)
    Layout.fillWidth: true
    Accessible.role: clickable ? Accessible.Button : Accessible.StaticText
    Accessible.name: title

    Rectangle {
        anchors.fill: parent
        anchors.leftMargin: -8
        anchors.rightMargin: -8
        radius: 8
        color: mouse.pressed ? Theme.pressed : Theme.hover
        visible: root.clickable && (mouse.containsMouse || root.activeFocus)
    }
    MouseArea {
        id: mouse
        anchors.fill: parent
        enabled: root.clickable
        hoverEnabled: root.clickable
        cursorShape: root.clickable ? Qt.PointingHandCursor : Qt.ArrowCursor
        onClicked: root.clicked()
    }
    Keys.onReturnPressed: if (clickable) clicked()
    Keys.onSpacePressed: if (clickable) clicked()
    activeFocusOnTab: clickable

    RowLayout {
        anchors.fill: parent
        spacing: 16
        ColumnLayout {
            id: col
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignVCenter
            spacing: 3
            Text {
                Layout.fillWidth: true
                text: root.title
                color: Theme.text
                font.family: Theme.fontFamily
                font.pixelSize: Theme.bodySize
                font.weight: Font.Medium
                wrapMode: Text.WordWrap
            }
            Text {
                Layout.fillWidth: true
                visible: root.subtitle !== ""
                text: root.subtitle
                color: Theme.text2
                font.family: Theme.fontFamily
                font.pixelSize: Theme.smallSize
                wrapMode: Text.WordWrap
                lineHeight: 1.15
            }
            Text {
                Layout.fillWidth: true
                visible: root.risk !== ""
                text: "Risco: " + root.risk
                color: Theme.amberText
                font.family: Theme.fontFamily
                font.pixelSize: Theme.smallSize
                wrapMode: Text.WordWrap
                lineHeight: 1.15
            }
        }
        Item {
            id: trailingBox
            Layout.alignment: Qt.AlignVCenter
            implicitWidth: childrenRect.width
            implicitHeight: childrenRect.height
        }
        Icon {
            visible: root.clickable
            name: "chevron-right"
            color: Theme.text2
            size: 16
            Layout.alignment: Qt.AlignVCenter
        }
    }
    Rectangle {
        visible: root.showDivider
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 1
        color: Theme.divider
    }
}
