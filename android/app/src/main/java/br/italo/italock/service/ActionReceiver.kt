package br.italo.italock.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import br.italo.italock.crypto.KeyGuard
import br.italo.italock.data.EventKind
import br.italo.italock.data.Events
import br.italo.italock.data.Origin
import br.italo.italock.data.PairingStore
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.WakeOnLan
import br.italo.italock.ui.UnlockActivity
import br.italo.italock.widget.WidgetStatus
import kotlin.concurrent.thread

/**
 * Ações sem abrir o app: botões da notificação e botões Travar/Ligar do widget.
 * Nunca abre tela daqui (o Android 12+ bloqueia "trampolim"): ações que precisam de tela usam PendingIntent.getActivity.
 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                when (intent.action) {
                    // Android 12+: a ação vem com setAuthenticationRequired, o sistema já exigiu o celular destravado.
                    ACTION_APPROVE -> approveWithoutBiometric(context, intent.getStringExtra(UnlockActivity.EXTRA_SESSION))
                    ACTION_DENY -> UnlockSession.active[intent.getStringExtra(UnlockActivity.EXTRA_SESSION) ?: ""]?.deny()
                    ACTION_LOCK -> toast(context, PairingStore(context).byDeviceId(intent.getStringExtra(EXTRA_DEVICE) ?: "")
                        ?.let { pc ->
                            val origin = Origin.fromKey(intent.getStringExtra(EXTRA_ORIGIN)) ?: Origin.WIDGET
                            val r = LockClient.lockOutcome(pc)
                            if (r.ok) {
                                Events.record(context, pc.deviceId, EventKind.LOCKED, origin, r.via)
                                UnlockedNotice.cancel(context, pc.deviceId)
                            }
                            br.italo.italock.ui.Haptics.done(context, r.ok)
                            WidgetStatus.refreshLater(context, pc.deviceId, 1_500)
                            r.message(pc)
                        } ?: "Computador não encontrado (foi removido?)")
                    // Widget: "atualizar" consulta o cadeado só deste computador, só agora.
                    ACTION_WIDGET_REFRESH -> intent.getStringExtra(EXTRA_DEVICE)?.let { WidgetStatus.refreshNow(context, it) }
                    ACTION_WAKE -> toast(context, PairingStore(context).byDeviceId(intent.getStringExtra(EXTRA_DEVICE) ?: "")
                        ?.let { WakeOnLan.wake(it.macAddresses).also { w -> br.italo.italock.ui.Haptics.done(context, w.ok) }.message }
                        ?: "Computador não encontrado (foi removido?)")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(context: Context, msg: String) =
        android.os.Handler(context.mainLooper).post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }

    companion object {
        const val ACTION_APPROVE = "br.italo.italock.APPROVE"
        const val ACTION_DENY = "br.italo.italock.DENY"
        const val ACTION_LOCK = "br.italo.italock.LOCK"
        const val ACTION_WAKE = "br.italo.italock.WAKE"
        const val ACTION_WIDGET_REFRESH = "br.italo.italock.WIDGET_REFRESH"
        const val EXTRA_DEVICE = "deviceId"
        const val EXTRA_ORIGIN = "origin"

        /**
         * "Destravar pela notificação" (sem biometria, com a cópia da senha na chave sem biometria).
         * Bloqueante (rede): chamar fora da thread da tela, e só com o celular já destravado.
         */
        fun approveWithoutBiometric(context: Context, sessionId: String?) {
            val s = UnlockSession.active[sessionId ?: return] ?: return
            val pc = br.italo.italock.data.PairingStore(context).all().find { it.deviceId == s.pc.deviceId }
            if (pc != null && pc.notificationApprove && pc.hasAutoKey && br.italo.italock.data.AppPrefs(context).expNotification) {
                val key = runCatching { KeyGuard.openAuto(pc.autoKeyEnc, pc.autoKeyIv) }.getOrNull()
                if (key != null) s.approve(key, br.italo.italock.data.Origin.NOTIFICATION) else s.deny("APP_ERROR")
                context.getSystemService(NotificationManager::class.java)?.cancel(s.id.hashCode())
            } else {
                // A opção foi desligada depois que a notificação apareceu: o pedido continua aberto para confirmar com biometria.
                Log.i("Italock", "aprovação direta desligada para ${s.pc.displayName}: tocar na notificação para confirmar")
            }
        }
    }
}
