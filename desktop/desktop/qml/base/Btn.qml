import QtQuick
import QtQuick.Controls.Basic

// Botão do Ítalock. kind: "primary" (laranja — uma por tela), "secondary", "text", "danger", "icon".
Button {
    id: control
    property string kind: "secondary"
    property string iconName: ""

    readonly property bool isIcon: kind === "icon"
    readonly property color fg: {
        if (!enabled) return Theme.text2;
        if (kind === "primary") return Theme.textOnAccent;
        if (kind === "danger") return Theme.textOnDanger;
        if (kind === "text") return Theme.link;
        return Theme.text;
    }

    implicitHeight: isIcon ? 40 : 40
    implicitWidth: isIcon ? 40 : Math.max(kind === "text" ? 0 : 88, contentItem.implicitWidth + leftPadding + rightPadding)
    leftPadding: isIcon ? 0 : (kind === "text" ? 8 : 18)
    rightPadding: leftPadding
    font.family: Theme.fontFamily
    font.pixelSize: Theme.bodySize
    font.weight: kind === "text" ? Font.Normal : Font.DemiBold
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus
    Accessible.name: text !== "" ? text : (ToolTip.text || "")

    contentItem: Item {
        implicitWidth: row.implicitWidth
        implicitHeight: row.implicitHeight
        Row {
            id: row
            anchors.centerIn: parent
            spacing: 8
            Icon {
                visible: control.iconName !== ""
                name: control.iconName
                color: control.fg
                size: 20
                anchors.verticalCenter: parent.verticalCenter
            }
            Text {
                visible: control.text !== ""
                text: control.text
                font: control.font
                color: control.fg
                anchors.verticalCenter: parent.verticalCenter
                elide: Text.ElideRight
            }
        }
    }

    background: Rectangle {
        radius: control.isIcon ? 20 : Theme.buttonRadius
        color: {
            if (control.kind === "primary") {
                if (!control.enabled) return Theme.pressed;
                return control.down ? Theme.accentPressed : (control.hovered ? Theme.accentHover : Theme.accent);
            }
            if (control.kind === "danger")
                return control.enabled ? (control.down ? Qt.darker(Theme.dangerFill, 1.15) : Theme.dangerFill) : Theme.pressed;
            if (control.down) return Theme.pressed;
            if (control.hovered) return Theme.hover;
            return control.kind === "secondary" ? Theme.surface : "transparent";
        }
        border.width: control.kind === "secondary" ? 1 : 0
        border.color: Theme.fieldBorder
        // Anel de foco para teclado
        Rectangle {
            anchors.fill: parent
            anchors.margins: -3
            radius: parent.radius + 3
            color: "transparent"
            border.width: 2
            border.color: Theme.brandOn
            visible: control.visualFocus
        }
    }
}
