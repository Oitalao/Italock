package br.italo.italock.lock

import br.italo.italock.crypto.PcbuCrypto
import br.italo.italock.data.PairedPc
import br.italo.italock.net.Frame
import br.italo.italock.net.NetInfo
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Extensão do Ítalock: pede ao agente do PC para travar, acordar a tela ou dizer se está travada.
 * Frame LOCK_REQUEST com {"deviceId", "wantSealed":true, "encData"=hex(encryptPacket({"action", "ips", "wantSealed", "nonce"}, encKey))}.
 *
 * Resposta cifrada (agente novo): frame LOCK_RESPONSE com {"sealed":"HEX(encryptPacket(json, encKey))"}, em que json é
 * a resposta normal ({"error":"", "locked":...}) e, de preferência, repete o "nonce" do pedido. Só ela é "verificada":
 * ninguém sem a chave consegue forjar o cadeado. Agente antigo responde em claro, sem "sealed": o estado aparece, mas
 * marcado como não verificado, e não serve para o modo sem confirmação.
 *
 * Tenta todos os endereços do PC AO MESMO TEMPO (rede de casa, cabo, Tailscale) e fica com o primeiro que responder.
 */
object LockClient {
    /** Endereço que respondeu por último a cada PC: a consulta de estado tenta só ele antes de chamar todos. */
    private val lastGoodHost = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Chamado quando um PC responde cifrado pela primeira vez (o app grava para recusar respostas em claro depois). */
    @Volatile var onSealedSeen: ((PairedPc) -> Unit)? = null

    /** Estado da tela do PC, para o cadeado da lista. */
    enum class LockStatus {
        LOCKED, UNLOCKED,
        /** Desligado, fora de alcance ou caminho não permitido. */
        NO_ANSWER,
        /** O PC respondeu TIME_ERROR: o relógio do celular (ou o do PC) está fora de hora. */
        CLOCK_OFF,
        /** O PC respondeu, mas não reconhece este celular (NOT_PAIRED/DATA_ERROR): parear de novo. */
        NOT_PAIRED,
        /** O PC respondeu com outro erro (ou uma resposta que não confere). */
        ERROR,
    }

    /** Estado + se veio cifrado (verificado) + a mensagem de erro, quando houver. */
    data class StatusResult(val status: LockStatus, val verified: Boolean, val detail: String = "")

    fun status(pc: PairedPc): StatusResult = status(pc, pc.hosts)

    fun status(pc: PairedPc, hosts: List<String>): StatusResult {
        val preferred = lastGoodHost[pc.deviceId]?.takeIf { it in hosts }
        var reply = preferred?.let { request(pc, listOf(it), "status", connectTimeoutMs = 2500, readTimeoutMs = 4000) }
        if (reply?.json == null) reply = request(pc, hosts, "status", connectTimeoutMs = 2500, readTimeoutMs = 4000)
        val resp = reply.json ?: return StatusResult(LockStatus.NO_ANSWER, false)
        lastGoodHost[pc.deviceId] = reply.host
        return interpretStatus(resp, reply.verified, pc.displayName)
    }

    /** Traduz a resposta de "status" (já aberta). Separado para os testes. */
    fun interpretStatus(resp: JSONObject, verified: Boolean, name: String): StatusResult {
        val err = resp.optString("error")
        return when {
            err == "TIME_ERROR" -> StatusResult(LockStatus.CLOCK_OFF, verified)
            err == "NOT_PAIRED" || err == "DATA_ERROR" -> StatusResult(LockStatus.NOT_PAIRED, verified, describeError(name, "status", err, true))
            err.isNotEmpty() -> StatusResult(LockStatus.ERROR, verified, describeError(name, "status", err, true))
            !resp.has("locked") -> StatusResult(LockStatus.ERROR, verified, describeError(name, "status", "STATE_UNKNOWN", true))
            resp.optBoolean("locked") -> StatusResult(LockStatus.LOCKED, verified)
            else -> StatusResult(LockStatus.UNLOCKED, verified)
        }
    }

