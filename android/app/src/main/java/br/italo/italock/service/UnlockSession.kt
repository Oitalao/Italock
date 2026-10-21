package br.italo.italock.service

import br.italo.italock.crypto.PcbuCrypto
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import br.italo.italock.net.Frame
import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uma tentativa de desbloqueio em andamento sobre uma conexão (TCP ou Bluetooth).
 * O PC manda UNLOCK_REQUEST; guardamos a sessão, a tela pede biometria e responde por [approve] ou [deny].
 */
class UnlockSession(
    val id: String = UUID.randomUUID().toString(),
    val pc: PairedPc,
    val user: String,
    val program: String,
    val via: String,
    private val unlockToken: String,
    private val output: OutputStream,
    private val closeable: Closeable,
    /** Quando o pedido chegou (relógio de boot): a pré-aprovação só vale para pedidos recebidos depois dela. */
    val receivedAt: Long = android.os.SystemClock.elapsedRealtime(),
    /**
     * O PC pediu sozinho, sem ninguém na frente dele (tela de entrada do boot): em vez do alerta, fica um aviso
     * silencioso "Destravar X?" e a resposta é "WAITING". PC antigo não manda o campo.
     */
    val automatic: Boolean = false,
) {
    /** Quando o pedido expira (relógio de boot). O serviço ajusta pelo tempo escolhido nas Configurações. */
    @Volatile var expiresAt: Long = receivedAt + br.italo.italock.data.AppPrefs.PROMPT_TIMEOUT_DEFAULT * 1000L

    /** Só uma resposta vence (aprovar ou recusar), mesmo com botão, notificação e pré-aprovação ao mesmo tempo. */
    private val answered = AtomicBoolean(false)
    @Volatile private var closed = false
    val finished: Boolean get() = answered.get() || closed
    private val done = CountDownLatch(1)

    /**
     * Aprova. Bloqueante (cifra e manda pela rede): nunca chamar na thread da tela. Retorna true se a resposta saiu.
     * [origin]: de onde veio a aprovação (vai para o histórico).
     */
    fun approve(passwordKey: String, origin: Origin = Origin.PC): Boolean {
        if (finished) return false
        val payload = JSONObject().put("encData", PcbuCrypto.toHex(PcbuCrypto.encryptPacket(
            JSONObject().put("passwordKey", passwordKey).put("unlockToken", unlockToken).toString().toByteArray(), pc.encKey,
        ))).put("error", "")
        val sent = respond(payload)
        if (sent) runCatching { onApproved?.invoke(this, origin) }
        return sent
    }

    /**
     * Recusa. Bloqueante (rede): nunca chamar na thread da tela. Recusa do usuário ("CANCEL") e pedido que expirou
     * ([expired]) vão para o histórico; erro interno não.
     */
    fun deny(error: String = "CANCEL", origin: Origin = Origin.PC, expired: Boolean = false): Boolean {
        val sent = respond(JSONObject().put("encData", "").put("error", error))
        if (sent && (expired || error == "CANCEL")) runCatching { onDenied?.invoke(this, origin, expired) }
        return sent
    }

    /** Só a primeira resposta é enviada; true se foi esta e ela chegou ao socket. */
    private fun respond(json: JSONObject): Boolean {
        if (closed || !answered.compareAndSet(false, true)) return false
        val ok = runCatching { Frame.write(output, Frame.UNLOCK_RESPONSE, json.toString().toByteArray()) }
            .onFailure { android.util.Log.w("Italock", "resposta ao PC falhou: ${it.message}") }.isSuccess
        done.countDown()
        return ok
    }

    fun awaitFinish(timeoutMs: Long): Boolean = done.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun close() {
        closed = true
        done.countDown()
        runCatching { closeable.close() }
    }

    companion object {
        val active = ConcurrentHashMap<String, UnlockSession>()

        /** Chamado a cada destravamento aprovado, com a origem (o serviço registra o histórico e o "último uso"). */
        @Volatile var onApproved: ((UnlockSession, Origin) -> Unit)? = null

        /** Chamado quando o pedido é recusado pelo usuário ou expira (histórico). */
        @Volatile var onDenied: ((UnlockSession, Origin, Boolean) -> Unit)? = null

        /**
         * Lê o UNLOCK_REQUEST e cria a sessão. Em erro de protocolo responde o código certo e retorna null.
         */
        fun fromRequest(store: PairingStore, input: InputStream, output: OutputStream, closeable: Closeable, via: String): UnlockSession? {
            var packet = Frame.read(input)
            while (packet.id != Frame.UNLOCK_REQUEST) packet = Frame.read(input)
            val receivedAt = android.os.SystemClock.elapsedRealtime()
            val req = JSONObject(String(packet.payload))
            fun fail(code: String): UnlockSession? {
                runCatching { Frame.write(output, Frame.UNLOCK_RESPONSE, JSONObject().put("encData", "").put("error", code).toString().toByteArray()) }
                return null
            }
            val pc = store.byDeviceId(req.optString("deviceId")) ?: return fail("NOT_PAIRED")
            if (!req.optString("protoVersion").startsWith("3.")) return fail("PROTOCOL_ERROR")
            val inner = try {
                JSONObject(String(PcbuCrypto.decryptPacket(PcbuCrypto.fromHex(req.getString("encData")), pc.encKey)))
            } catch (_: PcbuCrypto.TimestampException) {
                return fail("TIME_ERROR")
            } catch (_: Exception) {
                return fail("DATA_ERROR")
            }
            return UnlockSession(
                pc = pc,
                user = inner.optString("user"),
                program = inner.optString("program"),
                via = via,
                unlockToken = inner.getString("unlockToken"),
                output = output,
                closeable = closeable,
                receivedAt = receivedAt,
                automatic = inner.optBoolean("auto", false),
            )
        }
    }
}
