package br.italo.italock.pair

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import br.italo.italock.crypto.PcbuCrypto
import br.italo.italock.data.PairingStore
import br.italo.italock.net.Frame
import br.italo.italock.net.NetInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Dados lidos do QR code (formato novo com serverId ou legado com ip) ou do código manual Base32. */
data class QrData(val encKey: String, val method: String, val port: Int, val serverId: String?, val ip: String?) {
    val isLegacy get() = ip != null

    companion object {
        fun parse(text: String): QrData {
            val trimmed = text.trim()
            val json = if (trimmed.startsWith("{")) trimmed else String(base32Decode(trimmed))
            val o = JSONObject(json)
            return QrData(
                encKey = o.getString("encKey"),
                method = o.optString("method", "UDP"),
                port = o.getInt("port"),
                serverId = o.optString("serverId").ifEmpty { null },
                ip = o.optString("ip").ifEmpty { null },
            )
        }

        private fun base32Decode(code: String): ByteArray {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val clean = code.uppercase().filter { it != '-' && !it.isWhitespace() && it != '=' }
            val out = java.io.ByteArrayOutputStream()
            var buffer = 0
            var bits = 0
            for (c in clean) {
                val v = alphabet.indexOf(c)
                require(v >= 0) { "código inválido" }
                buffer = (buffer shl 5) or v
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    out.write((buffer shr bits) and 0xFF)
                }
            }
            return out.toByteArray()
        }
    }
}

data class PairResult(
    val deviceId: String,
    val passwordKey: String,
    val pcName: String,
    val pcOs: String,
    val userName: String,
    val pairingMethod: String,
    val unlockServerPort: Int,
    val macAddresses: List<String>,
    val pcIp: String,
    val pcIps: List<String>,
    val encKey: String,
)

/** O computador não apareceu na rede (nenhum aviso de pareamento chegou em 60 s). */
class PcNotFoundException : Exception("computador não encontrado")

/** O computador respondeu com erro ao pareamento (texto do programa do computador, não mostrado cru). */
class PcRefusedException(val pcMessage: String) : Exception("recusado pelo computador")

/** Frase para a tela a partir da falha do pareamento (nunca a mensagem crua do sistema). */
fun describePairingError(e: Throwable): String = when (e) {
    is PcNotFoundException -> "Computador não encontrado. Os dois estão na mesma rede, ou ligados pelo cabo USB com ancoragem?"
    is PcRefusedException -> "O computador recusou o pareamento. Gere um QR code novo no computador e tente de novo."
    is PcbuCrypto.TimestampException -> "Relógio do celular fora de hora. Ative a data e hora automáticas no celular e no computador."
    is java.net.SocketTimeoutException -> "O computador demorou demais para responder. Confira se a tela de parear continua aberta nele."
    is java.net.ConnectException, is java.net.NoRouteToHostException -> "Não deu para conectar ao computador. Confira se a tela de parear está aberta e se os dois estão na mesma rede."
    is java.net.UnknownHostException -> "Endereço do computador inválido. Gere um QR code novo."
    is javax.crypto.AEADBadTagException, is java.security.GeneralSecurityException -> "O código não confere com o do computador. Leia o QR code de novo."
    is java.io.EOFException, is java.net.SocketException -> "A conexão com o computador caiu no meio. Tente de novo."
    is org.json.JSONException -> "Resposta do computador em formato inesperado. Atualize o Ítalock no computador."
    else -> "Não deu para parear. Tente de novo."
}

class PairingClient(private val context: Context) {
    private val store = PairingStore(context)

    /** Descobre o PC (beacon UDP ou IP do QR legado) e faz a troca de pareamento. Bloqueante: chamar fora da thread de UI. */
    fun pair(qr: QrData, onStatus: (String) -> Unit): PairResult {
        val (ip, port) = if (qr.isLegacy) qr.ip!! to qr.port else discover(qr, onStatus)
        onStatus("Conectando ao computador")
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), 8000)
            s.soTimeout = 20000
            s.tcpNoDelay = true
            val init = JSONObject()
                .put("cloudToken", "")
                .put("deviceName", deviceName())
                .put("deviceUUID", store.deviceUuid)
                .put("ipAddress", NetInfo.bestLocalIp() ?: "")
                .put("protoVersion", "4.0.0")
                .put("tcpPort", PairingStore.TCP_PORT)
                .put("udpManualPort", PairingStore.UDP_MANUAL_PORT)
                .put("udpPort", PairingStore.UDP_PORT)
                // Extensão Ítalock: todos os IPs do celular (inclui Tailscale 100.x) para o PC chamar direto.
                .put("ipAddresses", JSONArray(NetInfo.allIpv4()))
                .put("italock", 1)
            Frame.write(s.getOutputStream(), Frame.PAIR_INIT, PcbuCrypto.encryptPacket(init.toString().toByteArray(), qr.encKey))
            onStatus("Esperando a resposta do computador")
            val resp = Frame.read(s.getInputStream())
            val o = JSONObject(String(PcbuCrypto.decryptPacket(resp.payload, qr.encKey)))
            val err = o.optString("errMsg")
            if (err.isNotEmpty()) throw PcRefusedException(err)
            val d = o.getJSONObject("data")
            return PairResult(
                deviceId = d.getString("deviceId"),
                passwordKey = d.getString("passwordKey"),
                pcName = d.optString("deviceName"),
                pcOs = d.optString("deviceOS"),
                userName = d.optString("userName"),
                pairingMethod = d.optString("pairingMethod", qr.method),
                unlockServerPort = d.optInt("unlockServerPort", 43296),
                // Só endereços de placa válidos são guardados (o Wake-on-LAN não recebe lixo).
                macAddresses = d.optJSONArray("macAddresses")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
                    .filter { br.italo.italock.lock.WakeOnLan.parseMac(it) != null },
                pcIp = ip,
                pcIps = (listOf(ip) + (d.optJSONArray("ipAddresses")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList())).distinct(),
                encKey = qr.encKey,
            )
        }
    }

    private fun discover(qr: QrData, onStatus: (String) -> Unit): Pair<String, Int> {
        onStatus("Procurando o computador na rede")
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("italock-pair")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket(null).use { sock ->
                sock.reuseAddress = true
                sock.broadcast = true
                sock.bind(InetSocketAddress(qr.port))
                sock.soTimeout = 2500
                val deadline = System.currentTimeMillis() + 60_000
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try { sock.receive(pkt) } catch (_: SocketTimeoutException) { continue }
                    val plain = runCatching {
                        PcbuCrypto.decryptPacket(pkt.data.copyOf(pkt.length), qr.encKey)
                    }.getOrNull() ?: continue
                    val o = JSONObject(String(plain))
                    if (o.optString("serverId") != qr.serverId) continue
                    return o.getString("ip") to o.getInt("port")
                }
            }
        } finally {
            lock?.release()
        }
        throw PcNotFoundException()
    }

    /** Nome do aparelho (Android 7.1+); antes disso, o modelo. */
    private fun deviceName(): String =
        (if (Build.VERSION.SDK_INT >= 25) Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) else null)
            ?: Build.MODEL
}
