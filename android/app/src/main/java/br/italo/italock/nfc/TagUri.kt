package br.italo.italock.nfc

/**
 * Conteúdo da tag NFC do Ítalock: um registro URI `italock://pc/<id público>` (primeiro, é por ele que o Android escolhe
 * o app) e um Android Application Record "br.italo.italock". Nada secreto: o id público é aleatório e só o celular sabe
 * de qual computador ele é. Parte pura (sem Android) para os testes.
 */
object TagUri {
    const val SCHEME = "italock"
    const val HOST = "pc"
    const val AAR_PACKAGE = "br.italo.italock"

    /** NDEF: TNF e tipos usados (NFC Forum, "URI Record Type Definition"). */
    const val TNF_WELL_KNOWN: Short = 0x01
    const val TNF_ABSOLUTE_URI: Short = 0x03
    val RTD_URI = byteArrayOf('U'.code.toByte())

    private val ID = Regex("^[0-9a-fA-F]{8,64}$")

    fun build(publicId: String): String = "$SCHEME://$HOST/$publicId"

    /** Id público da URI `italock://pc/<id>` (esquema e host sem diferença de maiúsculas), ou null se não for do Ítalock. */
    fun parse(uri: String?): String? {
        if (uri == null) return null
        val m = Regex("^([A-Za-z][A-Za-z0-9+.]*)://([^/]+)/([^/?#]+)/?$").find(uri.trim()) ?: return null
        val (scheme, host, id) = m.destructured
        if (!scheme.equals(SCHEME, true) || !host.equals(HOST, true)) return null
        return id.takeIf { ID.matches(it) }?.lowercase()
    }

    /** Um registro NDEF sem as classes do Android (tnf, tipo, dados). */
    class Record(val tnf: Short, val type: ByteArray, val payload: ByteArray)

    /** Procura o registro URI do Ítalock entre os registros de uma mensagem NDEF. */
    fun fromRecords(records: List<Record>): String? {
        for (r in records) {
            val uri = when {
                r.tnf == TNF_WELL_KNOWN && r.type.contentEquals(RTD_URI) -> decodeUriPayload(r.payload)
                r.tnf == TNF_ABSOLUTE_URI -> String(r.type, Charsets.UTF_8)
                else -> null
            }
            parse(uri)?.let { return it }
        }
        return null
    }

    /** Dados de um registro URI: 1 byte de prefixo (tabela do NFC Forum) + o resto em UTF-8. */
    fun decodeUriPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val code = payload[0].toInt() and 0xFF
        val prefix = PREFIXES.getOrNull(code) ?: return null
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    /** Monta os dados de um registro URI sem prefixo abreviado (código 0x00), como o Android faz para esquemas próprios. */
    fun encodeUriPayload(uri: String): ByteArray = byteArrayOf(0) + uri.toByteArray(Charsets.UTF_8)

    /** Tabela de prefixos do URI Record Type Definition (NFC Forum), códigos 0x00 a 0x23. */
    private val PREFIXES = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://anonymous:anonymous@",
        "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:",
        "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://",
        "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )
}
