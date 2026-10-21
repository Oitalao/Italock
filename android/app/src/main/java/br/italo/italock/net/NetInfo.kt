package br.italo.italock.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetInfo {
    /** Rótulos de caminho mostrados ao usuário (e usados nas regras de "perto" e de caminhos permitidos). */
    const val VIA_LAN = "rede local"
    const val VIA_BLUETOOTH = "Bluetooth"
    const val VIA_TAILSCALE = "Tailscale"
    const val VIA_INTERNET = "internet"

    /** Todos os IPv4 que não sejam loopback, incluindo Tailscale (100.64.0.0/10) e ancoragem USB. Faz E/S: fora da tela. */
    fun allIpv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress!! } }
            .distinct()
    }.getOrDefault(emptyList())

    /** IP preferido para o campo legado `ipAddress`: rede local privada antes de Tailscale/celular. */
    fun bestLocalIp(): String? {
        val ips = allIpv4()
        return ips.firstOrNull { isPrivate(it) } ?: ips.firstOrNull()
    }

    /**
     * Nome curto do caminho para mostrar ao usuário, a partir de um rótulo já calculado ou de um IP.
     * IP público vira "internet"; nunca aparece "Wi-Fi/cabo".
     */
    fun viaLabel(raw: String): String = when {
        raw == VIA_TAILSCALE || isTailscale(raw) -> VIA_TAILSCALE
        raw == VIA_BLUETOOTH -> VIA_BLUETOOTH
        raw == VIA_LAN || isLocal(raw) -> VIA_LAN
        else -> VIA_INTERNET
    }

    /**
     * Caminho de uma conexão pelos dois lados: [remote] (IP do computador) e [local] (IP do celular em que ela chegou
     * ou saiu). Só "rede local" e "Bluetooth" contam como "perto".
     *  - qualquer lado no Tailscale → "Tailscale";
     *  - IP privado que chegou por uma VPN (ex.: rota de sub rede do Tailscale) NÃO é perto → "internet";
     *  - IP privado por Wi-Fi/cabo → "rede local";
     *  - o resto → "internet".
     */
    fun viaOf(remote: String, local: String? = null, localIsVpn: Boolean = local?.let { isVpnAddress(it) } ?: false): String = when {
        isTailscale(remote) || (local != null && isTailscale(local)) -> VIA_TAILSCALE
        localIsVpn -> VIA_INTERNET
        isLocal(remote) -> VIA_LAN
        else -> VIA_INTERNET
    }

    /** O endereço local pertence a uma interface de VPN (tun/ppp/ipsec)? */
    fun isVpnAddress(local: String): Boolean = runCatching {
        val nif = NetworkInterface.getByInetAddress(InetAddress.getByName(local)) ?: return false
        isVpnInterface(nif.name)
    }.getOrDefault(false)

    fun isVpnInterface(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("tun") || n.startsWith("ppp") || n.startsWith("ipsec") || n.startsWith("wg")
    }

    /** Privado ou de enlace (ancoragem USB, rede direta). */
    fun isLocal(ip: String): Boolean {
        val p = octets(ip) ?: return false
        return isPrivate(ip) || (p[0] == 169 && p[1] == 254)
    }

    fun isPrivate(ip: String): Boolean {
        val p = octets(ip) ?: return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    fun isTailscale(ip: String): Boolean {
        val p = octets(ip) ?: return false
        return p[0] == 100 && p[1] in 64..127
    }

    private fun octets(ip: String): List<Int>? {
        val p = ip.trim().split(".")
        if (p.size != 4) return null
        val n = p.map { it.toIntOrNull() ?: return null }
        return if (n.all { it in 0..255 }) n else null
    }
}
