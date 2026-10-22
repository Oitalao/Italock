import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

import PCBioUnlock
import 'qrc:/ui/base'

// Sobre: o crédito ao PC Bio Unlock e os links da licença/código-fonte são obrigação da GPL-3.0 — não tirar.
Item {
    id: page
    property bool showLicenses: false
    readonly property string sourceUrl: MainWindow.GetSourceUrl()

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.gutter
        anchors.topMargin: 16
        spacing: 8

        PageHeader {
            Layout.maximumWidth: 680
            Layout.alignment: Qt.AlignHCenter
            title: qsTr("Sobre o Ítalock")
            onBack: stack.pop()
        }

        ScrollView {
            id: scroll
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            clip: true
            ColumnLayout {
                width: Math.min(scroll.availableWidth, 680)
                x: (scroll.availableWidth - width) / 2
                spacing: 10

                RowLayout {
                    Layout.topMargin: 12
                    spacing: 14
                    Image {
                        source: "qrc:/res/icons/icon.png"
                        sourceSize: Qt.size(96, 96)
                        Layout.preferredWidth: 48
                        Layout.preferredHeight: 48
                        smooth: true
                    }
                    ColumnLayout {
                        spacing: 2
                        Text {
                            text: "Ítalock"
                            color: Theme.text
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.titleSize
                            font.weight: Font.DemiBold
                        }
                        Text {
                            text: qsTr("Destrave este computador com o celular.")
                            color: Theme.text2
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.bodySize
                        }
                    }
                }

                Rectangle { Layout.fillWidth: true; Layout.topMargin: 10; height: 1; color: Theme.divider }

                Text {
                    Layout.fillWidth: true
                    Layout.topMargin: 6
                    text: qsTr("Baseado no PC Bio Unlock (hoje PulseUnlock), de MeisApps, licença GPL versão 3.")
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.bodySize
                    wrapMode: Text.WordWrap
                }
                Text {
                    Layout.fillWidth: true
                    text: qsTr("O Ítalock é software livre sob a mesma licença: você pode usar, estudar, mudar e redistribuir. Quem distribuir o Ítalock precisa oferecer o código do programa junto.")
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }

                Repeater {
                    model: [
                        { text: qsTr("Licença GPL versão 3"), url: "https://www.gnu.org/licenses/gpl-3.0.html" },
                        { text: qsTr("Código do PC Bio Unlock"), url: "https://github.com/MeisApps/pcbu-desktop" }
                    ]
                    delegate: Btn {
                        required property var modelData
                        kind: "text"
                        leftPadding: 0
                        iconName: "external"
                        text: modelData.text
                        Accessible.name: qsTr("%1 (abre no navegador)").arg(modelData.text)
                        onClicked: Qt.openUrlExternally(modelData.url)
                    }
                }
                // Endereço do código do Ítalock: constante única (vazia até o projeto ir para o GitHub).
                Btn {
                    visible: page.sourceUrl !== ""
                    kind: "text"
                    leftPadding: 0
                    iconName: "external"
                    text: qsTr("Código do Ítalock")
                    Accessible.name: qsTr("Código do Ítalock (abre no navegador)")
                    onClicked: Qt.openUrlExternally(page.sourceUrl)
                }
                Text {
                    Layout.fillWidth: true
                    visible: page.sourceUrl === ""
                    text: qsTr("Código do programa: em breve no GitHub")
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }

                Rectangle { Layout.fillWidth: true; Layout.topMargin: 10; height: 1; color: Theme.divider }
                SettingRow {
                    title: qsTr("Licenças de terceiros")
                    subtitle: qsTr("Qt, OpenSSL, Boost e outras bibliotecas usadas.")
                    clickable: true
                    showDivider: false
                    onClicked: page.showLicenses = !page.showLicenses
                }
                TextArea {
                    visible: page.showLicenses
                    Layout.fillWidth: true
                    readOnly: true
                    wrapMode: Text.Wrap
                    text: page.showLicenses ? MainWindow.GetLicenseText() : ""
                    color: Theme.text2
                    font.family: Theme.monoFamily
                    font.pixelSize: Theme.smallSize
                    background: Rectangle { color: Theme.surface; radius: 8; border.width: 1; border.color: Theme.divider }
                }
            }
        }
    }
}
