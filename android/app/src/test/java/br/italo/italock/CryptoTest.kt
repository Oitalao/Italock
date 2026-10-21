package br.italo.italock

import br.italo.italock.crypto.PcbuCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class CryptoTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** Vetores do RFC 7914 (seção 11) e os conhecidos para PBKDF2-HMAC-SHA256 com 32 bytes de saída. */
    @Test fun pbkdf2ManualMatchesKnownVectors() {
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(PcbuCrypto.pbkdf2Sha256("password".toByteArray(), "salt".toByteArray(), 1)))
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            hex(PcbuCrypto.pbkdf2Sha256("password".toByteArray(), "salt".toByteArray(), 2)))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(PcbuCrypto.pbkdf2Sha256("password".toByteArray(), "salt".toByteArray(), 4096)))
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc",
            hex(PcbuCrypto.pbkdf2Sha256("passwd".toByteArray(), "salt".toByteArray(), 1)))
    }

    /** A conta feita à mão (Android 6 e 7) dá o mesmo que a do sistema com as 65535 iterações do protocolo. */
    @Test fun pbkdf2ManualMatchesJvmAt65535() {
        val password = "aB3!x_%^&*()-=+[]{}|;:,.<>?zZ9".repeat(2)
        val salt = ByteArray(16) { (it * 7 + 3).toByte() }
        val jvm = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 65535, 256)).encoded
        assertArrayEquals(jvm, PcbuCrypto.pbkdf2Sha256(password.toByteArray(Charsets.UTF_8), salt, 65535))
    }

    @Test fun packetRoundTrip() {
        val key = PcbuCrypto.randomString(64)
        val blob = PcbuCrypto.encryptPacket("""{"action":"status"}""".toByteArray(), key)
        // IV(16) + salt(16) + timestamp(8) + dados + tag(16)
        assertEquals(16 + 16 + 8 + 19 + 16, blob.size)
        assertEquals("""{"action":"status"}""", String(PcbuCrypto.decryptPacket(blob, key)))
        assertEquals("""{"action":"status"}""", String(PcbuCrypto.decryptPacket(PcbuCrypto.fromHex(PcbuCrypto.toHex(blob)), key)))
    }

    @Test fun wrongKeyOrTamperFails() {
        val blob = PcbuCrypto.encryptPacket("oi".toByteArray(), "chave1")
        assertThrows(Exception::class.java) { PcbuCrypto.decryptPacket(blob, "chave2") }
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { PcbuCrypto.decryptPacket(tampered, "chave1") }
    }

    @Test fun oldTimestampRejected() {
        val old = ByteBuffer.allocate(8).putLong(System.currentTimeMillis() - PcbuCrypto.TIMESTAMP_WINDOW_MS - 5_000).array()
        val blob = PcbuCrypto.encrypt(old + "x".toByteArray(), "k")
        assertThrows(PcbuCrypto.TimestampException::class.java) { PcbuCrypto.decryptPacket(blob, "k") }
    }

    @Test fun hexIsUppercaseAndAcceptsLower() {
        assertEquals("00FFA1", PcbuCrypto.toHex(byteArrayOf(0, -1, 0xA1.toByte())))
        assertArrayEquals(byteArrayOf(0, -1, 0xA1.toByte()), PcbuCrypto.fromHex("00ffa1"))
    }
}
