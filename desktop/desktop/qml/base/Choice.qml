import QtQuick
import QtQuick.Controls.Basic

// Lista de escolha (ComboBox) no visual do Ítalock.
ComboBox {
    id: control
    implicitHeight: 40
    font.family: Theme.fontFamily
    font.pixelSize: Theme.bodySize
    hoverEnabled: true

    contentItem: Text {
        leftPadding: 12
        rightPadding: 36
        text: control.displayText
        textFormat: Text.PlainText
        font: control.font
        color: control.enabled ? Theme.text : Theme.text2
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }
    indicator: Icon {
        x: control.width - width - 10
        y: (control.height - height) / 2
        name: "chevron-down"
        color: Theme.text2
        size: 18
    }
    background: Rectangle {
        radius: 8
        color: control.hovered ? Theme.hover : Theme.field
        border.width: control.visualFocus || control.popup.visible ? 2 : 1
        border.color: control.visualFocus || control.popup.visible ? Theme.brandOn : Theme.fieldBorder
    }
    delegate: ItemDelegate {
        id: item
        required property int index
        width: control.width - 8
        x: 4
        height: 40
        hoverEnabled: true
        highlighted: control.highlightedIndex === index
        contentItem: Text {
            text: control.textAt(item.index)
            textFormat: Text.PlainText
            font: control.font
            color: Theme.text
            verticalAlignment: Text.AlignVCenter
            elide: Text.ElideRight
            leftPadding: 8
        }
        background: Rectangle {
            radius: 6
            color: item.highlighted ? Theme.hover : "transparent"
        }
    }
    popup: Popup {
        y: control.height + 4
        width: control.width
        implicitHeight: Math.min(contentItem.implicitHeight + 8, 320)
        padding: 4
        contentItem: ListView {
            clip: true
            implicitHeight: contentHeight
            model: control.popup.visible ? control.delegateModel : null
            currentIndex: control.highlightedIndex
        }
        background: Rectangle {
            radius: 10
            color: Theme.surface
            border.width: 1
            border.color: Theme.divider
        }
    }
}
