package br.italo.italock.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.edit
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Tile com até 4 computadores. Só atualiza sob demanda: ao entrar no tile pede a lista ao celular,
 * e a resposta (/italock/pcs) pede um novo desenho. Sem atualização automática.
 */
class PcTileService : TileService() {
    /** Muda a cada desenho: os ids dos botões nunca se repetem entre desenhos. */
    private var layoutToken = "0"

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        runCatching { handleClick(requestParams.currentState.lastClickableId) }
        val pcs = PcStore.load(this).take(MAX)
        val root = layout(pcs, requestParams.deviceConfiguration)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(0)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return done(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        Phone.requestPcs(this)
    }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        Phone.requestPcs(this)
    }

    /**
     * Toque num botão do tile. O tile não abre tela (a ActionActivity não é exportada): a ação sai daqui mesmo, pelo
     * serviço do tile, e a resposta do celular aparece num aviso curto. Cada desenho do tile usa ids novos e o último id
     * atendido fica guardado, então um redesenho sem toque nunca repete a ação.
     */
    private fun handleClick(clickId: String) {
        if (!clickId.startsWith(CLICK_PREFIX)) return
        val parts = clickId.split(":", limit = 4)
        if (parts.size != 4) return
        val action = parts[2]
        val id = parts[3]
        if (action !in setOf("lock", "unlock", "wake") || id.isEmpty()) return
        val prefs = getSharedPreferences(TILE_PREFS, MODE_PRIVATE)
        if (prefs.getString(K_LAST_CLICK, null) == clickId) return
        prefs.edit { putString(K_LAST_CLICK, clickId) }
        val app = applicationContext
        val name = PcStore.load(this).firstOrNull { it.id == id }?.name.orEmpty()
        toast(app, listOf(Pc.actionLabel(action), name).filter { it.isNotBlank() }.joinToString(" ") + ": enviando")
        Phone.scope.launch {
            val sent = runCatching {
                Phone.send(app, Proto.ACTION, JSONObject().put("id", id).put("action", action))
            }.getOrDefault(false)
            if (!sent) toast(app, "Celular não encontrado. Abra o Ítalock no celular.")
        }
    }

    private fun toast(ctx: android.content.Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun layout(pcs: List<Pc>, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        if (pcs.isEmpty()) {
            val open = ModifiersBuilders.Modifiers.Builder()
                .setClickable(clickable("abrir", activity(MainActivity::class.java.name, emptyMap())))
                .build()
            return LayoutElementBuilders.Box.Builder()
                .setWidth(expand()).setHeight(expand())
                .setModifiers(open)
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dp(device.screenWidthDp * 0.75f))
                        .addContent(
                            Text.Builder(this, "Abra o Ítalock no celular")
                                .setTypography(Typography.TYPOGRAPHY_BODY1)
                                .setColor(argb(TEXT))
                                .setMaxLines(3)
                                .build(),
                        )
                        .build(),
                )
                .build()
        }
        layoutToken = java.lang.Long.toString(System.currentTimeMillis(), 36)
        // Linhas mais estreitas quando há muitas, para caber na tela redonda.
        val rowWidth = device.screenWidthDp * if (pcs.size >= 4) 0.74f else 0.82f
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        pcs.forEachIndexed { i, pc ->
            if (i > 0) column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
            column.addContent(row(pc, rowWidth, device))
        }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    private fun row(pc: Pc, width: Float, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val texts = LayoutElementBuilders.Column.Builder()
            .setWidth(weight(1f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(
                Text.Builder(this, pc.name)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(TEXT))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .build(),
            )
            .addContent(
                Text.Builder(this, pc.stateLabel)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(TEXT2))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .build(),
            )
            .build()
        val primary = pc.action == "unlock"
        val colors = if (primary) ChipColors(ORANGE, ON_ORANGE) else ChipColors(SURFACE, TEXT)
        val load = ActionBuilders.LoadAction.Builder().build()
        val chip = CompactChip.Builder(
            this,
            pc.actionLabel,
            clickable("$CLICK_PREFIX${layoutToken}:${pc.action}:${pc.id}", load),
            device,
        ).setChipColors(colors).build()
        return LayoutElementBuilders.Row.Builder()
            .setWidth(dp(width))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(texts)
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
            .addContent(chip)
            .build()
    }

    private fun activity(className: String, extras: Map<String, String>): ActionBuilders.LaunchAction {
        val a = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(className)
        extras.forEach { (k, v) ->
            a.addKeyToExtraMapping(k, ActionBuilders.AndroidStringExtra.Builder().setValue(v).build())
        }
        return ActionBuilders.LaunchAction.Builder().setAndroidActivity(a.build()).build()
    }

    private fun clickable(id: String, action: ActionBuilders.Action) =
        ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(action).build()

    private fun <T : Any> done(value: T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { it.set(value); "italock-tile" }

    private companion object {
        const val MAX = 4
        const val CLICK_PREFIX = "act:"
        const val TILE_PREFS = "tile"
        const val K_LAST_CLICK = "last_click"
        const val RES_VERSION = "1"
        const val TEXT = 0xFFE6E8EE.toInt()
        const val TEXT2 = 0xFF9AA3B5.toInt()
        const val SURFACE = 0xFF171D2E.toInt()
        const val ORANGE = 0xFFEC7000.toInt()
        const val ON_ORANGE = 0xFF1A1206.toInt()
    }
}
