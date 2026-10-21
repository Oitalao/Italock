import QtQuick

// Ícones de linha (traço 1,75 px, cantos arredondados), desenhados aqui mesmo — um conjunto só.
Image {
    id: root
    property string name: ""
    property color color: Theme.text
    property int size: 20

    width: size
    height: size
    sourceSize: Qt.size(size * 2, size * 2)
    fillMode: Image.PreserveAspectFit
    smooth: true

    readonly property var paths: ({
        "back": '<path d="M19 12H5M11 6l-6 6 6 6"/>',
        "chevron-right": '<path d="M9 6l6 6-6 6"/>',
        "chevron-down": '<path d="M6 9l6 6 6-6"/>',
        "check": '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
        "close": '<path d="M6 6l12 12M18 6L6 18"/>',
        "lock": '<rect x="5" y="10.5" width="14" height="10" rx="2.2"/><path d="M8.2 10.5V7.8a3.8 3.8 0 0 1 7.6 0v2.7"/><path d="M12 14.5v2"/>',
        "lock-open": '<rect x="5" y="10.5" width="14" height="10" rx="2.2"/><path d="M8.2 10.5V7.8a3.8 3.8 0 0 1 7.2-1.7"/><path d="M12 14.5v2"/>',
        "tune": '<path d="M4 7h9M18 7h2M4 17h3M12 17h8"/><circle cx="15.5" cy="7" r="2.2"/><circle cx="9.5" cy="17" r="2.2"/>',
        "more": '<circle cx="5.5" cy="12" r="1.3" fill="COR"/><circle cx="12" cy="12" r="1.3" fill="COR"/><circle cx="18.5" cy="12" r="1.3" fill="COR"/>',
        "copy": '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h8"/>',
        "warning": '<path d="M12 4.2l8.6 15H3.4z"/><path d="M12 10v4.2"/><path d="M12 17v.01"/>',
        "external": '<path d="M14 5h5v5M19 5l-8 8"/><path d="M17 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h4"/>',
        "wifi": '<path d="M3 9.2a13.5 13.5 0 0 1 18 0M6 12.6a9 9 0 0 1 12 0M9 16a4.6 4.6 0 0 1 6 0"/><path d="M12 19.3v.01"/>',
        "bluetooth": '<path d="M7 7.5l10 9-5 4.5V3l5 4.5-10 9"/>',
        "globe": '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.3 3.6 5.1 3.6 8.5s-1.2 6.2-3.6 8.5c-2.4-2.3-3.6-5.1-3.6-8.5s1.2-6.2 3.6-8.5z"/>',
        "phone": '<rect x="7" y="3" width="10" height="18" rx="2.2"/><path d="M11 17.5h2"/>'
    })

    source: {
        let c = String(root.color);
        if (c.length === 9) c = "#" + c.substring(3); // #aarrggbb -> #rrggbb
        let body = (paths[name] || "").replace(/COR/g, c);
        let svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="' + c +
                  '" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">' + body + '</svg>';
        return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
    }
}
