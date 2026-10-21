package br.italo.italock.data

import android.content.Context
import br.italo.italock.crypto.KeyGuard
import org.json.JSONArray
import org.json.JSONObject

/**
 * Um PC pareado. `passwordKeyEnc` fica cifrado por uma chave do Android Keystore que exige rosto/digital/senha.
 * `encKey` fica na memória em claro, mas no disco vai cifrada pela chave do Keystore sem biometria (ver [toJson]).
 */
data class PairedPc(
    val deviceId: String,
    val encKey: String,
    val passwordKeyEnc: String,
    val passwordKeyIv: String,
    val pcName: String,
    val pcOs: String,
    val userName: String,
    val pairingMethod: String,
    val pcIp: String,
    val pcIps: List<String> = emptyList(),
    val unlockServerPort: Int,
    val macAddresses: List<String>,
    val lockPort: Int = LOCK_PORT_DEFAULT,
    /** Destrava sozinho quando o pedido vem por Bluetooth ou pela rede local (nunca pelo Tailscale). */
    val autoUnlock: Boolean = false,
    /** EXPERIMENTAL: mostra "Destravar" na notificação sem pedir biometria (vale só com a chave global ligada). */
    val notificationApprove: Boolean = false,
    /** EXPERIMENTAL: aprova pelo controle de música do relógio (vale só com a chave global ligada). */
    val watchApprove: Boolean = false,
    val autoKeyEnc: String = "",
    val autoKeyIv: String = "",
    /**
     * MODO PERIGOSO "destravar sem confirmar": o botão Destravar do celular (app, widget, barra rápida) destrava
     * sem biometria. Só vale para pedidos iniciados no celular. Cópia própria da senha, apagada ao desligar.
     */
    val noConfirm: Boolean = false,
    val noConfirmKeyEnc: String = "",
    val noConfirmKeyIv: String = "",
    val pairedAt: Long = System.currentTimeMillis(),
    /** Apelido escolhido no app (lápis no cartão); vazio = nome do PC. */
    val nickname: String = "",
    /** Último uso, ex.: "Destravado" + "Tailscale", com horário. */
    val lastEvent: String = "",
    val lastEventVia: String = "",
    val lastEventAt: Long = 0,
    /**
     * Identificador público e aleatório deste computador, gerado e guardado só no celular. Vai na tag NFC e no relógio:
     * não é o deviceId (que o protocolo usa) nem nada secreto.
     */
    val publicId: String = "",
    /** Caminhos permitidos para este computador: rede local (Wi-Fi ou cabo), Bluetooth, Tailscale (internet). */
    val allowLan: Boolean = true,
    val allowBluetooth: Boolean = true,
    val allowTailscale: Boolean = true,
    /** Tag NFC: "Travar pela tag" e "Destravar pela tag" (os dois = alterna; nenhum = a tag não faz nada). Ver [TagRule]. */
    val tagLock: Boolean = true,
    val tagUnlock: Boolean = true,
    /** EXPERIMENTAL: aprova pelo relógio Wear OS (vale só com a chave global ligada). */
    val wearApprove: Boolean = false,
    /**
     * O agente deste computador já respondeu cifrado alguma vez. Daí em diante resposta em claro é recusada
     * (impede alguém de fingir ser um agente antigo para enganar o cadeado).
     */
    val sealedSeen: Boolean = false,
) {
    val hasAutoKey get() = autoKeyEnc.isNotEmpty() && autoKeyIv.isNotEmpty()
    /** Alguma opção sem biometria (experimental) ainda usa a cópia da senha em [autoKeyEnc]. */
    val usesAutoKey get() = autoUnlock || notificationApprove || watchApprove || wearApprove
    val hasNoConfirmKey get() = noConfirmKeyEnc.isNotEmpty() && noConfirmKeyIv.isNotEmpty()
    /** Modo perigoso ligado e com a cópia da senha guardada. */
    val noConfirmActive get() = noConfirm && hasNoConfirmKey

    /** Nome que aparece em todo lugar: o apelido, se houver. */
    val displayName get() = nickname.ifBlank { pcName }

    fun toJson(): JSONObject = JSONObject()
        .put("deviceId", deviceId).also { putEncKey(it, encKey) }
        .put("passwordKeyEnc", passwordKeyEnc).put("passwordKeyIv", passwordKeyIv)
        .put("pcName", pcName).put("pcOs", pcOs).put("userName", userName)
        .put("pairingMethod", pairingMethod).put("pcIp", pcIp).put("pcIps", JSONArray(pcIps))
        .put("unlockServerPort", unlockServerPort).put("macAddresses", JSONArray(macAddresses))
        .put("lockPort", lockPort).put("pairedAt", pairedAt)
        .put("autoUnlock", autoUnlock).put("notificationApprove", notificationApprove).put("watchApprove", watchApprove)
        .put("autoKeyEnc", autoKeyEnc).put("autoKeyIv", autoKeyIv)
        .put("noConfirm", noConfirm).put("noConfirmKeyEnc", noConfirmKeyEnc).put("noConfirmKeyIv", noConfirmKeyIv)
        .put("nickname", nickname).put("lastEvent", lastEvent).put("lastEventVia", lastEventVia).put("lastEventAt", lastEventAt)
        .put("publicId", publicId).put("allowLan", allowLan).put("allowBluetooth", allowBluetooth).put("allowTailscale", allowTailscale)
        .put(TagRule.K_LOCK, tagLock).put(TagRule.K_UNLOCK, tagUnlock).put("wearApprove", wearApprove).put("sealedSeen", sealedSeen)

    /** O caminho de um pedido (rótulo de [br.italo.italock.net.NetInfo.viaOf]) está liberado para este computador? */
    fun allows(via: String): Boolean = when (via) {
        br.italo.italock.net.NetInfo.VIA_BLUETOOTH -> allowBluetooth
        br.italo.italock.net.NetInfo.VIA_LAN -> allowLan
        // Tailscale ou qualquer outro caminho de fora de casa.
        else -> allowTailscale
    }

    /** Endereços do computador que os caminhos permitidos deixam usar (para travar, destravar e consultar). */
    val hosts: List<String>
        get() = (pcIps + pcIp).filter { it.isNotBlank() }.distinct().filter { ip ->
            if (br.italo.italock.net.NetInfo.isTailscale(ip)) allowTailscale
            else if (br.italo.italock.net.NetInfo.isLocal(ip)) allowLan
            else allowTailscale
        }

    companion object {
        const val LOCK_PORT_DEFAULT = 43301

        /** Cache: evita ir ao Keystore a cada leitura (a lista é lida a cada pacote e a cada consulta de cadeado). */
        private val opened = java.util.concurrent.ConcurrentHashMap<String, String>() // cifrado → claro
        private val sealed = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>() // claro → (cifrado, iv)

        /** Grava a encKey cifrada; se o Keystore falhar, grava em claro como antes (nada se perde). */
        private fun putEncKey(o: JSONObject, plain: String) {
            val s = sealed[plain] ?: runCatching { KeyGuard.sealAuto(plain) }.getOrNull()?.also {
                sealed[plain] = it
                opened[it.first] = plain
            }
            if (s != null) o.put("encKeyEnc", s.first).put("encKeyIv", s.second)
            else o.put("encKey", plain)
        }

        /** Lê a encKey: cifrada (formato novo) ou em claro (pareamentos antigos, migrados na próxima gravação). */
        private fun readEncKey(o: JSONObject): String {
            val enc = o.optString("encKeyEnc")
            val iv = o.optString("encKeyIv")
            if (enc.isEmpty() || iv.isEmpty()) return o.getString("encKey")
            opened[enc]?.let { return it }
            val plain = KeyGuard.openAuto(enc, iv)
            opened[enc] = plain
            sealed.putIfAbsent(plain, enc to iv)
            return plain
        }

        fun needsMigration(o: JSONObject) = o.has("encKey") && !o.has("encKeyEnc")

        fun fromJson(o: JSONObject) = TagRule.read(o).let { (tagLock, tagUnlock) -> PairedPc(
            deviceId = o.getString("deviceId"),
            encKey = readEncKey(o),
            passwordKeyEnc = o.getString("passwordKeyEnc"),
            passwordKeyIv = o.getString("passwordKeyIv"),
            pcName = o.optString("pcName"),
            pcOs = o.optString("pcOs"),
            userName = o.optString("userName"),
            pairingMethod = o.optString("pairingMethod", "UDP"),
            pcIp = o.optString("pcIp"),
            pcIps = o.optJSONArray("pcIps")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            unlockServerPort = o.optInt("unlockServerPort", 43296),
            // Endereço de placa malformado é descartado na leitura (o Wake-on-LAN nunca recebe lixo).
            macAddresses = o.optJSONArray("macAddresses")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
                .filter { br.italo.italock.lock.WakeOnLan.parseMac(it) != null },
            lockPort = o.optInt("lockPort", LOCK_PORT_DEFAULT),
            pairedAt = o.optLong("pairedAt"),
            autoUnlock = o.optBoolean("autoUnlock"),
            notificationApprove = o.optBoolean("notificationApprove"),
            watchApprove = o.optBoolean("watchApprove"),
            autoKeyEnc = o.optString("autoKeyEnc"),
            autoKeyIv = o.optString("autoKeyIv"),
            noConfirm = o.optBoolean("noConfirm"),
            noConfirmKeyEnc = o.optString("noConfirmKeyEnc"),
            noConfirmKeyIv = o.optString("noConfirmKeyIv"),
            nickname = o.optString("nickname"),
            lastEvent = o.optString("lastEvent"),
            lastEventVia = o.optString("lastEventVia"),
            lastEventAt = o.optLong("lastEventAt"),
            publicId = o.optString("publicId"),
            allowLan = o.optBoolean("allowLan", true),
            allowBluetooth = o.optBoolean("allowBluetooth", true),
            allowTailscale = o.optBoolean("allowTailscale", true),
            tagLock = tagLock,
            tagUnlock = tagUnlock,
            wearApprove = o.optBoolean("wearApprove"),
            sealedSeen = o.optBoolean("sealedSeen"),
        ) }

        /** Identificador público novo: 16 bytes aleatórios em hexadecimal. */
        fun newPublicId(): String {
            val b = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            return b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

/**
 * Armazenamento simples em SharedPreferences privadas do app. Segredo real (passwordKey) já vem cifrado pelo Keystore.
 * Toda alteração é ler-alterar-gravar sob [LOCK] e grava com commit(): serviço, widget, atalho e tela mexem ao mesmo
 * tempo, e uma gravação atrasada não pode desfazer outra (ex.: "último uso" religar o modo perigoso recém-desligado).
 */
class PairingStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("italock", Context.MODE_PRIVATE)

    fun all(): List<PairedPc> {
        var arr = JSONArray(prefs.getString("pcs", "[]"))
        // Id público: gravado direto no JSON (não depende do Keystore), para nunca mudar entre leituras.
        if ((0 until arr.length()).any { arr.getJSONObject(it).optString("publicId").isEmpty() }) synchronized(LOCK) {
            arr = JSONArray(prefs.getString("pcs", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("publicId").isEmpty()) o.put("publicId", PairedPc.newPublicId())
            }
            prefs.edit().putString("pcs", arr.toString()).commit()
        }
        val list = ArrayList<PairedPc>(arr.length())
        var migrate = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (PairedPc.needsMigration(o)) migrate = true
            // Um PC ilegível (Keystore apagado) não derruba os outros.
            runCatching { PairedPc.fromJson(o) }.onSuccess { list += it }
                .onFailure { android.util.Log.w("Italock", "pareamento ilegível: ${it.message}") }
        }
        // Migração: pareamentos antigos tinham a encKey em claro; regrava cifrada (só se todos foram lidos).
        if (migrate && list.size == arr.length()) synchronized(LOCK) { writeRaw(list) }
        return list
    }

    fun byDeviceId(id: String): PairedPc? = all().firstOrNull { it.deviceId.equals(id, ignoreCase = true) }

    /**
     * Altera um PC a partir do estado GRAVADO agora (não de uma cópia antiga da tela). Retorna o PC gravado,
     * ou null se ele não existe mais.
     */
    fun update(deviceId: String, change: (PairedPc) -> PairedPc): PairedPc? {
        var list: List<PairedPc> = emptyList()
        val saved = synchronized(LOCK) {
            val current = all()
            val index = current.indexOfFirst { it.deviceId.equals(deviceId, ignoreCase = true) }
            if (index < 0) return null
            val next = change(current[index])
            list = current.toMutableList().also { it[index] = next }
            writeRaw(list)
            next
        }
        afterChange(list)
        return saved
    }

    /** Grava um PC inteiro (pareamento novo ou re-pareamento): no mesmo lugar da lista, ou no fim se for novo. */
    fun save(pc: PairedPc) {
        val list = synchronized(LOCK) {
            val current = all()
            val index = current.indexOfFirst { it.deviceId.equals(pc.deviceId, ignoreCase = true) }
            (if (index >= 0) current.toMutableList().also { it[index] = pc } else current + pc).also { writeRaw(it) }
        }
        afterChange(list)
    }

    /** Sobe (delta = -1) ou desce (delta = +1) um PC na lista. */
    fun move(deviceId: String, delta: Int) {
        val list = synchronized(LOCK) {
            val list = all().toMutableList()
            val from = list.indexOfFirst { it.deviceId.equals(deviceId, ignoreCase = true) }
            val to = from + delta
            if (from < 0 || to !in list.indices) return
            list.add(to, list.removeAt(from))
            writeRaw(list)
            list
        }
        afterChange(list)
    }

    /**
     * Widgets e atalhos do ícone mostram nome, ordem e botões dos PCs: atualiza depois de salvar, renomear, reordenar
     * ou remover. Os atalhos só chamam o sistema se a lista deles mudou de verdade.
     */
    private fun afterChange(list: List<PairedPc>) {
        br.italo.italock.widget.PcWidgetProvider.updateAll(app)
        br.italo.italock.ui.AppShortcuts.sync(app, list)
    }

    /** Grava a lista; PCs que não deu para ler (Keystore com problema) são mantidos como estavam, salvo se removidos. */
    private fun writeRaw(list: List<PairedPc>, removed: String? = null) {
        val out = JSONArray(list.map { it.toJson() })
        val ids = list.map { it.deviceId.lowercase() }.toSet()
        val raw = JSONArray(prefs.getString("pcs", "[]"))
        for (i in 0 until raw.length()) {
            val o = raw.getJSONObject(i)
            val id = o.optString("deviceId").lowercase()
            if (id in ids || id == removed?.lowercase()) continue
            if (runCatching { PairedPc.fromJson(o) }.isFailure) out.put(o)
        }
        prefs.edit().putString("pcs", out.toString()).commit()
    }

    /** PC controlado pelo atalho da barra rápida (vazio = o primeiro da lista; [TILE_NONE] = nenhum). */
    var tileDeviceId: String
        get() = prefs.getString("tileDeviceId", "") ?: ""
        set(value) { prefs.edit().putString("tileDeviceId", value).commit() }

    fun tilePc(): PairedPc? = tilePcOf(all(), tileDeviceId)

    fun byPublicId(id: String): PairedPc? = if (id.isBlank()) null else all().firstOrNull { it.publicId.equals(id, ignoreCase = true) }

    fun remove(deviceId: String) {
        val list = synchronized(LOCK) {
            all().filterNot { it.deviceId.equals(deviceId, ignoreCase = true) }.also { writeRaw(it, removed = deviceId) }
        }
        // O que era só deste computador sai junto: histórico, cadeado do widget e o aviso "Destravado".
        HistoryStore(app).clear(deviceId)
        br.italo.italock.widget.WidgetStatus.forget(app, deviceId)
        br.italo.italock.service.UnlockedNotice.cancel(app, deviceId)
        afterChange(list)
    }

    fun rename(deviceId: String, nickname: String) {
        update(deviceId) { it.copy(nickname = nickname.trim()) }
    }

    /** Registra o último uso (aparece na lista): serve de conferência — se aparecer algo que você não fez, desconfie. */
    fun recordEvent(deviceId: String, event: String, via: String) {
        update(deviceId) { it.copy(lastEvent = event, lastEventVia = via, lastEventAt = System.currentTimeMillis()) }
    }

    /** Guarda o último IP pelo qual o PC falou (broadcast/unicast), útil para travar pelo mesmo caminho. */
    fun rememberPcIp(deviceId: String, ip: String) {
        if (byDeviceId(deviceId)?.pcIps?.firstOrNull() == ip) return
        update(deviceId) { pc -> if (pc.pcIps.firstOrNull() == ip) pc else pc.copy(pcIps = (listOf(ip) + pc.pcIps).distinct().take(6)) }
    }

    /**
     * Quando a chave protegida por biometria teve de ser recriada (o bloqueio de tela mudou), as senhas cifradas com
     * a antiga se perderam: PCs pareados antes disso precisam ser pareados de novo.
     */
    var keyResetAt: Long
        get() = prefs.getLong("keyResetAt", 0)
        set(value) { prefs.edit().putLong("keyResetAt", value).commit() }

    fun needsRepair(pc: PairedPc) = pc.pairedAt < keyResetAt

    /** UUID estável por instalação: re-parear o mesmo celular gera o mesmo deviceId no PC. */
    val deviceUuid: String
        get() = prefs.getString("deviceUuid", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceUuid", it).commit()
        }

    companion object {
        private val LOCK = Any()
        const val TILE_NONE = "none"

        /** PC do atalho rápido: o escolhido, o primeiro da lista se nada foi escolhido, ou nenhum. */
        fun tilePcOf(list: List<PairedPc>, tileId: String): PairedPc? =
            if (tileId == TILE_NONE) null else list.firstOrNull { it.deviceId.equals(tileId, true) } ?: list.firstOrNull()
        const val UDP_PORT = 43298
        const val UDP_MANUAL_PORT = 43299
        const val TCP_PORT = 43300
    }
}
