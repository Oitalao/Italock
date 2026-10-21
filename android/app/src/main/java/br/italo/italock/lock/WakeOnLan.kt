package br.italo.italock.lock

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Liga o PC pela rede local (pacote mágico). Não atravessa o Tailscale: precisa estar na mesma rede. */
object WakeOnLan {
    /** "AA:BB:CC:DD:EE:FF" ou com traço separando → 6 bytes; qualquer outra coisa → null (nunca derruba o app). */
    fun parseMac(mac: String): ByteArray? {
        val parts = mac.trim().split(":", "-")
        if (parts.size != 6) return null
        val bytes = parts.map { p -> if (p.length in 1..2) p.toIntOrNull(16) else null }
        if (bytes.any { it == null }) return null
        return ByteArray(6) { bytes[it]!!.toByte() }
    }

    /** Resultado do envio: [ok] = pelo menos um pacote saiu. */
    data class Result(val ok: Boolean, val message: String)

    fun wake(macs: List<String>): Result {
        val valid = macs.mapNotNull { parseMac(it) }
        if (valid.isEmpty()) return Result(false, "Este computador não tem endereço de placa de rede com cabo. Sem ele não dá para ligar pela rede.")
        val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
        var sent = 0
        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                for (bytes in valid) {
                    val payload = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { bytes.toList() }.toByteArray()
                    for (t in targets.distinct()) for (port in listOf(9, 7)) {
                        runCatching { sock.send(DatagramPacket(payload, payload.size, t, port)); sent++ }
                    }
                }
            }
        }
        return if (sent > 0) Result(true, "Sinal de ligar enviado. Só funciona na mesma rede do computador.")
        else Result(false, "Não consegui enviar o sinal de ligar. O celular está na mesma rede do computador?")
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())
}
