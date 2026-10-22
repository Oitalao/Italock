package br.italo.italock.wear

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** Recebe as mensagens do celular. Não fica rodando: o Google Play Services acorda o serviço a cada mensagem. */
class ListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val json = runCatching { JSONObject(String(event.data, Charsets.UTF_8)) }.getOrNull() ?: JSONObject()
        // Idioma escolhido no celular: guardado antes de montar qualquer aviso ou tile.
        val lang = json.optString("lang")
        if (lang.isNotEmpty() && WearLang.save(this, lang)) {
            Bus.langChanged.tryEmit(Unit)
            if (event.path != Proto.PCS) TileService.getUpdater(this).requestUpdate(PcTileService::class.java)
        }
        when (event.path) {
            Proto.PROMPT -> {
                val p = Prompt(
                    session = json.optString("session"),
                    pc = json.optString("pc"),
                    user = json.optString("user"),
                    expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + 60_000),
                )
                if (p.session.isNotEmpty() && p.expiresAt > System.currentTimeMillis()) PromptNotifier.show(this, p)
            }
            Proto.PROMPT_END -> {
                val session = json.optString("session")
                PromptNotifier.cancel(this, session)
                Bus.promptEnded.tryEmit(session)
            }
            Proto.PCS -> {
                PcStore.save(this, json)
                TileService.getUpdater(this).requestUpdate(PcTileService::class.java)
            }
            Proto.RESULT -> {
                val r = ActionResult(
                    id = json.optString("id"),
                    action = json.optString("action"),
                    ok = json.optBoolean("ok"),
                    message = json.optString("message"),
                    confirmOnPhone = json.optBoolean("confirmOnPhone"),
                )
                if (Bus.results.subscriptionCount.value > 0) {
                    Bus.results.tryEmit(r)
                } else {
                    val text = resultText(this, r)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                    }
                }
                // O estado do PC mudou: pede a lista nova para o tile.
                Phone.requestPcs(this)
            }
        }
    }
}

fun resultText(ctx: android.content.Context, r: ActionResult): String = when {
    r.confirmOnPhone -> WearLang.s(ctx, R.string.result_confirm_on_phone)
    r.message.isNotBlank() -> r.message
    r.ok -> WearLang.s(ctx, R.string.result_done)
    else -> WearLang.s(ctx, R.string.result_failed)
}
