import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

import PCBioUnlock
import 'qrc:/ui/base'

// Assistente de pareamento: conta → como conectar → (Bluetooth: escolher o celular) → QR → pronto.
Item {
    id: page
    property string step: "account"
    property string method: "wifi"
    property string btAddress: ""
    property string pairedName: ""
    property string qrSource: ""
    property string pairingCode: ""
    property bool btPairing: false
    property bool checking: false
    // Lidos de novo ao chegar no passo "como conectar" e a cada 3 s nele (Bluetooth ou Tailscale ligados depois).
    property bool hasBluetooth: PairingForm.HasBluetooth()
    property string tailscaleIP: PairingForm.GetTailscaleIP()
    function refreshCaps() {
        hasBluetooth = PairingForm.HasBluetooth();
        tailscaleIP = PairingForm.GetTailscaleIP();
    }
    onStepChanged: if (step === "method") refreshCaps()
    Timer {
        interval: 3000
        repeat: true
        running: page.step === "method"
        onTriggered: page.refreshCaps()
    }

    readonly property int totalSteps: method === "bluetooth" ? 4 : 3
    readonly property int stepNumber: ({ "account": 1, "method": 2, "bt": 3, "qr": totalSteps, "done": totalSteps })[step]

    Component.onDestruction: PairingForm.Cancel()

    function goBack() {
        if (step === "account" || step === "done") { stack.pop(); return; }
        if (step === "method") { step = "account"; return; }
        if (step === "bt") { PairingForm.StopBluetoothScan(); step = "method"; return; }
        if (step === "qr") {
            PairingForm.StopQR(); // voltar do QR desliga o servidor de pareamento
            qrSource = "";
            pairingCode = "";
            step = method === "bluetooth" ? "bt" : "method";
            if (step === "bt") startScan();
            return;
        }
    }

    // ---- passo 1: conta ----
    UserListModel { id: userModel }
    property var accounts: []
    Component.onCompleted: {
        let list = [];
        let current = 0;
        for (let i = 0; i < userModel.size(); i++) {
            let u = userModel.get(i);
            let name = u.userName;
            let shortName = name.lastIndexOf("\\") >= 0 ? name.substring(name.lastIndexOf("\\") + 1) : name;
            list.push({ text: shortName + (u.isMicrosoftAccount ? "  (conta Microsoft)" : ""), value: name, ms: u.isMicrosoftAccount });
            if (u.isCurrentUser) current = i;
        }
        accounts = list;
        accountBox.currentIndex = current;
        passwordField.forceActiveFocus();
    }
    readonly property string chosenUser: manualUser.visible ? manualUser.text : (accounts.length > 0 && accountBox.currentIndex >= 0 ? accounts[accountBox.currentIndex].value : "")
    readonly property bool chosenIsMs: !manualUser.visible && accounts.length > 0 && accountBox.currentIndex >= 0 && accounts[accountBox.currentIndex].ms

    // A conferência da senha roda numa thread (leva ~1 s); a resposta chega em accountChecked (via window).
    function checkAccount(skip) {
        if (checking) return;
        checking = true;
        PairingForm.CheckAccountAsync(window, page.chosenUser, passwordField.text, manualUser.visible, skip);
    }
    function accountChecked(r) {
        checking = false;
        if (step !== "account") return;
        if (r.ok) { step = "method"; return; }
        if (r.canSkip) window.showSkipPasswordCheck(r.error, () => page.checkAccount(true));
        else window.showErrorMessage(r.error);
    }

    // ---- passo 3 (Bluetooth) ----
    ListModel { id: btModel }
    property int btIndex: -1
    function startScan() {
        btModel.clear();
        btIndex = -1;
        btAddress = "";
        PairingForm.StartBluetoothScan(window);
    }
    function updateBluetoothDeviceList(devices) {
        let selected = btIndex >= 0 && btIndex < btModel.count ? btModel.get(btIndex).address : "";
        btModel.clear();
        btIndex = -1;
        for (let i = 0; i < devices.length; i++) {
            btModel.append({ name: devices[i].name !== "" ? devices[i].name : devices[i].address, address: devices[i].address });
            if (devices[i].address === selected) btIndex = i;
        }
    }
    function finishBluetoothPairing(ok) {
        btPairing = false;
        if (step !== "bt") return; // a pessoa voltou enquanto o Windows pareava: ignora a resposta
        if (!ok) {
            window.showErrorMessage(QI18n.Get("error_bluetooth_pairing"));
            startScan();
            return;
        }
        startQR();
    }

    // ---- passo QR ----
    function startQR() {
        let r = PairingForm.StartQR(window, method, btAddress);
        if (r.error !== "") { window.showErrorMessage(r.error); return; }
        qrSource = r.qr;
        pairingCode = r.code;
        step = "qr";
    }
    function pairingSucceeded(name) {
        pairedName = name;
        step = "done";
        PairingForm.Cancel();
    }

    TextEdit { id: clip; visible: false }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.gutter
        anchors.topMargin: 16
        spacing: 4

        PageHeader {
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            title: "Parear celular"
            onBack: page.goBack()
        }
        Text {
            Layout.fillWidth: true
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            Layout.leftMargin: 48
            visible: page.step !== "done"
            text: "Passo " + page.stepNumber + " de " + page.totalSteps
            color: Theme.text2
            font.family: Theme.fontFamily
            font.pixelSize: Theme.smallSize
        }

        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.maximumWidth: 760
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 16

            // ===== Conta =====
            ColumnLayout {
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.leftMargin: 48
                anchors.rightMargin: 48
                visible: page.step === "account"
                spacing: 10

                Text {
                    Layout.fillWidth: true
                    text: "Qual conta o celular vai destravar?"
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: 17
                    font.weight: Font.DemiBold
                    wrapMode: Text.WordWrap
                }
                Text {
                    Layout.fillWidth: true
                    text: "A senha fica guardada cifrada neste computador. O celular só libera o uso dela."
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                    Layout.bottomMargin: 8
                }
                Text { text: "Conta"; color: Theme.text; font.family: Theme.fontFamily; font.pixelSize: Theme.smallSize; font.weight: Font.DemiBold }
                Choice {
                    id: accountBox
                    Layout.fillWidth: true
                    visible: !manualUser.visible
                    model: page.accounts
                    textRole: "text"
                    Accessible.name: "Conta"
                }
                Field {
                    id: manualUser
                    Layout.fillWidth: true
                    visible: false
                    placeholderText: "COMPUTADOR\\usuário ou email da conta Microsoft"
                    Accessible.name: "Conta"
                }
                Btn {
                    kind: "text"
                    leftPadding: 0
                    text: manualUser.visible ? "Escolher da lista" : "Outra conta"
                    onClicked: { manualUser.visible = !manualUser.visible; if (manualUser.visible) manualUser.forceActiveFocus(); }
                }
                Text {
                    Layout.topMargin: 4
                    text: "Senha"
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    font.weight: Font.DemiBold
                }
                Field {
                    id: passwordField
                    Layout.fillWidth: true
                    echoMode: TextInput.Password
                    Accessible.name: "Senha"
                    Keys.onReturnPressed: if (!page.checking) page.checkAccount(false)
                    Keys.onEnterPressed: if (!page.checking) page.checkAccount(false)
                }
                Text {
                    Layout.fillWidth: true
                    visible: page.chosenIsMs
                    text: "Conta Microsoft: use a senha da conta, não o PIN."
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }
                RowLayout {
                    Layout.topMargin: 12
                    Item { Layout.fillWidth: true }
                    Busy { running: page.checking; size: 18 }
                    Btn {
                        kind: "primary"
                        text: page.checking ? "Conferindo…" : "Continuar"
                        enabled: !page.checking && page.chosenUser !== "" && passwordField.text !== ""
                        onClicked: page.checkAccount(false)
                    }
                }
            }

            // ===== Método =====
            ColumnLayout {
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.leftMargin: 48
                anchors.rightMargin: 48
                visible: page.step === "method"
                spacing: 10

                Text {
                    Layout.fillWidth: true
                    text: "Como o celular vai falar com este computador?"
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: 17
                    font.weight: Font.DemiBold
                    wrapMode: Text.WordWrap
                    Layout.bottomMargin: 6
                }
                Repeater {
                    model: [
                        { id: "wifi", title: "Mesma rede", desc: "Celular e computador na mesma rede. Vale também o cabo USB com ancoragem.", ok: true, why: "" },
                        { id: "bluetooth", title: "Bluetooth", desc: "Funciona sem rede, com o celular perto do computador.", ok: page.hasBluetooth, why: "Este computador está sem Bluetooth ligado." },
                        { id: "tailscale", title: "Pela internet (Tailscale)", desc: "De qualquer lugar, com o Tailscale ligado no celular e aqui.", ok: page.tailscaleIP !== "", why: "O Tailscale não está ligado neste computador." }
                    ]
                    delegate: Rectangle {
                        id: opt
                        required property var modelData
                        readonly property bool selected: page.method === modelData.id
                        Layout.fillWidth: true
                        implicitHeight: optCol.implicitHeight + 28
                        radius: Theme.radius
                        color: optMouse.containsMouse && modelData.ok ? Theme.hover : Theme.surface
                        border.width: selected ? 2 : 1
                        border.color: selected ? Theme.brandOn : Theme.divider
                        opacity: modelData.ok ? 1 : 0.6
                        activeFocusOnTab: modelData.ok
                        Accessible.role: Accessible.RadioButton
                        Accessible.name: modelData.title
                        Accessible.checked: selected
                        Keys.onSpacePressed: if (modelData.ok) page.method = modelData.id
                        MouseArea {
                            id: optMouse
                            anchors.fill: parent
                            hoverEnabled: true
                            cursorShape: opt.modelData.ok ? Qt.PointingHandCursor : Qt.ArrowCursor
                            onClicked: if (opt.modelData.ok) page.method = opt.modelData.id
                        }
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 14
                            spacing: 14
                            Rectangle {
                                Layout.alignment: Qt.AlignTop
                                Layout.topMargin: 1
                                width: 18; height: 18; radius: 9
                                color: "transparent"
                                border.width: 2
                                border.color: opt.selected ? Theme.brandOn : Theme.fieldBorder
                                Rectangle { anchors.centerIn: parent; width: 8; height: 8; radius: 4; color: Theme.brandOn; visible: opt.selected }
                            }
                            ColumnLayout {
                                id: optCol
                                Layout.fillWidth: true
                                spacing: 3
                                Text {
                                    text: opt.modelData.title
                                    color: Theme.text
                                    font.family: Theme.fontFamily
                                    font.pixelSize: Theme.bodySize
                                    font.weight: Font.DemiBold
                                }
                                Text {
                                    Layout.fillWidth: true
                                    text: opt.modelData.ok ? opt.modelData.desc : opt.modelData.why
                                    color: Theme.text2
                                    font.family: Theme.fontFamily
                                    font.pixelSize: Theme.smallSize
                                    wrapMode: Text.WordWrap
                                }
                            }
                        }
                    }
                }
                RowLayout {
                    Layout.topMargin: 12
                    Item { Layout.fillWidth: true }
                    Btn {
                        kind: "primary"
                        text: "Continuar"
                        onClicked: {
                            if (page.method === "bluetooth") { page.step = "bt"; page.startScan(); }
                            else page.startQR();
                        }
                    }
                }
            }

            // ===== Bluetooth: escolher o celular =====
            ColumnLayout {
                anchors.fill: parent
                anchors.leftMargin: 48
                anchors.rightMargin: 48
                visible: page.step === "bt"
                spacing: 10
                Text {
                    Layout.fillWidth: true
                    text: page.btPairing ? "Confirme o código no celular e neste computador" : "Escolha o celular"
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: 17
                    font.weight: Font.DemiBold
                    wrapMode: Text.WordWrap
                }
                Text {
                    Layout.fillWidth: true
                    text: page.btPairing ? "O Windows e o celular mostram o mesmo número. Confirme nos dois."
                                         : "No celular, abra Configurações › Bluetooth e deixe a tela aberta. Ele aparece aqui em alguns segundos."
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }
                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.topMargin: 6
                    radius: Theme.radius
                    color: Theme.surface
                    border.width: 1
                    border.color: Theme.divider
                    clip: true
                    ListView {
                        id: btList
                        anchors.fill: parent
                        anchors.margins: 4
                        model: btModel
                        enabled: !page.btPairing
                        ScrollBar.vertical: ScrollBar {}
                        delegate: Rectangle {
                            id: btRow
                            required property int index
                            required property string name
                            required property string address
                            width: ListView.view.width
                            height: 48
                            radius: 8
                            color: page.btIndex === index ? Theme.hover : (btMouse.containsMouse ? Theme.hover : "transparent")
                            border.width: page.btIndex === index ? 2 : 0
                            border.color: Theme.brandOn
                            MouseArea {
                                id: btMouse
                                anchors.fill: parent
                                hoverEnabled: true
                                onClicked: { page.btIndex = btRow.index; page.btAddress = btRow.address; }
                            }
                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                anchors.leftMargin: 14
                                text: btRow.name
                                textFormat: Text.PlainText
                                color: Theme.text
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.bodySize
                            }
                        }
                    }
                    RowLayout {
                        anchors.centerIn: parent
                        visible: btModel.count === 0
                        spacing: 10
                        Busy { size: 18 }
                        Text { text: "Procurando…"; color: Theme.text2; font.family: Theme.fontFamily; font.pixelSize: Theme.bodySize }
                    }
                }
                RowLayout {
                    Layout.topMargin: 8
                    Item { Layout.fillWidth: true }
                    Busy { running: page.btPairing; size: 18 }
                    Btn {
                        kind: "primary"
                        text: page.btPairing ? "Pareando…" : "Parear"
                        enabled: page.btIndex >= 0 && !page.btPairing
                        onClicked: { page.btPairing = true; PairingForm.PairBluetooth(window, page.btAddress); }
                    }
                }
            }

            // ===== QR =====
            GridLayout {
                id: qrGrid
                anchors.fill: parent
                visible: page.step === "qr"
                columns: width >= 600 ? 2 : 1
                columnSpacing: 32
                rowSpacing: 16

                Rectangle {
                    Layout.alignment: Qt.AlignTop | Qt.AlignHCenter
                    readonly property int side: Math.max(200, Math.min(300, qrGrid.height - 8, qrGrid.columns === 2 ? qrGrid.width / 2 - 16 : 260))
                    Layout.preferredWidth: side
                    Layout.preferredHeight: side
                    radius: Theme.radius
                    color: "#FFFFFF"
                    border.width: Theme.dark ? 0 : 1
                    border.color: Theme.divider
                    Image {
                        anchors.fill: parent
                        anchors.margins: 10
                        source: page.qrSource
                        sourceSize: Qt.size(width, height)
                        fillMode: Image.PreserveAspectFit
                        smooth: false
                        Accessible.name: "Código QR de pareamento"
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.alignment: Qt.AlignTop
                    spacing: 12
                    Text {
                        Layout.fillWidth: true
                        text: "Leia o código com o celular"
                        color: Theme.text
                        font.family: Theme.fontFamily
                        font.pixelSize: 17
                        font.weight: Font.DemiBold
                        wrapMode: Text.WordWrap
                    }
                    Repeater {
                        model: ["No celular, abra o Ítalock.", "Toque em Parear computador.", "Aponte a câmera para o código."]
                        delegate: RowLayout {
                            required property string modelData
                            required property int index
                            Layout.fillWidth: true
                            spacing: 10
                            Rectangle {
                                width: 22; height: 22; radius: 11
                                color: Theme.hover
                                Text {
                                    anchors.centerIn: parent
                                    text: index + 1
                                    color: Theme.text
                                    font.family: Theme.fontFamily
                                    font.pixelSize: Theme.smallSize
                                    font.weight: Font.DemiBold
                                }
                            }
                            Text {
                                Layout.fillWidth: true
                                text: modelData
                                color: Theme.text
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.bodySize
                                wrapMode: Text.WordWrap
                            }
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        visible: page.method === "tailscale"
                        text: "O Tailscale precisa estar ligado no celular."
                        color: Theme.text2
                        font.family: Theme.fontFamily
                        font.pixelSize: Theme.smallSize
                        wrapMode: Text.WordWrap
                    }
                    RowLayout {
                        Layout.topMargin: 6
                        spacing: 10
                        Busy { size: 18 }
                        Text {
                            text: "Esperando o celular…"
                            color: Theme.text2
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.bodySize
                        }
                    }
                    Rectangle { Layout.fillWidth: true; Layout.topMargin: 8; height: 1; color: Theme.divider }
                    Text {
                        Layout.fillWidth: true
                        text: "Sem câmera? Digite este código no app:"
                        color: Theme.text2
                        font.family: Theme.fontFamily
                        font.pixelSize: Theme.smallSize
                        wrapMode: Text.WordWrap
                    }
                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        Text {
                            Layout.fillWidth: true
                            text: page.pairingCode
                            color: Theme.text
                            font.family: Theme.monoFamily
                            font.pixelSize: Theme.smallSize
                            wrapMode: Text.WrapAnywhere
                            maximumLineCount: 4
                            elide: Text.ElideRight
                        }
                        Btn {
                            Layout.alignment: Qt.AlignTop
                            kind: "icon"
                            iconName: "copy"
                            Accessible.name: "Copiar código"
                            ToolTip.text: "Copiar"
                            ToolTip.visible: hovered
                            ToolTip.delay: 400
                            onClicked: { clip.text = page.pairingCode; clip.selectAll(); clip.copy(); }
                        }
                    }
                }
            }

            // ===== Pronto =====
            ColumnLayout {
                anchors.centerIn: parent
                width: Math.min(parent.width - 48, 420)
                visible: page.step === "done"
                spacing: 12
                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    width: 56; height: 56; radius: 28
                    color: Theme.brand
                    Icon { anchors.centerIn: parent; name: "check"; color: "#FFFFFF"; size: 28 }
                }
                Text {
                    Layout.fillWidth: true
                    horizontalAlignment: Text.AlignHCenter
                    text: "Pareado com " + (page.pairedName !== "" ? page.pairedName : "o celular")
                    color: Theme.text
                    font.family: Theme.fontFamily
                    font.pixelSize: 17
                    font.weight: Font.DemiBold
                    wrapMode: Text.WordWrap
                }
                Text {
                    Layout.fillWidth: true
                    horizontalAlignment: Text.AlignHCenter
                    text: "Na próxima vez que o computador travar, ele chama este celular."
                    color: Theme.text2
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.smallSize
                    wrapMode: Text.WordWrap
                }
                Btn {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.topMargin: 8
                    kind: "primary"
                    text: "Concluir"
                    onClicked: stack.pop()
                }
            }
        }
    }
}
