package br.italo.italock.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import br.italo.italock.R
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.i18n.L10n
import br.italo.italock.ui.QuickUnlockActivity

/**
 * Aviso "Destravado <PC>" com o botão "Travar de novo", depois de destravar pelo celular. Some sozinho em
 * [VISIBLE_MS] (setTimeoutAfter: quem apaga é o sistema, nada fica rodando). Com "Esconder nome no bloqueio",
 * ele não aparece na tela de bloqueio.
 */
object UnlockedNotice {
    const val VISIBLE_MS = 15_000L
    private const val BASE_ID = 7000

    fun id(deviceId: String) = BASE_ID + deviceId.lowercase().hashCode()

    /** [via]: rótulo interno do caminho (NetInfo.viaLabel); aparece no idioma escolhido. */
    fun show(context: Context, pc: PairedPc, via: String, title: String = L10n.s(R.string.notice_unlocked, pc.displayName)) {
        val app = context.applicationContext
        UnlockService.createChannels(app)
        val prefs = AppPrefs(app)
        // "Confirmar antes de travar" vale aqui também: com ela ligada, o botão abre a pergunta.
        val lockPi = if (prefs.confirmLock) {
            PendingIntent.getActivity(
                app, ("again" + pc.deviceId).hashCode(),
                Intent(app, QuickUnlockActivity::class.java)
                    .putExtra(QuickUnlockActivity.EXTRA_DEVICE, pc.deviceId)
                    .putExtra(QuickUnlockActivity.EXTRA_ACTION, QuickUnlockActivity.ACTION_LOCK)
                    .putExtra(QuickUnlockActivity.EXTRA_ORIGIN, Origin.NOTIFICATION.key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            PendingIntent.getBroadcast(
                app, ("again" + pc.deviceId).hashCode(),
                Intent(app, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_LOCK)
                    .putExtra(ActionReceiver.EXTRA_DEVICE, pc.deviceId)
                    .putExtra(ActionReceiver.EXTRA_ORIGIN, Origin.NOTIFICATION.key),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val b = NotificationCompat.Builder(app, UnlockService.CHANNEL_PROMPT)
            .setSmallIcon(R.drawable.ic_lock_open).setColor(UnlockService.BRAND_BLUE)
            .setContentTitle(title)
            .setContentText(L10n.s(R.string.prompt_via, L10n.via(via)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(VISIBLE_MS)
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_lock, L10n.s(R.string.notice_lock_again), lockPi).build())
        // "Esconder nome no bloqueio": o aviso nem aparece na tela de bloqueio (dura 15 s; com o celular destravado ele
        // fica na barra normalmente). Versão privada com versão pública não bastaria: o Android só usa a pública se a
        // pessoa tiver mandado ocultar conteúdo sensível.
        if (prefs.hideNameOnLockscreen) b.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        val nid = id(pc.deviceId)
        runCatching { app.getSystemService(NotificationManager::class.java)?.notify(nid, b.build()) }
        // Android 6 e 7 não têm setTimeoutAfter: um lembrete único apaga o aviso (só o deste envio, não um mais novo).
        if (android.os.Build.VERSION.SDK_INT < 26) {
            val token = Any()
            tokens[nid] = token
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (tokens.remove(nid, token)) cancel(app, pc.deviceId)
            }, VISIBLE_MS)
        }
    }

    private val tokens = java.util.concurrent.ConcurrentHashMap<Int, Any>()

    fun cancel(context: Context, deviceId: String) {
        runCatching { context.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(id(deviceId)) }
    }
}