    /**
     * Resultado simples para a interface: ok e o caminho usado (ex.: "Tailscale"), ou a falha.
     * [reached] = o PC respondeu (a falha é um código do agente, não de rede).
     */
    data class Outcome(val ok: Boolean, val via: String, val error: String, val reached: Boolean = true, val action: String = "") {
        /** Frase em português para mostrar ao usuário. */
        fun message(pc: PairedPc): String = if (ok) when (action) {
            "lock" -> "${pc.displayName} travado"
            "wake" -> "Destravando ${pc.displayName}"
            else -> "Pronto"
        } else describeError(pc.displayName, action, error, reached)
    }

    /** Códigos que valem uma nova tentativa automática (uma vez só). */
    private val RETRYABLE = setOf("REPLAY", "BUSY")

    fun lockOutcome(pc: PairedPc): Outcome = outcome(pc, pc.hosts, "lock")
    fun wakeOutcome(pc: PairedPc): Outcome = outcome(pc, pc.hosts, "wake")

    private fun outcome(pc: PairedPc, hosts: List<String>, action: String): Outcome {
        if (hosts.isEmpty()) return Outcome(false, "", NO_PATH, reached = false, action = action)
        var retried = false
        while (true) {
            val reply = request(pc, hosts, action)
            val resp = reply.json ?: return Outcome(false, "", reply.failure, reached = false, action = action)
            val err = resp.optString("error")
            if (err in RETRYABLE && !retried) {
                // Pacote repetido ou PC ocupado: um pacote novo (outro horário/IV) depois de uma pausa costuma passar.
                retried = true
                Thread.sleep(800)
                continue
            }
            return Outcome(err.isEmpty(), NetInfo.viaLabel(reply.host), err, reached = true, action = action)
        }
    }

    /** Falha "nenhum caminho permitido" (todos desligados nos Detalhes). */
    const val NO_PATH = "NO_PATH"

    /** Traduz os códigos do agente do PC (ver o topo do main.cpp do italock-agent) em mensagens claras. */
    fun describeError(name: String, action: String, error: String, reached: Boolean): String {
        if (error == NO_PATH) return "Nenhum caminho liberado para $name. Ligue rede local ou Tailscale nos detalhes do computador."
        if (!reached) return "$name não respondeu. Ele está ligado e na mesma rede (ou no Tailscale)?"
        return when (error) {
            "NOT_PAIRED" -> "$name não reconhece este celular. Remova o computador e pareie de novo."
            "TIME_ERROR" -> "Relógio do celular fora de hora: a diferença para $name passa de 1 minuto. Ligue a data e hora automáticas nos dois."
            "DATA_ERROR" -> "$name não conseguiu ler o pedido (chave diferente). Remova o computador e pareie de novo."
            "BAD_ACTION" -> "O Ítalock de $name está desatualizado para esse comando. Atualize o programa no computador."
            "BAD_PACKET" -> "$name não entendeu o pedido. Atualize o Ítalock no computador."
            "STATE_UNKNOWN" -> "$name não conseguiu dizer se a tela está travada."
            "NOT_WAITING" -> "O computador não está na tela de bloqueio."
            "LOCK_FAILED" -> "$name recebeu o pedido, mas não conseguiu travar a tela."
            "REPLAY" -> "$name recusou o pedido por parecer repetido. Tente de novo."
            "BUSY" -> "$name está ocupado atendendo outras conexões. Tente de novo em instantes."
            "NOT_LOGGED_IN" -> "Ninguém está conectado em $name com a conta deste pareamento. Entre na conta e tente de novo."
            "LOCK_DISABLED" -> "$name não aceita pedidos de travar pelo celular. Ligue essa opção no Ítalock do computador."
            "PATH_DISABLED" -> "$name não aceita pedidos por este caminho (rede local ou Tailscale). Veja as opções no Ítalock do computador."
            "UNSUPPORTED" -> "$name não tem esse recurso neste sistema."
            "NOT_ALLOWED" -> "Esta conta do computador não pode usar este celular."
            "LAUNCH_FAILED" -> "$name não conseguiu abrir a janela do Ítalock. Deixe alguém conectado nele e tente de novo."
            "BAD_NONCE", "NOT_SEALED" -> "A resposta não confere com o pedido. Se continuar, remova o computador e pareie de novo."
            "BAD_CMD" -> "$name não entendeu o pedido. Atualize o Ítalock no computador."
            BAD_SEAL -> "A resposta de $name não confere com a chave do pareamento. Se continuar, remova o computador e pareie de novo."
            UNSEALED -> "$name respondeu sem a proteção de sempre. Por segurança a resposta foi ignorada."
            else -> "$name recusou o pedido."
        }
    }

