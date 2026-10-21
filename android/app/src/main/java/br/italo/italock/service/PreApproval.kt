package br.italo.italock.service

import android.os.SystemClock
import br.italo.italock.data.Origin
import java.util.concurrent.ConcurrentHashMap

/**
 * Botão "Destravar" do celular: depois da confirmação (ou sem ela, no modo "sem confirmação"), a senha do PC fica só
 * na memória por pouco tempo, para aprovar o pedido que o próprio "wake" vai provocar.
 *
 * O protocolo não deixa amarrar o pedido ao wake: o wake vai pela conexão de travar (LOCK_REQUEST) e não devolve
 * nada; o pedido de destravar chega depois, por outra conexão, com um unlockToken novo gerado pelo PC. Sem mudar o
 * programa do PC, a regra é estreita:
 *  - [grant] RECUSA se já havia pedido aberto deste PC (quem chamou mostra esse pedido, que pede confirmação normal);
 *  - vale só o PRIMEIRO pedido deste PC que chegar DEPOIS do grant (recebido antes? não conta e não gasta a vez);
 *  - e só dentro de [WINDOW_MS] — contados do grant e renovados uma única vez quando o PC confirma que recebeu o wake
 *    ([wakeDelivered]), porque pelo Tailscale o wake sozinho pode levar alguns segundos.
 * Se chamar o PC falhar, quem chamou deve [revoke] na hora, com o token do seu grant.
 */
object PreApproval {
    private data class Entry(
        val passwordKey: String, val grantedAt: Long, val expiresAt: Long, val renewed: Boolean = false, val origin: Origin = Origin.APP,
    )

    /** A senha levada por um pedido e de onde partiu o toque em Destravar (vai para o histórico). */
    data class Granted(val passwordKey: String, val origin: Origin)
    private val entries = ConcurrentHashMap<String, Entry>()
    const val WINDOW_MS = 10_000L

    /** Relógio (trocável nos testes). */
    @Volatile internal var clock: () -> Long = { SystemClock.elapsedRealtime() }
    private fun now() = clock()

    /** Pedido aberto deste PC, se houver (quem chamou deve mostrá-lo em vez de pré-aprovar). */
    fun openSession(deviceId: String): UnlockSession? =
        UnlockSession.active.values.firstOrNull { it.pc.deviceId.equals(deviceId, true) && !it.finished }

    /**
     * Pré aprova. Retorna um token (o instante do grant) para [revoke] desfazer só esta entrada, ou null se havia
     * pedido aberto deste PC (nada foi pré aprovado).
     */
    fun grant(deviceId: String, passwordKey: String, origin: Origin = Origin.APP): Long? {
        val id = deviceId.lowercase()
        if (openSession(deviceId) != null) { entries.remove(id); return null }
        var t = now()
        // Dois grants no mesmo milissegundo teriam o mesmo token: o segundo anda um passo.
        entries[id]?.let { if (it.grantedAt >= t) t = it.grantedAt + 1 }
        entries[id] = Entry(passwordKey, grantedAt = t, expiresAt = t + WINDOW_MS, origin = origin)
        return t
    }

    /** O PC confirmou que recebeu o wake: o prazo recomeça (uma vez só). */
    fun wakeDelivered(deviceId: String) {
        val id = deviceId.lowercase()
        val e = entries[id] ?: return
        if (!e.renewed) entries.replace(id, e, e.copy(expiresAt = now() + WINDOW_MS, renewed = true))
    }

    /**
     * Chamado para cada pedido que chega. Só o primeiro pedido recebido depois do grant leva a senha (e gasta a vez);
     * pedido recebido antes do grant nunca é aprovado por aqui.
     */
    fun take(session: UnlockSession): Granted? {
        val id = session.pc.deviceId.lowercase()
        val e = entries[id] ?: return null
        if (session.receivedAt < e.grantedAt) return null
        if (!entries.remove(id, e)) return null
        return if (now() <= e.expiresAt) Granted(e.passwordKey, e.origin) else null
    }

    /**
     * Desfaz a pré aprovação (o PC não foi alcançado ou recusou o pedido). Com [token], só se a entrada ainda for a
     * mesma daquele grant: um grant mais novo (outro toque em Destravar) não é apagado por um pedido antigo.
     */
    fun revoke(deviceId: String, token: Long? = null) {
        val id = deviceId.lowercase()
        if (token == null) { entries.remove(id); return }
        val e = entries[id] ?: return
        if (e.grantedAt == token) entries.remove(id, e)
    }

    /** Há pré aprovação valendo para este PC? (testes e diagnóstico) */
    fun pending(deviceId: String): Boolean = entries[deviceId.lowercase()]?.let { now() <= it.expiresAt } ?: false
}
