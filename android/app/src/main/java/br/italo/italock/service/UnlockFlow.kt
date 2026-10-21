package br.italo.italock.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import br.italo.italock.R
import br.italo.italock.crypto.KeyGuard
import br.italo.italock.data.DiagLog
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.lock.LockClient
import br.italo.italock.ui.QuickUnlockActivity
import kotlin.concurrent.thread

/**
 * O caminho único do botão Destravar (app, widget, atalho rápido, tag NFC, relógio): pré aprova com a senha já aberta
 * e chama o computador ("wake"). Bloqueante: nunca na thread da tela.
 */
object UnlockFlow {
    data class Result(val ok: Boolean, val message: String, val token: Long?, val alreadyOpen: Boolean = false)

    /** [origin]: de onde partiu o toque (app, widget, atalho, tag, relógio); vai para o histórico quando o PC entrar. */
    fun wakeWithKey(pc: PairedPc, key: String, origin: Origin = Origin.APP): Result {
        val token = PreApproval.grant(pc.deviceId, key, origin)
            ?: return Result(false, "${pc.displayName} já está pedindo: confirme no pedido aberto.", null, alreadyOpen = true)
        val r = LockClient.wakeOutcome(pc)
        if (!r.ok) {
            // Não chegou ao computador (ou ele recusou): a pré aprovação não pode ficar valendo.
            PreApproval.revoke(pc.deviceId, token)
            DiagLog.i("destravar ${pc.displayName}: ${r.error.ifEmpty { "sem resposta" }}")
            return Result(false, r.message(pc), token)
        }
        PreApproval.wakeDelivered(pc.deviceId)
        return Result(true, r.message(pc), token)
    }

    /** A senha não fica na memória além do prazo (só a entrada deste grant é apagada). */
    fun releaseLater(pc: PairedPc, token: Long?) {
        if (token == null) return
        thread(isDaemon = true) { Thread.sleep(PreApproval.WINDOW_MS + 1000); PreApproval.revoke(pc.deviceId, token) }
    }

    /**
     * Modo sem confirmação: a senha sai da chave sem biometria. Só vale quando a decisão de destravar não veio de um
     * estado NÃO verificado (resposta em claro de agente antigo). null = precisa confirmar.
     */
    fun noConfirmKey(pc: PairedPc, decidedFromUnverified: Boolean = false): String? {
        if (!pc.noConfirmActive || decidedFromUnverified) return null
        return runCatching { KeyGuard.openAuto(pc.noConfirmKeyEnc, pc.noConfirmKeyIv) }.getOrNull()
    }

    /**
     * Pedido que veio de fora da tela (relógio): mostra no celular uma notificação que abre a confirmação.
     * O Android não deixa abrir tela sozinho a partir de segundo plano.
     */
    /**
     * [quiet]: aviso silencioso de que o computador está esperando na tela de bloqueio (pedido automático do PC ou
     * pedido que expirou). Sem som nem vibração, fica até 12 h; tocar destrava como o botão do app.
     */
    fun askOnPhone(context: Context, pc: PairedPc, action: String = QuickUnlockActivity.ACTION_UNLOCK, origin: Origin = Origin.WATCH,
                   quiet: Boolean = false) {
        val intent = Intent(context, QuickUnlockActivity::class.java)
            .putExtra(QuickUnlockActivity.EXTRA_DEVICE, pc.deviceId)
            .putExtra(QuickUnlockActivity.EXTRA_ACTION, action)
            .putExtra(QuickUnlockActivity.EXTRA_ORIGIN, origin.key)
            // Veio de fora da tela: sempre com biometria, mesmo com "Destravar sem confirmação" ligado.
            .putExtra(QuickUnlockActivity.EXTRA_UNVERIFIED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, ("ask" + action + pc.deviceId).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        UnlockService.createChannels(context)
        val lock = action == QuickUnlockActivity.ACTION_LOCK
        val prefs = br.italo.italock.data.AppPrefs(context)
        val name = if (prefs.hideNameNow(context)) "o computador" else pc.displayName
        val b = NotificationCompat.Builder(context, if (quiet) UnlockService.CHANNEL_WAITING else UnlockService.CHANNEL_PROMPT)
            .setSmallIcon(R.drawable.ic_lock).setColor(UnlockService.BRAND_BLUE)
            .setContentTitle(if (lock) "Travar $name?" else "Destravar $name?")
            .setContentText(if (quiet) "Está na tela de bloqueio. Toque para destravar." else "Toque para confirmar no celular.")
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (quiet) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_REMINDER)
            .setSilent(quiet).setOnlyAlertOnce(quiet)
            .setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(if (quiet) QUIET_TIMEOUT_MS else 60_000)
        // "Esconder nome no bloqueio": na tela de bloqueio aparece a versão sem o nome.
        if (prefs.hideNameOnLockscreen) {
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            b.setPublicVersion(NotificationCompat.Builder(context, UnlockService.CHANNEL_PROMPT)
                .setSmallIcon(R.drawable.ic_lock).setColor(UnlockService.BRAND_BLUE)
                .setContentTitle(if (lock) "Travar o computador?" else "Destravar o computador?").build())
        }
        val n = b.build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ASK_NOTIF_BASE + pc.deviceId.hashCode(), n) }
    }

    fun cancelAsk(context: Context, pc: PairedPc) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(ASK_NOTIF_BASE + pc.deviceId.hashCode()) }
    }

    private const val ASK_NOTIF_BASE = 5000
    private const val QUIET_TIMEOUT_MS = 12 * 60 * 60 * 1000L
}