    /** Resposta cifrada que não abre (chave errada ou forjada). */
    const val BAD_SEAL = "BAD_SEAL"
    /** Resposta em claro de um PC que já respondeu cifrado antes (ou com "sealed" inválido). */
    const val UNSEALED = "UNSEALED"

    /** Resposta aberta: [json] null = ninguém respondeu ([failure] diz por quê). */
    class Reply(val json: JSONObject?, val host: String, val verified: Boolean, val failure: String)

    /**
     * Abre a resposta do agente. Cifrada: decifra e confere o nonce (se o agente o repetir). Em claro: aceita só se
     * não tiver campo "sealed" e o PC nunca tiver respondido cifrado; senão vira erro.
     * Retorna (json, verificado).
     */
    fun openResponse(raw: JSONObject, encKey: String, nonce: String, sealedSeen: Boolean): Pair<JSONObject, Boolean> {
        if (raw.has("sealed")) {
            val inner = runCatching {
                JSONObject(String(PcbuCrypto.decryptPacket(PcbuCrypto.fromHex(raw.getString("sealed")), encKey, PcbuCrypto.AGENT_WINDOW_MS)))
            }.getOrNull() ?: return JSONObject().put("error", BAD_SEAL) to false
            val echoed = inner.optString("nonce")
            if (echoed.isNotEmpty() && echoed != nonce) return JSONObject().put("error", BAD_SEAL) to false
            return inner to true
        }
        if (sealedSeen) return JSONObject().put("error", UNSEALED) to false
        return raw to false
    }

    /** Resposta do primeiro endereço que atender (com o endereço), ou json null e a descrição da falha. */
    private fun request(
        pc: PairedPc, hosts: List<String>, action: String,
        connectTimeoutMs: Int = 3000, readTimeoutMs: Int = 8000,
    ): Reply {
        val targets = hosts.distinct().filter { it.isNotBlank() }
        if (targets.isEmpty()) return Reply(null, "", false, NO_PATH)
        val winner = AtomicReference<String?>(null)
        val result = AtomicReference<Reply?>(null)
        val lastError = AtomicReference("PC inalcançável")
        val done = CountDownLatch(1)
        val remaining = AtomicInteger(targets.size)
        val phoneIps = org.json.JSONArray(NetInfo.allIpv4())
        for (host in targets) thread(isDaemon = true) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, pc.lockPort), if (NetInfo.isTailscale(host)) connectTimeoutMs * 2 else connectTimeoutMs)
                    if (winner.get() != null) return@thread // outro caminho já resolveu: não repete a ação
                    s.soTimeout = readTimeoutMs
                    val nonce = PcbuCrypto.toHex(ByteArray(12).also { java.security.SecureRandom().nextBytes(it) })
                    // "ips": o PC aprende os endereços atuais do celular (Wi-Fi novo, Tailscale) sem parear de novo.
                    val inner = JSONObject().put("action", action).put("ips", phoneIps).put("wantSealed", true).put("nonce", nonce)
                    val enc = PcbuCrypto.encryptPacket(inner.toString().toByteArray(), pc.encKey)
                    val req = JSONObject().put("deviceId", pc.deviceId).put("wantSealed", true).put("encData", PcbuCrypto.toHex(enc))
                    if (!winner.compareAndSet(null, host)) return@thread
                    Frame.write(s.getOutputStream(), Frame.LOCK_REQUEST, req.toString().toByteArray())
                    val raw = JSONObject(String(Frame.read(s.getInputStream()).payload))
                    val (json, verified) = openResponse(raw, pc.encKey, nonce, pc.sealedSeen)
                    if (verified && !pc.sealedSeen) runCatching { onSealedSeen?.invoke(pc) }
                    result.set(Reply(json, host, verified, ""))
                    done.countDown()
                }
            } catch (e: Exception) {
                if (winner.get() == host) { lastError.set("$host: ${e.javaClass.simpleName}"); done.countDown() }
                else if (winner.get() == null) lastError.set("$host: ${e.javaClass.simpleName}")
            } finally {
                if (remaining.decrementAndGet() == 0) done.countDown()
            }
        }
        done.await(20, TimeUnit.SECONDS)
        return result.get() ?: Reply(null, "", false, lastError.get())
    }
}
