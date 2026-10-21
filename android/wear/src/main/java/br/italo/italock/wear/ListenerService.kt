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
                    val text = resultText(r)
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

fun resultText(r: ActionResult): String = when {
    r.confirmOnPhone -> "Confirme no celular"
    r.message.isNotBlank() -> r.message
    r.ok -> "Feito"
    else -> "Não deu certo"
}
