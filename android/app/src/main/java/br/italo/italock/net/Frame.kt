package br.italo.italock.net

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Framing do protocolo (BaseConnection.cpp):
 * magic u64 BE 0xDB065AC7AFDFA4CC | id u16 BE | len u16 BE | payload.
 * O leitor ressincroniza no magic e trata "CLOSE" cru como fim de sessão.
 */
object Frame {
    const val PAIR_INIT = 0x50
    const val PAIR_RESPONSE = 0x51
    const val DEVICE_ID = 0xB0
    const val UNLOCK_REQUEST = 0xB1
    const val UNLOCK_RESPONSE = 0xB2
    /** Extensão do Ítalock: pedido de travar tela ao agente do PC. */
    const val LOCK_REQUEST = 0xC0
    const val LOCK_RESPONSE = 0xC1

    private val MAGIC = byteArrayOf(
        0xDB.toByte(), 0x06, 0x5A, 0xC7.toByte(), 0xAF.toByte(), 0xDF.toByte(), 0xA4.toByte(), 0xCC.toByte()
    )
    private val CLOSE = "CLOSE".toByteArray()

    class ClosedByPeer : Exception("o PC encerrou a sessão")

    data class Packet(val id: Int, val payload: ByteArray)

    fun write(out: OutputStream, id: Int, payload: ByteArray) {
        require(payload.isNotEmpty() && payload.size <= 0xFFFF)
        val header = ByteBuffer.allocate(12).put(MAGIC).putShort(id.toShort()).putShort(payload.size.toShort()).array()
        out.write(header + payload)
        out.flush()
    }

    /** Bytes estranhos tolerados antes do magic: passou disso, a conexão não é de um PC (ou está quebrada). */
    const val MAX_JUNK = 64 * 1024

    class TooMuchJunk : java.io.IOException("lixo demais antes do frame")

    fun read(input: InputStream, maxJunk: Int = MAX_JUNK): Packet {
        val data = if (input is DataInputStream) input else DataInputStream(input)
        // Procura o magic byte a byte (o PC faz o mesmo); "CLOSE" fora de frame encerra.
        var matched = 0
        var closeMatched = 0
        var seen = 0
        while (matched < MAGIC.size) {
            val b = data.read()
            if (b < 0) throw EOFException()
            if (++seen > maxJunk + MAGIC.size) throw TooMuchJunk()
            if (b.toByte() == MAGIC[matched]) matched++
            else matched = if (b.toByte() == MAGIC[0]) 1 else 0
            if (b.toByte() == CLOSE[closeMatched]) {
                closeMatched++
                if (closeMatched == CLOSE.size) throw ClosedByPeer()
            } else closeMatched = if (b.toByte() == CLOSE[0]) 1 else 0
        }
        val id = data.readUnsignedShort()
        val len = data.readUnsignedShort()
        if (len == 0) throw IllegalStateException("payload vazio")
        val payload = ByteArray(len)
        data.readFully(payload)
        return Packet(id, payload)
    }
}
