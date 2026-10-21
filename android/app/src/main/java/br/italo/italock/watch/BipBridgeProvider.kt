package br.italo.italock.watch

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import br.italo.italock.crypto.KeyGuard
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.DiagLog
import br.italo.italock.data.Origin
import br.italo.italock.data.PairingStore
import br.italo.italock.service.UnlockSession
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Entrada que o fork do Gadgetbridge (relógio Amazfit, pela "chamada falsa") chama para responder a um pedido de
 * desbloqueio: ignorar no relógio = aprovar, recusar = negar.
 *
 * Exportada, mas trancada por código: só o pacote do fork com o certificado certo passa (pacotes são únicos e o
 * Android impede colisão; o certificado é conferido por garantia). A aprovação só vale para uma sessão que o PRÓPRIO
 * celular abriu e anunciou ([BipBridge.announce]); o id da sessão é aleatório e vai só ao fork, por broadcast
 * explícito. Sem biometria: exige o experimental ligado, o PC marcado e a cópia da senha guardada (autoKey).
 */
class BipBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!callerIsFork(ctx)) {
            DiagLog.w("ponte do relógio: chamador recusado ($callingPackage)")
            return null
        }
        if (method != "answer") return null
        val sessionId = extras?.getString("session") ?: return null
        val action = extras.getString("action") ?: return null
        val s = UnlockSession.active[sessionId] ?: return result("NO_SESSION")
        if (action == "deny") {
            thread(isDaemon = true) { runCatching { s.deny("CANCEL", Origin.WATCH) } }
            return result("DENIED")
        }
        if (action != "approve") return null
        val pc = PairingStore(ctx).byDeviceId(s.pc.deviceId)
        if (pc == null || !pc.watchApprove || !pc.hasAutoKey || !AppPrefs(ctx).expWatch) return result("NOT_ALLOWED")
        val key = runCatching { KeyGuard.openAuto(pc.autoKeyEnc, pc.autoKeyIv) }.getOrNull() ?: return result("APP_ERROR")
        thread(isDaemon = true) { runCatching { s.approve(key, Origin.WATCH) } }
        return result("APPROVED")
    }

    private fun result(r: String) = Bundle().apply { putString("result", r) }

    private fun callerIsFork(ctx: Context): Boolean {
        if (callingPackage != FORK_PKG) return false
        return certSha256(ctx, FORK_PKG) == FORK_CERT_SHA256
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun certSha256(ctx: Context, pkg: String): String? = runCatching {
        val sigs = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures ?: return null
        val sig = sigs.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()

    // ContentProvider exige o resto, mas a ponte só usa call().
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    companion object {
        const val FORK_PKG = "nodomain.freeyourgadget.gadgetbridge.voz"
        // Certificado (debug) do fork Gadgetbridge Voz, SHA-256. Para distribuir, o fork precisa de assinatura própria.
        const val FORK_CERT_SHA256 = "A481A7B4A952E3762BE83A4CD46D5F4482FEECD4BA3187F9DFE248DBC83A7B10"
    }
}
