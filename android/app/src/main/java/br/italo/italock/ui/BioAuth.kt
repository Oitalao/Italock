package br.italo.italock.ui

import android.app.KeyguardManager
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.italo.italock.crypto.KeyGuard
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import javax.crypto.Cipher

/**
 * Confirmação com rosto/digital/senha do celular para abrir ou guardar o passwordKey, em qualquer Android 6+.
 *  - Android 11+: biometria forte ou senha do celular, com CryptoObject (chave de um uso).
 *  - Android 6 a 10: o Android não aceita "biometria forte + senha" (9 e 10) nem senha com CryptoObject (antes do 11);
 *    usa digital ou senha do celular sem CryptoObject e a chave, que vale alguns segundos, é usada logo em seguida.
 */
object BioAuth {
    /**
     * Com "Aceitar PIN, padrão ou senha" ligado (padrão): biometria ou senha do celular.
     * Desligado: só biometria forte (digital, ou rosto de classe forte).
     */
    private fun authenticators(allowCredential: Boolean): Int = when {
        !allowCredential -> BIOMETRIC_STRONG
        Build.VERSION.SDK_INT >= 30 -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

    /** Abre o passwordKey do PC depois da confirmação. [onFail] recebe null quando o usuário só cancelou. */
    fun openPasswordKey(
        activity: FragmentActivity, pc: PairedPc, title: String, subtitle: String? = null,
        onOk: (String) -> Unit, onFail: (String?) -> Unit,
    ) = run(activity, title, subtitle, { KeyGuard.decryptCipher(pc.passwordKeyIv) { markReset(activity) } },
        { KeyGuard.open(it, pc.passwordKeyEnc) }, onOk, onFail)

    /** Guarda um segredo novo (pareamento). Retorna (cifradoB64, ivB64). */
    fun seal(
        activity: FragmentActivity, title: String, secret: String,
        onOk: (Pair<String, String>) -> Unit, onFail: (String?) -> Unit,
    ) = run(activity, title, null, { KeyGuard.encryptCipher { markReset(activity) } }, { KeyGuard.seal(it, secret) }, onOk, onFail)

    /** A chave foi recriada: PCs pareados antes de agora precisam ser pareados de novo (a tela mostra quais). */
    private fun markReset(activity: FragmentActivity) {
        PairingStore(activity).keyResetAt = System.currentTimeMillis()
    }

    private fun <T> run(
        activity: FragmentActivity, title: String, subtitle: String?,
        cipherFactory: () -> Cipher, use: (Cipher) -> T, onOk: (T) -> Unit, onFail: (String?) -> Unit,
    ) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (km != null && !km.isDeviceSecure) {
            onFail("Configure um bloqueio de tela (PIN, padrão ou senha) no celular primeiro.")
            return
        }
        val allowCredential = br.italo.italock.data.AppPrefs(activity).allowDeviceCredential
        val allowed = authenticators(allowCredential)
        if (!allowCredential && androidx.biometric.BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) !=
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        ) {
            onFail("Nenhuma digital cadastrada neste celular. Cadastre uma, ou ligue Aceitar PIN, padrão ou senha nas Configurações.")
            return
        }
        // Prepara a cifra ANTES de pedir a confirmação: é aqui que uma chave invalidada é descoberta e recriada.
        // Chave de um uso (Android 11+): esta cifra vai no CryptoObject. Chave com prazo (6–10): a preparação falha
        // com "não autenticado" (esperado) e a cifra é feita de novo logo depois da confirmação.
        val probe: Cipher? = try {
            cipherFactory()
        } catch (_: UserNotAuthenticatedException) {
            null
        } catch (e: Exception) {
            onFail(describe(e)); return
        }
        val perUse = KeyGuard.perUseKey()
        if (perUse && probe == null) { onFail("Erro na chave: confirme de novo."); return }
        val pre: Cipher? = if (perUse) probe else null
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val value = try {
                    val cipher = if (perUse) result.cryptoObject?.cipher ?: error("confirmação sem chave") else cipherFactory()
                    use(cipher)
                } catch (e: Exception) {
                    onFail(describe(e)); return
                }
                onOk(value)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val canceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onFail(if (canceled) null else when (errorCode) {
                    BiometricPrompt.ERROR_LOCKOUT -> "Muitas tentativas. Espere um pouco e tente de novo."
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometria bloqueada. Desbloqueie o celular com a senha e tente de novo."
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> "Nenhuma digital cadastrada neste celular."
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "Configure um bloqueio de tela (PIN, padrão ou senha) no celular primeiro."
                    BiometricPrompt.ERROR_TIMEOUT -> "O tempo para confirmar acabou. Tente de novo."
                    BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_HW_NOT_PRESENT -> "O leitor de biometria não está disponível agora."
                    else -> "Confirmação não concluída. Tente de novo."
                })
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).apply {
            if (subtitle != null) setSubtitle(subtitle)
            // Sem a senha do celular como alternativa, o Android exige um botão de cancelar.
            if (allowed and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Cancelar")
        }.setAllowedAuthenticators(allowed).build()
        try {
            if (pre != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(pre)) else prompt.authenticate(info)
        } catch (_: Exception) {
            onFail("Não deu para pedir a confirmação. Tente de novo.")
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UserNotAuthenticatedException ->
            "Confirme com a digital ou com o PIN, padrão ou senha do celular. Neste Android o reconhecimento de rosto não libera a chave."
        is KeyGuard.KeyLostException -> e.message ?: "Pareie o computador de novo."
        is KeyPermanentlyInvalidatedException, is javax.crypto.AEADBadTagException ->
            "O bloqueio de tela do celular mudou e a senha guardada deste computador se perdeu. Pareie o computador de novo."
        else -> "Erro na chave do celular. Tente de novo; se continuar, pareie o computador de novo."
    }
}
