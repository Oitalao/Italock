package br.italo.italock.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Chave AES do Android Keystore que só funciona depois de rosto, digital ou senha do celular. Protege o passwordKey de cada PC.
 *  - Android 11+: vale para UM uso, liberado pelo BiometricPrompt (CryptoObject) com biometria forte ou senha do celular.
 *  - Android 6 a 10: o Android não aceita "senha do celular" com CryptoObject; a chave vale por alguns segundos
 *    depois de qualquer confirmação (digital ou PIN/padrão/senha) — ver [perUseKey].
 */
object KeyGuard {
    private const val ALIAS = "italock-password-key"
    private const val PROVIDER = "AndroidKeyStore"
    /** Android 6–10: por quantos segundos a chave fica liberada depois da confirmação. */
    const val TIME_BOUND_SECONDS = 15

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    // Sincronizado: duas threads criando a chave ao mesmo tempo gerariam duas, e a segunda apagaria a primeira
    // (tudo o que foi cifrado com ela ficaria ilegível).
    @Synchronized
    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(TIME_BOUND_SECONDS)
        }
        if (Build.VERSION.SDK_INT >= 24) builder.setInvalidatedByBiometricEnrollment(false)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(builder.build()) }.generateKey()
    }

    /**
     * true = a chave vale para um uso e precisa do CryptoObject no BiometricPrompt (criada no Android 11+);
     * false = a chave vale alguns segundos depois de qualquer confirmação (criada no Android 6–10).
     * Decide pela chave que existe, não pela versão atual: quem atualizou o Android continua funcionando.
     */
    fun perUseKey(): Boolean = runCatching {
        val k = key()
        val info = SecretKeyFactory.getInstance(k.algorithm, PROVIDER).getKeySpec(k, KeyInfo::class.java) as KeyInfo
        @Suppress("DEPRECATION")
        info.userAuthenticationValidityDurationSeconds <= 0
    }.getOrDefault(Build.VERSION.SDK_INT >= 30)

    private const val AUTO_ALIAS = "italock-auto-key"

    /**
     * Chave do Keystore SEM exigir biometria a cada uso. Guarda: o passwordKey dos PCs em que o usuário ligou
     * "destravar pela notificação" ou "destravar sem confirmar" (ligar exige biometria uma vez) e a encKey dos pareamentos.
     */
    @Synchronized
    private fun autoKey(): SecretKey {
        (keyStore().getKey(AUTO_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(AUTO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    fun sealAuto(secret: String): Pair<String, String> {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, autoKey()) }
        return Base64.encodeToString(c.doFinal(secret.toByteArray()), Base64.NO_WRAP) to Base64.encodeToString(c.iv, Base64.NO_WRAP)
    }

    fun openAuto(sealedB64: String, ivB64: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, autoKey(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
        }
        return String(c.doFinal(Base64.decode(sealedB64, Base64.NO_WRAP)))
    }

    /**
     * Cifra para guardar uma senha nova. Se a chave foi invalidada (o bloqueio de tela do celular mudou), ela é apagada
     * e recriada na hora — sem isso, parear de novo usaria a mesma chave morta e falharia para sempre.
     * [onReset] avisa que as senhas cifradas com a chave antiga se perderam.
     */
    fun encryptCipher(onReset: () -> Unit = {}): Cipher = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        resetKey(); onReset()
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    }

    /** Cifra para abrir uma senha guardada. Chave invalidada: apaga, recria e lança [KeyLostException]. */
    fun decryptCipher(ivB64: String, onReset: () -> Unit = {}): Cipher = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        resetKey(); onReset()
        throw KeyLostException()
    }

    /** Apaga a chave protegida por biometria; a próxima chamada a [key] cria outra. */
    @Synchronized
    private fun resetKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    /** A chave foi recriada: a senha deste PC (cifrada com a antiga) não pode mais ser aberta. */
    class KeyLostException : Exception(
        br.italo.italock.i18n.L10n.s(br.italo.italock.R.string.crypto_key_lost),
    )

    /** Usa um Cipher já autenticado. Retorna (textoCifradoB64, ivB64). */
    fun seal(authed: Cipher, secret: String): Pair<String, String> =
        Base64.encodeToString(authed.doFinal(secret.toByteArray()), Base64.NO_WRAP) to
            Base64.encodeToString(authed.iv, Base64.NO_WRAP)

    fun open(authed: Cipher, sealedB64: String): String =
        String(authed.doFinal(Base64.decode(sealedB64, Base64.NO_WRAP)))
}
