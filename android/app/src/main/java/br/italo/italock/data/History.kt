package br.italo.italock.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** O que aconteceu com o computador. */
enum class EventKind(val key: String, val label: String) {
    LOCKED("locked", "Travado"),
    UNLOCKED("unlocked", "Destravado"),
    DENIED("denied", "Pedido recusado"),
    EXPIRED("expired", "Pedido expirado");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k }
    }
}

/** De onde partiu a ação. */
enum class Origin(val key: String, val label: String) {
    APP("app", "app"),
    WIDGET("widget", "widget"),
    SHORTCUT("shortcut", "atalho do ícone"),
    TILE("tile", "atalho rápido"),
    TAG("tag", "tag NFC"),
    WATCH("watch", "relógio"),
    PC("pc", "pedido do computador"),
    NOTIFICATION("notification", "notificação"),
    AUTO("auto", "automático perto");

    /** A ação começou no celular (vale para a pré aprovação e para a notificação "Travar de novo"). */
    val fromPhone get() = this != PC && this != AUTO

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k }
    }
}

data class HistoryEvent(val at: Long, val kind: EventKind, val origin: Origin, val via: String) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("k", kind.key).put("o", origin.key).put("v", via)

    companion object {
        fun fromJson(o: JSONObject): HistoryEvent? {
            val kind = EventKind.fromKey(o.optString("k")) ?: return null
            val origin = Origin.fromKey(o.optString("o")) ?: return null
            return HistoryEvent(o.optLong("at"), kind, origin, o.optString("v"))
        }
    }
}

/**
 * Histórico por computador, guardado só no celular (preferências privadas próprias), com no máximo [MAX] eventos por
 * computador: o mais novo entra no começo e o mais velho sai. Nada de rede, nada em segundo plano.
 */
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(deviceId: String): List<HistoryEvent> = decode(prefs.getString(key(deviceId), null))

    fun add(deviceId: String, event: HistoryEvent) = synchronized(LOCK) {
        prefs.edit().putString(key(deviceId), encode(append(list(deviceId), event))).commit()
    }

    fun clear(deviceId: String) = synchronized(LOCK) { prefs.edit().remove(key(deviceId)).commit() }

    private fun key(deviceId: String) = "h_" + deviceId.lowercase()

    companion object {
        const val PREFS = "italock-historico"
        const val MAX = 20
        private val LOCK = Any()

        /** Novo evento no começo; passa de [max], o mais velho sai. */
        fun append(list: List<HistoryEvent>, event: HistoryEvent, max: Int = MAX): List<HistoryEvent> =
            (listOf(event) + list).take(max)

        fun encode(list: List<HistoryEvent>): String = JSONArray(list.map { it.toJson() }).toString()

        /** Texto ilegível ou evento desconhecido (versão futura) é ignorado sem derrubar o resto. */
        fun decode(raw: String?): List<HistoryEvent> {
            if (raw.isNullOrEmpty()) return emptyList()
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { HistoryEvent.fromJson(it) } }.take(MAX)
        }
    }
}

/**
 * Registro único de um evento: histórico do computador e, para travado/destravado, o "último uso" da lista.
 * Faz E/S (commit): fora da thread da tela.
 */
object Events {
    fun record(context: Context, deviceId: String, kind: EventKind, origin: Origin, via: String) {
        runCatching {
            HistoryStore(context).add(deviceId, HistoryEvent(System.currentTimeMillis(), kind, origin, via))
            if (kind == EventKind.LOCKED || kind == EventKind.UNLOCKED) PairingStore(context).recordEvent(deviceId, kind.label, via)
        }.onFailure { DiagLog.w("histórico: ${it.javaClass.simpleName}") }
    }
}
