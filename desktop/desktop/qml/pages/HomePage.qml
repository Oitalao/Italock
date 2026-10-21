import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

import PCBioUnlock
import 'qrc:/ui/base'

// Tela principal: estado da proteção (com o interruptor) e a lista de celulares pareados.
Item {
    id: page
    property var devices: []
    property bool protectionOn: false
    readonly property bool multiUser: {
        let users = {};
        for (let d of devices) users[d.user] = true;
        return Object.keys(users).length > 1;
    }

    function refresh() {
        protectionOn = MainWindow.IsProtectionOn();
        devices = MainWindow.GetDevices();
    }
    Component.onCompleted: refresh()
    StackView.onActivated: refresh()
    // "Último uso" muda quando o celular trava ou destrava: relê a lista de tempos em tempos com a tela à vista.
    Timer {
        interval: 30000
        repeat: true
        running: page.StackView.status === StackView.Active && Qt.application.state === Qt.ApplicationActive
        onTriggered: page.devices = MainWindow.GetDevices()
    }

    function lastUsedText(ts) {
        if (!ts) return "Ainda não usado";
        let d = new Date(ts * 1000);
        let now = new Date();
        let diff = (now - d) / 1000;
        if (diff < 60) return "Usado agora há pouco";
        if (diff < 3600) return "Usado há " + Math.floor(diff / 60) + " min";
        let hm = Qt.formatTime(d, "HH:mm");
        if (d.toDateString() === now.toDateString()) return "Usado hoje às " + hm;
        let y = new Date(now); y.setDate(now.getDate() - 1);
        if (d.toDateString() === y.toDateString()) return "Usado ontem às " + hm;
        return "Usado em " + Qt.formatDate(d, "dd/MM/yyyy");
    }
    function shortUser(u) {
        let i = u.lastIndexOf("\\");
        return i >= 0 ? u.substring(i + 1) : u;
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.gutter
        anchors.topMargin: 16
        spacing: 16

        // Cabeçalho
        RowLayout {
            Layout.fillWidth: true
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            spacing: 8
            Text {
                text: "Ítalock"
                color: Theme.text
                font.family: Theme.fontFamily
                font.pixelSize: Theme.titleSize
                font.weight: Font.DemiBold
                Accessible.role: Accessible.Heading
            }
            Text {
                Layout.fillWidth: true
                Layout.leftMargin: 6
                text: MainWindow.GetHostName()
                color: Theme.text2
                font.family: Theme.fontFamily
                font.pixelSize: Theme.smallSize
                elide: Text.ElideRight
                Layout.alignment: Qt.AlignBaseline
            }
            Btn {
                kind: "icon"
                iconName: "tune"
                Accessible.name: "Configurações"
                ToolTip.text: "Configurações"
                ToolTip.visible: hovered
                ToolTip.delay: 500
                onClicked: stack.push("qrc:/ui/pages/SettingsPage.qml")
            }
        }

        // Estado da proteção
        Rectangle {
            Layout.fillWidth: true
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            implicitHeight: statusRow.implicitHeight + 32
            radius: Theme.radius
            color: Theme.surface
            border.width: 1
            border.color: Theme.divider

            RowLayout {
                id: statusRow
                anchors.fill: parent
                anchors.margins: 16
                spacing: 14
                Rectangle {
                    Layout.alignment: Qt.AlignVCenter
                    width: 40
                    height: 40
                    radius: 10
                    color: page.protectionOn ? Theme.brand : Theme.hover
                    Icon {
                        anchors.centerIn: parent
                        name: page.protectionOn ? "lock" : "lock-open"
                        color: page.protectionOn ? "#FFFFFF" : Theme.text2
                        size: 22
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    Text {
                        Layout.fillWidth: true
                        text: "Tela de bloqueio protegida pelo Ítalock"
                        color: Theme.text
                        font.family: Theme.fontFamily
                        font.pixelSize: Theme.bodySize
                        font.weight: Font.DemiBold
                        wrapMode: Text.WordWrap
                    }
                    Text {
                        Layout.fillWidth: true
                        text: page.protectionOn
                              ? "Ligado. Ao travar, este computador chama o celular."
                              : "Desligado. Só a senha destrava."
                        color: Theme.text2
                        font.family: Theme.fontFamily
                        font.pixelSize: Theme.smallSize
                        wrapMode: Text.WordWrap
                    }
                }
                Toggle {
                    id: protectionToggle
                    Layout.alignment: Qt.AlignVCenter
                    checked: page.protectionOn
                    Accessible.name: "Tela de bloqueio protegida pelo Ítalock"
                    onToggled: {
                        let want = checked;
                        checked = Qt.binding(() => page.protectionOn); // só muda depois de aplicar
                        if (!want && page.devices.length > 0) {
                            window.ask("Desligar a proteção?",
                                       "A tela de bloqueio volta a pedir só a senha. Os celulares pareados continuam guardados.",
                                       "Desligar", true, () => MainWindow.SetProtection(window, false));
                        } else {
                            MainWindow.SetProtection(window, want);
                        }
                    }
                }
            }
        }

        // Lista
        RowLayout {
            Layout.fillWidth: true
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 4
            visible: page.devices.length > 0
            Text {
                Layout.fillWidth: true
                text: page.devices.length === 1 ? "1 celular pareado" : page.devices.length + " celulares pareados"
                color: Theme.text2
                font.family: Theme.fontFamily
                font.pixelSize: Theme.smallSize
                font.weight: Font.DemiBold
            }
            Btn {
                kind: "primary"
                text: "Parear celular"
                enabled: page.protectionOn
                onClicked: window.startPairing()
            }
        }

        Rectangle {
            Layout.fillWidth: true
            // Do tamanho da lista (a coluna encolhe e a lista rola se não couber)
            Layout.preferredHeight: page.devices.length > 0 ? list.contentHeight + 8 : 280
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            radius: Theme.radius
            color: Theme.surface
            border.width: 1
            border.color: Theme.divider
            clip: true

            ListView {
                id: list
                anchors.fill: parent
                anchors.margins: 4
                visible: page.devices.length > 0
                model: page.devices
                boundsBehavior: Flickable.StopAtBounds
                ScrollBar.vertical: ScrollBar {}
                delegate: Item {
                    id: row
                    required property var modelData
                    required property int index
                    width: ListView.view.width
                    height: 68
                    Rectangle {
                        anchors.fill: parent
                        anchors.margins: 2
                        radius: 8
                        color: Theme.hover
                        visible: rowMouse.containsMouse
                    }
                    MouseArea {
                        id: rowMouse
                        anchors.fill: parent
                        hoverEnabled: true
                        acceptedButtons: Qt.RightButton
                        onClicked: rowMenu.popup()
                    }
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 16
                        anchors.rightMargin: 8
                        spacing: 12
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 3
                            Text {
                                Layout.fillWidth: true
                                text: row.modelData.name
                                textFormat: Text.PlainText
                                color: Theme.text
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.bodySize
                                font.weight: Font.DemiBold
                                elide: Text.ElideRight
                            }
                            Text {
                                Layout.fillWidth: true
                                text: [row.modelData.method,
                                       page.multiUser ? "conta " + page.shortUser(row.modelData.user) : "",
                                       page.lastUsedText(row.modelData.lastUsed)].filter(s => s !== "").join("  ·  ")
                                textFormat: Text.PlainText
                                color: Theme.text2
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.smallSize
                                elide: Text.ElideRight
                            }
                        }
                        Btn {
                            kind: "icon"
                            iconName: "more"
                            Accessible.name: "Opções de " + row.modelData.name
                            ToolTip.text: "Opções"
                            ToolTip.visible: hovered
                            ToolTip.delay: 500
                            onClicked: rowMenu.popup(this, 0, height)
                        }
                    }
                    Rectangle {
                        visible: row.index < page.devices.length - 1
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.leftMargin: 16
                        anchors.rightMargin: 16
                        anchors.bottom: parent.bottom
                        height: 1
                        color: Theme.divider
                    }
                    AppMenu {
                        id: rowMenu
                        AppMenuItem {
                            text: "Testar destravar"
                            onTriggered: window.openUnlockTest(row.modelData.id)
                        }
                        AppMenuItem {
                            text: "Remover…"
                            danger: true
                            onTriggered: window.confirmRemove(row.modelData.id, row.modelData.name)
                        }
                    }
                }
            }

            // Nada pareado ainda
            ColumnLayout {
                anchors.centerIn: parent
                width: Math.min(parent.width - 48, 380)
                visible: page.devices.length === 0
                spacing: 10
                Icon {
                    Layout.alignment: Qt.AlignHCenter
                    name: "phone"
                    color: Theme.text2
                    size: 32
                }
                Text {
                    Layout.fillWidth: true
                    text: "Nenhum celular pareado"
                    horizontalAlignment: Text.AlignHCenter
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.bodySize
                    font.weight: Font.DemiBold
                }
                Text {
                    Layout.fillWidth: true
                    text: page.protectionOn
                          ? "Pareie o celular para destravar este computador com a digital ou o rosto."
                          : "Ligue a proteção acima e depois pareie o celular."
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                }
                Btn {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.topMargin: 6
                    kind: "primary"
                    text: "Parear celular"
                    enabled: page.protectionOn
                    onClicked: window.startPairing()
                }
            }
        }
        Item { Layout.fillHeight: true }
    }
}
