import QtQuick

// Indicador de "esperando": arco girando, discreto.
Item {
    id: root
    property int size: 20
    property color color: Theme.brandOn
    property bool running: true
    width: size
    height: size
    visible: running

    Canvas {
        id: canvas
        anchors.fill: parent
        property color c: root.color
        onCChanged: requestPaint()
        onPaint: {
            let ctx = getContext("2d");
            ctx.reset();
            ctx.lineWidth = Math.max(2, root.size / 10);
            ctx.lineCap = "round";
            ctx.strokeStyle = Qt.rgba(c.r, c.g, c.b, 0.2);
            ctx.beginPath();
            ctx.arc(width / 2, height / 2, width / 2 - ctx.lineWidth, 0, Math.PI * 2);
            ctx.stroke();
            ctx.strokeStyle = c;
            ctx.beginPath();
            ctx.arc(width / 2, height / 2, width / 2 - ctx.lineWidth, 0, Math.PI * 0.6);
            ctx.stroke();
        }
        RotationAnimator on rotation {
            from: 0
            to: 360
            duration: 900
            loops: Animation.Infinite
            running: root.running && root.visible
        }
    }
}
