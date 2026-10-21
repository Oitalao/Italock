package br.italo.italock.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Criptografia compatível com o lado do PC (CryptUtils.cpp do PC Bio Unlock):
 * blob = IV(16) || salt(16) || ciphertext || tag(16), AES-256-GCM sem AAD,
 * chave = PBKDF2-HMAC-SHA256(senha ASCII, salt, 65535 iterações, 32 bytes).
 * "Packet" = prefixo int64 big-endian com o horário em ms, válido por ±120 s.
 */
object PcbuCrypto {
    private const val IV_LEN = 16
    private const val SALT_LEN = 16
    private const val TAG_BITS = 128
    private const val ITERATIONS = 65535
    const val TIMESTAMP_WINDOW_MS = 120_000L

    private val random = SecureRandom()

    class TimestampException : Exception("timestamp fora da janela de 2 minutos")

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val bytes = try {
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (_: java.security.NoSuchAlgorithmException) {
            // Android 6 e 7 não têm PBKDF2WithHmacSHA256: mesma conta feita à mão com HMAC-SHA256.
            pbkdf2Sha256(password.toByteArray(Charsets.UTF_8), salt, ITERATIONS)
        }
        return SecretKeySpec(bytes, "AES")
    }

    /** PBKDF2-HMAC-SHA256 (RFC 8018) com saída de 32 bytes = um bloco. */
    internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        var u = mac.doFinal(byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
        }
        return t
    }

    fun encrypt(data: ByteArray, password: String): ByteArray {
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        return iv + salt + cipher.doFinal(data)
    }

    fun decrypt(blob: ByteArray, password: String): ByteArray {
        require(blob.size > IV_LEN + SALT_LEN + TAG_BITS / 8) { "blob curto" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val salt = blob.copyOfRange(IV_LEN, IV_LEN + SALT_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(blob, IV_LEN + SALT_LEN, blob.size - IV_LEN - SALT_LEN)
    }

    fun encryptPacket(data: ByteArray, password: String): ByteArray {
        val ts = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array()
        return encrypt(ts + data, password)
    }

    /** Respostas do agente do PC: mesma janela de 60 s que o agente usa para os pedidos. */
    const val AGENT_WINDOW_MS = 60_000L

    fun decryptPacket(blob: ByteArray, password: String, windowMs: Long = TIMESTAMP_WINDOW_MS): ByteArray {
        val plain = decrypt(blob, password)
        require(plain.size > 8) { "pacote vazio" }
        val ts = ByteBuffer.wrap(plain, 0, 8).long
        if (abs(System.currentTimeMillis() - ts) > windowMs) throw TimestampException()
        return plain.copyOfRange(8, plain.size)
    }

    fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) append("%02X".format(b.toInt() and 0xFF))
    }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex ímpar" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private const val ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%^&*()-_=+[]{}|;:,.<>?"

    fun randomString(len: Int): String {
        val bytes = ByteArray(len).also(random::nextBytes)
        return String(CharArray(len) { ALPHABET[(bytes[it].toInt() and 0xFF) % ALPHABET.length] })
    }
}
