package br.italo.italock.wear

import android.app.KeyguardManager
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import androidx.core.content.edit
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** Caminhos e capabilities combinados com o app do celular. Não mudar os nomes. */
object Proto {
    const val CAP_PHONE = "italock_phone"
    const val PROMPT = "/italock/prompt"
    const val PROMPT_END = "/italock/prompt/end"
    const val PCS = "/italock/pcs"
    const val RESULT = "/italock/result"
    const val ANSWER = "/italock/answer"
    const val PCS_GET = "/italock/pcs/get"
    const val ACTION = "/italock/action"
}

data class Pc(val id: String, val name: String, val state: String, val wake: Boolean) {
    /** Estado em texto curto, no idioma escolhido no celular. */
    fun stateLabel(ctx: Context): String = WearLang.s(ctx, when (state) {
        "locked" -> R.string.state_locked
        "unlocked" -> R.string.state_unlocked
        "no_answer" -> R.string.state_no_answer
        "clock" -> R.string.state_clock
        // "unknown": o computador respondeu com erro ou a consulta não terminou a tempo.
        else -> R.string.state_unknown
    })

    /** A ação certa para o estado: travado destrava, destravado trava, desligado liga, senão trava. */
    val action: String
        get() = when {
            state == "locked" -> "unlock"
            state == "unlocked" -> "lock"
            state == "no_answer" && wake -> "wake"
            else -> "lock"
        }

    fun actionLabel(ctx: Context): String = actionLabel(ctx, action)

    companion object {
        fun actionLabel(ctx: Context, action: String): String = WearLang.s(ctx, when (action) {
            "unlock" -> R.string.action_unlock
            "wake" -> R.string.action_wake
            else -> R.string.action_lock
        })
    }
}

data class Prompt(val session: String, val pc: String, val user: String, val expiresAt: Long)

data class ActionResult(
    val id: String, val action: String, val ok: Boolean, val message: String, val confirmOnPhone: Boolean,
)

/** Lista de PCs recebida do celular, guardada para o tile e o app. */
object PcStore {
    private const val PREFS = "pcs"
    private val state = MutableStateFlow<List<Pc>?>(null)
    val pcs: StateFlow<List<Pc>?> get() = state

    fun load(ctx: Context): List<Pc> {
        state.value?.let { return it }
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", null)
        val list = raw?.let { runCatching { parse(JSONObject(it)) }.getOrNull() } ?: emptyList()
        state.value = list
        return list
    }

    fun save(ctx: Context, json: JSONObject) {
        val list = parse(json)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("json", json.toString()) }
        state.value = list
    }

    private fun parse(json: JSONObject): List<Pc> {
        val arr = json.optJSONArray("pcs") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) null
            else Pc(id, o.optString("name", id), o.optString("state", "unknown"), o.optBoolean("wake", false))
        }
    }
}

/** Avisos entre o serviço de mensagens e as telas abertas (mesmo processo). */
object Bus {
    val promptEnded = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val results = MutableSharedFlow<ActionResult>(extraBufferCapacity = 8)
    /** O celular mandou outro idioma: a lista aberta se recria. */
    val langChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/** Conversa com o celular pela Data Layer. */
object Phone {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun nodeId(ctx: Context): String? {
        val app = ctx.applicationContext
        val cap = runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(Proto.CAP_PHONE, CapabilityClient.FILTER_REACHABLE).await()
        }.getOrNull()
        val byCap = cap?.nodes?.let { n -> n.firstOrNull { it.isNearby } ?: n.firstOrNull() }
        if (byCap != null) return byCap.id
        val nodes = runCatching { Wearable.getNodeClient(app).connectedNodes.await() }.getOrNull()
        return nodes?.let { n -> n.firstOrNull { it.isNearby } ?: n.firstOrNull() }?.id
    }

    /** Devolve false se não achou o celular ou a mensagem não saiu. */
    suspend fun send(ctx: Context, path: String, body: JSONObject): Boolean {
        val node = nodeId(ctx) ?: return false
        return runCatching {
            Wearable.getMessageClient(ctx.applicationContext)
                .sendMessage(node, path, body.toString().toByteArray(Charsets.UTF_8)).await()
        }.isSuccess
    }

    /** Sem bloqueio de tela no relógio, ou com ele travado, nunca aprova: manda approve=false, secure=false. */
    suspend fun sendAnswer(ctx: Context, session: String, approve: Boolean): Boolean {
        val secure = isWatchSecure(ctx)
        val body = JSONObject().put("session", session).put("approve", approve && secure).put("secure", secure)
        return runCatching { send(ctx, Proto.ANSWER, body) }.getOrDefault(false)
    }

    fun answer(ctx: Context, session: String, approve: Boolean) {
        val app = ctx.applicationContext
        scope.launchSafe { sendAnswer(app, session, approve) }
    }

    fun requestPcs(ctx: Context) {
        val app = ctx.applicationContext
        scope.launchSafe { send(app, Proto.PCS_GET, JSONObject()) }
    }
}

private fun CoroutineScope.launchSafe(block: suspend () -> Unit) =
    launch { runCatching { block() } }

/** O relógio tem bloqueio de tela configurado (sem ele, nunca aprova). */
fun watchHasLock(ctx: Context): Boolean =
    ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Pode aprovar AGORA: tem bloqueio de tela e está destravado. O pedido aparece por cima do bloqueio, mas aprovar com o
 * relógio travado deixaria qualquer um que pegasse o relógio destravar o computador.
 */
fun isWatchSecure(ctx: Context): Boolean {
    val km = ctx.getSystemService(KeyguardManager::class.java) ?: return false
    return km.isDeviceSecure && !km.isKeyguardLocked
}

/** Vibração curta dupla para o pedido chamar atenção no pulso. */
fun vibrateDouble(ctx: Context) {
    val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Vibrator::class.java)
    }
    if (vib?.hasVibrator() != true) return
    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120), -1))
}
