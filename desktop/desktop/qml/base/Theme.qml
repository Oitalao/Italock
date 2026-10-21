pragma Singleton
import QtQuick

import PCBioUnlock

// Cores, fonte e medidas do Ítalock (docs/design.md). Escuro é o padrão de fábrica; a pessoa escolhe em
// Configurações, Aparência (Escuro, Claro ou Igual ao sistema). O C++ aplica a escolha no esquema de cores do app,
// então aqui basta seguir o esquema. O claro é suave: nada de branco nem preto puros.
QtObject {
    // Escolha da pessoa (MainWindow.appearance); o esquema do sistema só vale em "Igual ao sistema".
    // 2 = Qt::ColorScheme::Dark
    readonly property bool dark: MainWindow.appearance === "escuro" ||
                                 (MainWindow.appearance === "sistema" && Application.styleHints.colorScheme === 2)

    readonly property color bg: dark ? "#0E1320" : "#E3E6EB"
    readonly property color surface: dark ? "#171D2E" : "#ECEEF1"
    readonly property color elevated: dark ? "#1C2336" : "#F1F2F4"
    readonly property color hover: dark ? "#1F2740" : "#E0E3E8"
    readonly property color pressed: dark ? "#262E44" : "#D5D9E0"
    readonly property color text: dark ? "#E6E8EE" : "#1D2330"
    readonly property color text2: dark ? "#9AA3B5" : "#4F5868"
    readonly property color divider: dark ? "#262E44" : "#CBD0D8"
    readonly property color field: dark ? "#10162A" : "#F1F2F4"
    readonly property color fieldBorder: dark ? "#34405E" : "#AEB5C1"

    // Marca: azul para destaque/estado ligado, laranja para a UMA ação principal da tela.
    readonly property color brand: dark ? "#003399" : "#1F3F99"
    readonly property color brandOn: dark ? "#4C7BF0" : "#1F3F99"   // interruptor ligado, foco
    readonly property color link: dark ? "#7F9CF5" : "#1F3F99"
    readonly property color accent: dark ? "#EC7000" : "#D86A00"      // botão cheio
    readonly property color accentHover: dark ? "#F28A2E" : "#C96200"
    readonly property color accentPressed: dark ? "#D46400" : "#B85600"
    readonly property color accentText: dark ? "#EC7000" : "#B85600"  // laranja em texto e ícone
    readonly property color textOnAccent: dark ? "#111827" : "#FFF8F0"
    readonly property color danger: dark ? "#EF6B6B" : "#B3261E"
    readonly property color textOnDanger: "#FFFFFF"
    readonly property color dangerFill: dark ? "#C62828" : "#B3261E"

    // Faixa âmbar dos recursos experimentais
    readonly property color amberBg: dark ? "#3A2A08" : "#ECE0BD"
    readonly property color amberBorder: dark ? "#6B4E12" : "#D2BC7C"
    readonly property color amberText: dark ? "#F7D48A" : "#5C4300"

    readonly property string fontFamily: {
        if (Qt.platform.os === "windows") {
            let fams = Qt.fontFamilies();
            return fams.indexOf("Segoe UI Variable Text") >= 0 ? "Segoe UI Variable Text" : "Segoe UI";
        }
        return Application.font.family;
    }
    readonly property string monoFamily: {
        let fams = Qt.fontFamilies();
        for (let f of ["Cascadia Mono", "Consolas", "DejaVu Sans Mono", "Ubuntu Mono", "Liberation Mono"])
            if (fams.indexOf(f) >= 0) return f;
        return "monospace";
    }

    // Três tamanhos por tela, no máximo
    readonly property int titleSize: 20
    readonly property int bodySize: 14
    readonly property int smallSize: 12

    readonly property int radius: 12
    readonly property int buttonRadius: 10
    readonly property int gutter: 24
}
