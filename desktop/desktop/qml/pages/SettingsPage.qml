import QtQml
import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

import PCBioUnlock
import 'qrc:/ui/base'

// Configurações em grupos: Geral, Tela de bloqueio, Conexão e Avançado (recolhido). Experimentais num item discreto
// no fim. Cada opção vale na hora (sem botão Salvar), menos portas e tempos do Avançado.
Item {
    id: page
    property bool isWindows: MainWindow.IsWindows()
    property var service: ({})         // id -> {enabled, selectedValue, available, options}
    property var opts: ({})            // opções do sistema (SettingsForm.GetOptions)
    property bool advancedOpen: false
    property bool netDirty: false
    property var devices: []

    function load() {
        SettingsForm.Load();
        let map = {};
        let list = SettingsForm.GetServiceSettings();
        for (let i = 0; i < list.length; i++) {
            let s = list[i];
            map[s.id] = { enabled: s.enabled, selectedValue: s.selectedValue, options: s.optionValues };
        }
        service = map;
        let st = SettingsForm.GetSettings();
        discoveryPort.text = st.pairingDiscoveryPort;
        pairingPort.text = st.pairingServerPort;
        unlockPort.text = st.unlockServerPort;
        connectTimeout.text = st.clientConnectTimeout;
        connectRetries.text = st.clientConnectRetries;
        socketTimeout.text = st.clientSocketTimeout;
        netDirty = false;
        devices = MainWindow.GetDevices();
        opts = SettingsForm.GetOptions();
    }

    // Opção do sistema: grava na hora; em erro avisa e recarrega (o controle volta ao valor gravado).
    function setOption(key, value) {
        let err = SettingsForm.SetOption(key, value);
        if (err !== "") window.showErrorMessage(err);
        opts = SettingsForm.GetOptions();
    }

    readonly property var appearanceOptions: [
        { text: "Escuro (padrão)", value: "escuro" },
        { text: "Claro", value: "claro" },
        { text: "Igual ao sistema", value: "sistema" }
    ]
    function appearanceIndex() {
        let v = MainWindow.GetPref("aparencia");
        for (let i = 0; i < appearanceOptions.length; i++) if (appearanceOptions[i].value === v) return i;
        return 0;
    }
    readonly property var waitOptions: [
        { text: "10 segundos", value: 10 },
        { text: "20 segundos (padrão)", value: 20 },
        { text: "40 segundos", value: 40 }
    ]
    // Atalho de teclado para travar (Windows; o ícone perto do relógio registra a combinação)
    readonly property var hotkeyOptions: [
        { text: "Ctrl + Alt + L (padrão)", value: "ctrl+alt+l" },
        { text: "Ctrl + Alt + Shift + L", value: "ctrl+alt+shift+l" },
        { text: "Ctrl + Alt + K", value: "ctrl+alt+k" },
        { text: "Ctrl + Alt + Espaço", value: "ctrl+alt+espaco" }
    ]
    function hotkeyIndex() {
        let v = MainWindow.GetPref("atalhoTecla");
        for (let i = 0; i < hotkeyOptions.length; i++) if (hotkeyOptions[i].value === v) return i;
        return 0;
    }
    property bool hotkeyOn: false
    property bool hotkeyError: false
    // A bandeja tenta registrar logo depois da mudança; relê o resultado em seguida.
    Timer { id: hotkeyCheck; interval: 1200; onTriggered: page.hotkeyError = page.hotkeyOn && MainWindow.GetPref("atalhoErro") }

    function waitIndex() {
        let v = opts.unlockWaitSeconds;
        for (let i = 0; i < waitOptions.length; i++) if (waitOptions[i].value === v) return i;
        return 1;
    }
    Component.onCompleted: { load(); if (MainWindow.DevPage() === "configuracoes") advancedOpen = true; }

    // Aplica na hora uma opção do sistema (interruptor/lista).
    function applyService(id, enabled, value) {
        SettingsForm.SetServiceSetting(id, enabled, value === undefined ? "" : value);
        let err = SettingsForm.Save();
        if (err !== "") window.showErrorMessage(err);
        load();
    }

    function portOk(t) { let n = parseInt(t, 10); return String(n) === t.trim() && n > 0 && n <= 65535; }
    function numOk(t, min) { let n = parseInt(t, 10); return String(n) === t.trim() && n >= min && n <= 3600; }
    readonly property bool netValid: portOk(discoveryPort.text) && portOk(pairingPort.text) && portOk(unlockPort.text) &&
                                     numOk(connectTimeout.text, 1) && numOk(connectRetries.text, 0) && numOk(socketTimeout.text, 10)

    function saveNetwork() {
        let st = SettingsForm.GetSettings();
        let doSave = function() {
            st.pairingDiscoveryPort = parseInt(discoveryPort.text, 10);
            st.pairingServerPort = parseInt(pairingPort.text, 10);
            st.unlockServerPort = parseInt(unlockPort.text, 10);
            st.clientConnectTimeout = parseInt(connectTimeout.text, 10);
            st.clientConnectRetries = parseInt(connectRetries.text, 10);
            st.clientSocketTimeout = parseInt(socketTimeout.text, 10);
            SettingsForm.SetSettings(st);
            let err = SettingsForm.Save();
            if (err !== "") window.showErrorMessage(err);
            load();
        };
        if (parseInt(unlockPort.text, 10) !== st.unlockServerPort && SettingsForm.HasUDPDevices()) {
            window.ask("Mudar a porta de destravar?",
                       "Os celulares pareados pela rede ou pela internet deixam de funcionar e vão ser removidos. Será preciso parear de novo.",
                       "Mudar e remover", true, function() { SettingsForm.RemoveUDPDevices(); doSave(); });
        } else {
            doSave();
        }
    }

    readonly property var behaviorOptions: [
        { text: "Ao apertar uma tecla (recomendado)", value: "key_press_lock_only" },
        { text: "Ao abrir a tela de bloqueio", value: "foreground_always" },
        { text: "Só pelo botão Destravar do celular", value: "wake_only" }
    ]
    function behaviorIndex() {
        let v = opts.winUnlockBehavior || "key_press_lock_only";
        if (v === "key_press") return 0;                              // valores antigos parecidos
        if (v === "foreground_lock_only" || v === "none") return 1;
        for (let i = 0; i < behaviorOptions.length; i++) if (behaviorOptions[i].value === v) return i;
        return 0;
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.gutter
        anchors.topMargin: 16
        spacing: 8

        PageHeader {
            Layout.maximumWidth: 680
            Layout.alignment: Qt.AlignHCenter
            title: "Configurações"
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
                spacing: 0

                // ---- Geral ----
                SectionLabel { text: "GERAL"; Layout.topMargin: 8 }
                SettingRow {
                    title: "Aparência"
                    subtitle: "Vale para todas as janelas do Ítalock."
                    showDivider: false
                }
                Choice {
                    Layout.fillWidth: true
                    Layout.bottomMargin: 12
                    model: page.appearanceOptions
                    textRole: "text"
                    currentIndex: page.appearanceIndex()
                    Accessible.name: "Aparência"
                    onActivated: function(i) { MainWindow.SetPref("aparencia", page.appearanceOptions[i].value); }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Iniciar com o computador, perto do relógio"
                    subtitle: "O ícone abre sozinho ao entrar no Windows. Por ali dá para travar na hora e abrir esta janela."
                    Toggle {
                        id: trayToggle
                        Accessible.name: "Iniciar com o computador, perto do relógio"
                        Component.onCompleted: checked = page.isWindows && SettingsForm.IsTrayAutostartEnabled()
                        onToggled: {
                            if (!SettingsForm.SetTrayAutostartEnabled(checked)) {
                                checked = !checked;
                                window.showErrorMessage("Não deu para mudar a inicialização do ícone.");
                            }
                        }
                    }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Avisar quando o celular travar ou destravar"
                    subtitle: "Mostra um aviso perto do relógio. Precisa do ícone aberto."
                    Toggle {
                        Accessible.name: "Avisar quando o celular travar ou destravar"
                        Component.onCompleted: checked = MainWindow.GetPref("avisos")
                        onToggled: MainWindow.SetPref("avisos", checked)
                    }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Som ao destravar"
                    subtitle: "Toca o som de destravar do Windows quando o celular destrava este computador."
                    Toggle {
                        Accessible.name: "Som ao destravar"
                        Component.onCompleted: checked = MainWindow.GetPref("som")
                        onToggled: MainWindow.SetPref("som", checked)
                    }
                }
                SettingRow {
                    visible: !page.isWindows
                    title: "Abrir o chaveiro junto"
                    subtitle: "Ao destravar pelo celular, abre também as senhas guardadas (redes sem fio, navegador)."
                    Toggle {
                        Accessible.name: "Abrir o chaveiro junto"
                        checked: page.service.pamSetPassword ? page.service.pamSetPassword.enabled : false
                        onToggled: page.applyService("pamSetPassword", checked)
                    }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Atalho para travar"
                    subtitle: page.hotkeyError
                              ? "Outro programa já usa essa combinação. Escolha outra abaixo."
                              : "Trava este computador na hora, e a tela de bloqueio fica esperando o celular. Precisa do ícone perto do relógio aberto."
                    showDivider: !page.hotkeyOn
                    Toggle {
                        Accessible.name: "Atalho para travar"
                        Component.onCompleted: {
                            checked = page.isWindows && MainWindow.GetPref("atalho");
                            page.hotkeyOn = checked;
                            page.hotkeyError = checked && MainWindow.GetPref("atalhoErro");
                        }
                        onToggled: {
                            MainWindow.SetPref("atalho", checked);
                            page.hotkeyOn = checked;
                            page.hotkeyError = false;
                            hotkeyCheck.restart();
                        }
                    }
                }
                Choice {
                    visible: page.isWindows && page.hotkeyOn
                    Layout.fillWidth: true
                    Layout.bottomMargin: 12
                    model: page.hotkeyOptions
                    textRole: "text"
                    currentIndex: page.hotkeyIndex()
                    Accessible.name: "Combinação de teclas para travar"
                    onActivated: function(i) {
                        MainWindow.SetPref("atalhoTecla", page.hotkeyOptions[i].value);
                        page.hotkeyError = false;
                        hotkeyCheck.restart();
                    }
                }
                SettingRow {
                    title: "Histórico"
                    subtitle: "Os últimos 50 pedidos: quem travou ou destravou, por qual caminho e quando."
                    clickable: true
                    onClicked: stack.push("qrc:/ui/pages/HistoryPage.qml")
                }
                SettingRow {
                    title: "Sobre o Ítalock"
                    clickable: true
                    showDivider: false
                    onClicked: stack.push("qrc:/ui/pages/AboutPage.qml")
                }

                // ---- Tela de bloqueio ----
                SectionLabel { text: page.isWindows ? "TELA DE BLOQUEIO" : "PROTEÇÃO DE TELA" }
                SettingRow {
                    visible: page.isWindows
                    title: "Quando chamar o celular"
                    subtitle: "Esperar uma tecla evita chamar o celular à toa quando a tela apaga sozinha. O botão Destravar do celular funciona em todas."
                    showDivider: false
                }
                Choice {
                    visible: page.isWindows
                    Layout.fillWidth: true
                    Layout.bottomMargin: 12
                    model: page.behaviorOptions
                    textRole: "text"
                    currentIndex: page.behaviorIndex()
                    Accessible.name: "Quando chamar o celular"
                    onActivated: function(i) { page.setOption("winUnlockBehavior", page.behaviorOptions[i].value); }
                }
                SettingRow {
                    title: "Tempo de espera pelo celular"
                    subtitle: "Se o celular nem responder nesse tempo, a tela desiste e fica esperando a senha."
                    showDivider: false
                }
                Choice {
                    Layout.fillWidth: true
                    Layout.bottomMargin: 12
                    model: page.waitOptions
                    textRole: "text"
                    currentIndex: page.waitIndex()
                    Accessible.name: "Tempo de espera pelo celular"
                    onActivated: function(i) { page.setOption("unlockWaitSeconds", page.waitOptions[i].value); }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Tentar de novo sozinho ao ligar o computador"
                    subtitle: "Na tela de entrada logo depois de ligar, chama o celular de novo depois de 1, 2, 5 e 10 minutos se ele não respondeu."
                    Toggle {
                        Accessible.name: "Tentar de novo sozinho ao ligar o computador"
                        checked: page.opts.bootAutoRetry === true
                        onToggled: page.setOption("bootAutoRetry", checked)
                    }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Começar a tela de bloqueio no Ítalock"
                    subtitle: "A tela de bloqueio já abre na opção do Ítalock. Senha e PIN continuam em Opções de entrada."
                    Toggle {
                        Accessible.name: "Começar a tela de bloqueio no Ítalock"
                        checked: page.service.forceCredProv ? page.service.forceCredProv.enabled : false
                        onToggled: page.applyService("forceCredProv", checked)
                    }
                }
                SettingRow {
                    visible: page.isWindows
                    title: "Esconder o campo de senha do Ítalock"
                    subtitle: "Mostra só a mensagem do celular. Para digitar a senha, use Opções de entrada."
                    Toggle {
                        Accessible.name: "Esconder o campo de senha do Ítalock"
                        checked: page.service.hidePasswordField ? page.service.hidePasswordField.enabled : false
                        onToggled: page.applyService("hidePasswordField", checked)
                    }
                }
                SettingRow {
                    title: "Cancelar a chamada ao celular"
                    subtitle: "Aperte juntas as teclas Ctrl e Alt do lado esquerdo. A tela para de esperar o celular e fica só a senha."
                    showDivider: false
                }

                // ---- Conexão ----
                SectionLabel { text: "CONEXÃO" }
                SettingRow {
                    title: "Mesma rede e cabo USB"
                    subtitle: "Celular e computador na mesma rede, ou ligados por cabo com ancoragem."
                    Toggle {
                        Accessible.name: "Mesma rede e cabo USB"
                        checked: page.opts.allowLan === true
                        onToggled: page.setOption("allowLan", checked)
                    }
                }
                SettingRow {
                    title: "Bluetooth"
                    subtitle: "Celular perto do computador, sem precisar de rede."
                    Toggle {
                        Accessible.name: "Bluetooth"
                        checked: page.opts.allowBluetooth === true
                        onToggled: page.setOption("allowBluetooth", checked)
                    }
                }
                SettingRow {
                    title: "Tailscale"
                    subtitle: "De qualquer lugar, com o Tailscale ligado no celular e neste computador."
                    Toggle {
                        Accessible.name: "Tailscale"
                        checked: page.opts.allowTailscale === true
                        onToggled: page.setOption("allowTailscale", checked)
                    }
                }
                SettingRow {
                    title: "Aceitar pedidos de travar pelo celular"
                    subtitle: "Desligado, o botão Travar do celular não faz nada aqui. Ver se está travado e destravar continuam."
                    showDivider: false
                    Toggle {
                        Accessible.name: "Aceitar pedidos de travar pelo celular"
                        checked: page.opts.agentLockEnabled === true
                        onToggled: page.setOption("agentLockEnabled", checked)
                    }
                }

                // ---- Avançado (recolhido) ----
                Rectangle { Layout.fillWidth: true; Layout.topMargin: 16; height: 1; color: Theme.divider }
                Item {
                    Layout.fillWidth: true
                    implicitHeight: 52
                    activeFocusOnTab: true
                    Accessible.role: Accessible.Button
                    Accessible.name: page.advancedOpen ? "Recolher Avançado" : "Mostrar Avançado"
                    Keys.onReturnPressed: page.advancedOpen = !page.advancedOpen
                    Keys.onSpacePressed: page.advancedOpen = !page.advancedOpen
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: page.advancedOpen = !page.advancedOpen
                    }
                    RowLayout {
                        anchors.fill: parent
                        Text {
                            Layout.fillWidth: true
                            text: "Avançado"
                            color: Theme.text
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.bodySize
                            font.weight: Font.DemiBold
                        }
                        Text {
                            text: page.advancedOpen ? "" : "Portas, tempos, registros"
                            color: Theme.text2
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.smallSize
                        }
                        Icon {
                            name: "chevron-down"
                            color: Theme.text2
                            size: 18
                            rotation: page.advancedOpen ? 180 : 0
                        }
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    visible: page.advancedOpen
                    spacing: 0

                    // Portas e tempos
                    SectionLabel { text: "PORTAS E TEMPOS"; Layout.topMargin: 4 }
                    GridLayout {
                        Layout.fillWidth: true
                        Layout.topMargin: 6
                        columns: 2
                        columnSpacing: 16
                        rowSpacing: 10
                        Repeater {
                            model: [
                                { label: "Porta de pareamento" },
                                { label: "Porta de destravar" },
                                { label: "Porta de descoberta (no celular)" },
                                { label: "Espera para conectar (s)" },
                                { label: "Tentativas de conexão" },
                                { label: "Espera pela resposta do celular (s)" }
                            ]
                            delegate: Text {
                                required property var modelData
                                required property int index
                                Layout.row: index
                                Layout.column: 0
                                Layout.fillWidth: true
                                text: modelData.label
                                color: Theme.text
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.bodySize
                            }
                        }
                        Field { id: pairingPort; Layout.row: 0; Layout.column: 1; mono: true; Layout.preferredWidth: 120; invalid: !page.portOk(text); onTextEdited: page.netDirty = true; Accessible.name: "Porta de pareamento" }
                        Field { id: unlockPort; Layout.row: 1; Layout.column: 1; mono: true; Layout.preferredWidth: 120; invalid: !page.portOk(text); onTextEdited: page.netDirty = true; Accessible.name: "Porta de destravar" }
                        Field { id: discoveryPort; Layout.row: 2; Layout.column: 1; mono: true; Layout.preferredWidth: 120; invalid: !page.portOk(text); onTextEdited: page.netDirty = true; Accessible.name: "Porta de descoberta" }
                        Field { id: connectTimeout; Layout.row: 3; Layout.column: 1; mono: true; Layout.preferredWidth: 120; invalid: !page.numOk(text, 1); onTextEdited: page.netDirty = true; Accessible.name: "Espera para conectar" }
                        Field { id: connectRetries; Layout.row: 4; Layout.column: 1; mono: true; Layout.preferredWidth: 120; invalid: !page.numOk(text, 0); onTextEdited: page.netDirty = true; Accessible.name: "Tentativas de conexão" }
                        Field { id: socketTimeout; Layout.row: 5; Layout.column: 1; mono: true; Layout.preferredWidth: 120; invalid: !page.numOk(text, 10); onTextEdited: page.netDirty = true; Accessible.name: "Espera pela resposta do celular" }
                    }
                    RowLayout {
                        Layout.fillWidth: true
                        Layout.topMargin: 10
                        visible: page.netDirty
                        Text {
                            Layout.fillWidth: true
                            text: page.netValid ? "Alterações não salvas." : "Confira os números marcados em vermelho."
                            color: page.netValid ? Theme.text2 : Theme.danger
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.smallSize
                        }
                        Btn { kind: "text"; text: "Desfazer"; onClicked: page.load() }
                        Btn { kind: "secondary"; text: "Salvar"; enabled: page.netValid; onClicked: page.saveNetwork() }
                    }

                    // Diagnóstico
                    SectionLabel { text: "DIAGNÓSTICO" }
                    SettingRow {
                        title: "Registro detalhado"
                        subtitle: "Grava mais informação nos registros, para achar problemas. Vale depois de reabrir o app."
                        Toggle {
                            Accessible.name: "Registro detalhado"
                            Component.onCompleted: checked = SettingsForm.IsDebugLoggingEnabled()
                            onToggled: SettingsForm.SetDebugLoggingEnabled(checked)
                        }
                    }
                    SettingRow {
                        title: "Registros"
                        subtitle: "O que o app e a tela de bloqueio anotaram."
                        clickable: true
                        onClicked: window.openLogs()
                    }
                    SettingRow {
                        title: "Testar destravar"
                        subtitle: page.devices.length === 0 ? "Pareie um celular primeiro." : "Pede a confirmação no celular sem travar o computador."
                        clickable: page.devices.length > 0
                        showDivider: false
                        onClicked: {
                            if (page.devices.length === 1) window.openUnlockTest(page.devices[0].id);
                            else testMenu.popup();
                        }
                        AppMenu {
                            id: testMenu
                            Instantiator {
                                model: page.devices
                                delegate: AppMenuItem {
                                    required property var modelData
                                    text: modelData.name
                                    onTriggered: window.openUnlockTest(modelData.id)
                                }
                                onObjectAdded: (index, object) => testMenu.insertItem(index, object)
                                onObjectRemoved: (index, object) => testMenu.removeItem(object)
                            }
                        }
                    }
                }

                // ---- Experimentais: item discreto no fim ----
                Rectangle { Layout.fillWidth: true; Layout.topMargin: 16; height: 1; color: Theme.divider }
                Btn {
                    Layout.topMargin: 12
                    Layout.bottomMargin: 16
                    kind: "text"
                    leftPadding: 0
                    text: "Recursos experimentais"
                    font.pixelSize: Theme.smallSize
                    onClicked: stack.push("qrc:/ui/pages/ExperimentalPage.qml")
                }
            }
        }
    }
}
