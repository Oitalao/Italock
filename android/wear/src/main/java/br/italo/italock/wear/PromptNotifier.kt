package br.italo.italock.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** Notificação do pedido de destravar, com as ações no próprio pulso. */
object PromptNotifier {
    private const val CHANNEL = "pedidos"
    private const val NOTIF_ID = 1
    const val EXTRA_SESSION = "session"
    const val EXTRA_PC = "pc"
    const val EXTRA_USER = "user"
    const val EXTRA_EXPIRES = "expiresAt"
    const val EXTRA_APPROVE = "approve"

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Pedidos", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pedidos para destravar o computador"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 100, 120)
        }
        nm.createNotificationChannel(ch)
    }

    fun promptIntent(ctx: Context, p: Prompt) = Intent(ctx, PromptActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_SESSION, p.session)
        .putExtra(EXTRA_PC, p.pc)
        .putExtra(EXTRA_USER, p.user)
        .putExtra(EXTRA_EXPIRES, p.expiresAt)

    fun show(ctx: Context, p: Prompt) {
        ensureChannel(ctx)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(ctx, p.session.hashCode(), promptIntent(ctx, p), flags)
        fun answer(approve: Boolean): PendingIntent {
            val i = Intent(ctx, AnswerReceiver::class.java)
                .putExtra(EXTRA_SESSION, p.session)
                .putExtra(EXTRA_APPROVE, approve)
            return PendingIntent.getBroadcast(ctx, p.session.hashCode() * 2 + if (approve) 1 else 0, i, flags)
        }
        val secure = watchHasLock(ctx)
        val title = if (p.pc.isNotBlank()) "Destravar ${p.pc}?" else "Destravar o computador?"
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(title)
            .setContentText(if (secure) p.user else "Ative o bloqueio de tela do relógio para destravar por aqui")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        val left = p.expiresAt - System.currentTimeMillis()
        if (left > 0) b.setTimeoutAfter(left)
        // Destravar abre a tela do pedido (não aprova direto): com o relógio travado, ela pede para destravar antes.
        val approveOpen = PendingIntent.getActivity(ctx, p.session.hashCode() * 2 + 1,
            promptIntent(ctx, p).putExtra(EXTRA_APPROVE, true), flags)
        if (secure) b.addAction(R.drawable.ic_lock_open, "Destravar", approveOpen)
        b.addAction(R.drawable.ic_lock, "Recusar", answer(false))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx).notify(p.session, NOTIF_ID, b.build())
        }
        vibrateDouble(ctx)
    }

    fun cancel(ctx: Context, session: String) {
        NotificationManagerCompat.from(ctx).cancel(session, NOTIF_ID)
    }
}

/** Resposta direto pelos botões da notificação. */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getStringExtra(PromptNotifier.EXTRA_SESSION) ?: return
        val approve = intent.getBooleanExtra(PromptNotifier.EXTRA_APPROVE, false)
        PromptNotifier.cancel(context, session)
        Bus.promptEnded.tryEmit(session)
        val app = context.applicationContext
        val pending = goAsync()
        Phone.scope.launch {
            try { Phone.sendAnswer(app, session, approve) } finally { pending.finish() }
        }
    }
}
