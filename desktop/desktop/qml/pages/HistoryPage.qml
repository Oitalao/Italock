import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

import PCBioUnlock
import 'qrc:/ui/base'

// Histórico: os últimos 50 eventos gravados pela tela de bloqueio e pelo agente (uso.json), do mais novo ao mais
// antigo. Só leitura, mais o botão Limpar.
Item {
    id: page
    property var events: []

    function refresh() { events = MainWindow.GetHistory(); }
    Component.onCompleted: refresh()
    StackView.onActivated: refresh()
    // Com a tela à vista, relê de vez em quando (o celular pode travar ou destravar enquanto isso).
    Timer {
        interval: 30000
        repeat: true
        running: page.StackView.status === StackView.Active && Qt.application.state === Qt.ApplicationActive
        onTriggered: page.refresh()
    }

    function phone(e) { return e.name !== "" ? e.name : "celular"; }
    function titleOf(e) {
        let n = phone(e);
        if (e.action === "destravar") {
            if (e.result === "ok") return "Destravado pelo " + n;
            if (e.result === "recusado") return "Recusado no " + n;
            if (e.result === "expirou") return "Pedido não confirmado a tempo no " + n;
            if (e.result === "sem_resposta") return "Nenhum celular respondeu";
            return "Destravar não deu certo";
        }
        if (e.action === "travar") {
            if (e.result === "ok") return "Travado pelo " + n;
            if (e.result === "desligado") return "Pedido de travar do " + n + " recusado";
            if (e.result === "falhou") return "O " + n + " pediu para travar, mas não deu certo";
            return "Travar não deu certo";
        }
        if (e.action === "acordar") return n + " pediu para destravar";
        return "Evento do " + n;
    }
    function failed(e) { return e.result !== "ok"; }
    function pathText(p) {
        if (p === "rede local") return "mesma rede";
        return p;
    }
    function whenText(ts) {
        let d = new Date(ts * 1000);
        let now = new Date();
        let hm = Qt.formatTime(d, "HH:mm");
        if (d.toDateString() === now.toDateString()) return "hoje às " + hm;
        let y = new Date(now); y.setDate(now.getDate() - 1);
        if (d.toDateString() === y.toDateString()) return "ontem às " + hm;
        return Qt.formatDate(d, "dd/MM/yyyy") + " às " + hm;
    }
    function detailOf(e) {
        let parts = [whenText(e.ts)];
        if (e.path !== "") parts.push(pathText(e.path));
        if (e.result === "desligado") parts.push("opção desligada nas Configurações");
        if (e.program !== "") parts.push(e.program);
        return parts.join("  ·  ");
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.gutter
        anchors.topMargin: 16
        spacing: 8

        RowLayout {
            Layout.fillWidth: true
            Layout.maximumWidth: 680
            Layout.alignment: Qt.AlignHCenter
            PageHeader {
                title: "Histórico"
                onBack: stack.pop()
            }
            Btn {
                kind: "text"
                text: "Limpar"
                visible: page.events.length > 0
                onClicked: window.ask("Limpar o histórico?",
                                      "Os eventos anotados até agora somem desta lista. Os registros técnicos continuam.",
                                      "Limpar", true, function() {
                                          let err = MainWindow.ClearHistory();
                                          if (err !== "") window.showErrorMessage(err);
                                          page.refresh();
                                      })
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: page.events.length > 0
            Layout.preferredHeight: page.events.length > 0 ? -1 : 220
            Layout.maximumWidth: 680
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 8
            radius: Theme.radius
            color: Theme.surface
            border.width: 1
            border.color: Theme.divider
            clip: true

            ListView {
                id: list
                anchors.fill: parent
                anchors.margins: 4
                visible: page.events.length > 0
                model: page.events
                boundsBehavior: Flickable.StopAtBounds
                ScrollBar.vertical: ScrollBar {}
                Accessible.role: Accessible.List
                Accessible.name: "Histórico"
                delegate: Item {
                    id: row
                    required property var modelData
                    required property int index
                    width: ListView.view.width
                    height: col.implicitHeight + 24
                    Accessible.role: Accessible.ListItem
                    Accessible.name: page.titleOf(modelData) + ". " + page.detailOf(modelData)

                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 16
                        anchors.rightMargin: 16
                        spacing: 12
                        Icon {
                            Layout.alignment: Qt.AlignVCenter
                            name: page.failed(row.modelData) ? "warning" : (row.modelData.action === "travar" ? "lock" : "lock-open")
                            color: page.failed(row.modelData) ? Theme.amberText : Theme.text2
                            size: 18
                        }
                        ColumnLayout {
                            id: col
                            Layout.fillWidth: true
                            Layout.alignment: Qt.AlignVCenter
                            spacing: 3
                            Text {
                                Layout.fillWidth: true
                                text: page.titleOf(row.modelData)
                                textFormat: Text.PlainText
                                color: Theme.text
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.bodySize
                                font.weight: Font.Medium
                                wrapMode: Text.WordWrap
                            }
                            Text {
                                Layout.fillWidth: true
                                text: page.detailOf(row.modelData)
                                textFormat: Text.PlainText
                                color: Theme.text2
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.smallSize
                                wrapMode: Text.WordWrap
                            }
                        }
                    }
                    Rectangle {
                        visible: row.index < page.events.length - 1
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.leftMargin: 16
                        anchors.rightMargin: 16
                        anchors.bottom: parent.bottom
                        height: 1
                        color: Theme.divider
                    }
                }
            }

            ColumnLayout {
                anchors.centerIn: parent
                width: Math.min(parent.width - 48, 380)
                visible: page.events.length === 0
                spacing: 8
                Icon {
                    Layout.alignment: Qt.AlignHCenter
                    name: "lock"
                    color: Theme.text2
                    size: 28
                }
                Text {
                    Layout.fillWidth: true
                    text: "Nada anotado ainda"
                    horizontalAlignment: Text.AlignHCenter
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.bodySize
                    font.weight: Font.DemiBold
                }
                Text {
                    Layout.fillWidth: true
                    text: "Quando o celular travar ou destravar este computador, aparece aqui com o caminho e a hora."
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                }
            }
        }
        Item { Layout.fillHeight: true; visible: page.events.length === 0 }
    }
}
