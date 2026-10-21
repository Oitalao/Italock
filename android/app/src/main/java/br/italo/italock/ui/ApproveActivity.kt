package br.italo.italock.ui

import android.os.Bundle
import br.italo.italock.service.ActionReceiver
import br.italo.italock.service.UnlockSession
import kotlin.concurrent.thread

/**
 * Android 11 ou anterior: botão "Destravar" da notificação (opção "destravar pela notificação").
 * Não existe setAuthenticationRequired nessas versões, então esta tela transparente exige o celular destravado antes.
 */
class ApproveActivity : GatedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(UnlockActivity.EXTRA_SESSION)
        if (savedInstanceState != null || UnlockSession.active[sessionId ?: ""] == null) { finish(); return }
        requireUnlocked {
            val app = applicationContext
            thread(name = "approve-notif", isDaemon = true) { ActionReceiver.approveWithoutBiometric(app, sessionId) }
            finish()
        }
    }
}
