package br.italo.italock.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import br.italo.italock.data.PairingStore
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.LockClient.LockStatus
import kotlin.concurrent.thread

/**
 * Cadeado mostrado no widget. Consulta o computador SÓ quando alguém toca em Atualizar no widget ou logo depois de uma
 * ação (travar, destravar): nada periódico, nada em segundo plano sem motivo. Sem widget desse computador, nem consulta.
 */
object WidgetStatus {
    /** Consulta que não terminou nesse prazo (processo encerrado no meio) deixa de aparecer como "Consultando". */
    private const val QUERY_STALE_MS = 30_000L

    /** [state] null = nunca consultado; [querying] = consulta em andamento agora. */
    data class Shown(val state: String?, val at: Long, val querying: Boolean)

    fun read(context: Context, deviceId: String): Shown {
        val p = prefs(context)
        val since = p.getLong(queryKey(deviceId), 0)
        val querying = since > 0 && System.currentTimeMillis() - since in 0..QUERY_STALE_MS
        return Shown(p.getString(stateKey(deviceId), null), p.getLong(timeKey(deviceId), 0), querying)
    }

    /** Algum widget na tela mostra este computador? */
    fun hasWidgetFor(context: Context, deviceId: String): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        val ids = manager.getAppWidgetIds(ComponentName(context, PcWidgetProvider::class.java))
        val p = prefs(context)
        ids.any { p.getString("w$it", null)?.equals(deviceId, ignoreCase = true) == true }
    }.getOrDefault(false)

    /** Consulta agora (bloqueante: fora da thread da tela) e redesenha os widgets. */
    fun refreshNow(context: Context, deviceId: String) {
        val app = context.applicationContext
        val pc = PairingStore(app).byDeviceId(deviceId) ?: return
        prefs(app).edit().putLong(queryKey(deviceId), System.currentTimeMillis()).commit()
        PcWidgetProvider.updateAll(app)
        val r = runCatching { LockClient.status(pc) }.getOrNull()
        prefs(app).edit().putString(stateKey(deviceId), stateOf(r?.status)).putLong(timeKey(deviceId), System.currentTimeMillis())
            .remove(queryKey(deviceId)).commit()
        PcWidgetProvider.updateAll(app)
    }

    /** Depois de uma ação: confere o cadeado passado [delayMs], só se houver widget deste computador. */
    fun refreshLater(context: Context, deviceId: String, delayMs: Long) {
        val app = context.applicationContext
        if (!hasWidgetFor(app, deviceId)) return
        thread(isDaemon = true, name = "widget-status") {
            runCatching { Thread.sleep(delayMs); refreshNow(app, deviceId) }
        }
    }

    fun forget(context: Context, deviceId: String) {
        prefs(context).edit().remove(stateKey(deviceId)).remove(timeKey(deviceId)).remove(queryKey(deviceId)).apply()
    }

    fun stateOf(s: LockStatus?): String = when (s) {
        LockStatus.LOCKED -> "locked"
        LockStatus.UNLOCKED -> "unlocked"
        LockStatus.CLOCK_OFF -> "clock"
        LockStatus.NOT_PAIRED -> "not_paired"
        LockStatus.ERROR -> "error"
        else -> "no_answer"
    }

    /** Linha curta do widget: "Travado · 20:10", "Consultando", "Toque em atualizar". */
    fun label(shown: Shown): String {
        if (shown.querying) return "Consultando"
        if (shown.state == null) return "Toque em atualizar para ver o cadeado"
        val what = when (shown.state) {
            "locked" -> "Travado"
            "unlocked" -> "Destravado"
            "clock" -> "Relógio fora de hora"
            "not_paired" -> "Não reconhece este celular"
            "error" -> "Respondeu com erro"
            else -> "Sem resposta"
        }
        return if (shown.at > 0) "$what · ${timeText(shown.at)}" else what
    }

    /** Hora da consulta: "20:10" hoje; "28/09 20:10" em outro dia. */
    private fun timeText(at: Long): String {
        val br = java.util.Locale.forLanguageTag("pt-BR")
        val then = java.util.Calendar.getInstance().apply { timeInMillis = at }
        val now = java.util.Calendar.getInstance()
        val sameDay = then.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
            then.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
        return java.text.SimpleDateFormat(if (sameDay) "HH:mm" else "dd/MM HH:mm", br).format(then.time)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PcWidgetProvider.PREFS, Context.MODE_PRIVATE)
    private fun stateKey(id: String) = "s_" + id.lowercase()
    private fun timeKey(id: String) = "t_" + id.lowercase()
    private fun queryKey(id: String) = "q_" + id.lowercase()
}
