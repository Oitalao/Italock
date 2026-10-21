package br.italo.italock.watch

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import br.italo.italock.data.DiagLog
import br.italo.italock.service.UnlockSession

/**
 * Anuncia um pedido de desbloqueio ao fork do Gadgetbridge, que mostra uma "chamada falsa" no relógio Amazfit.
 * Broadcast EXPLÍCITO (setPackage): só o fork recebe, então o id da sessão não vaza para outros apps.
 * A resposta (ignorar/recusar no relógio) volta pelo [BipBridgeProvider].
 */
object BipBridge {
    const val FORK_PKG = "nodomain.freeyourgadget.gadgetbridge.voz"
    const val ACTION_PROMPT = "nodomain.freeyourgadget.gadgetbridge.voz.BIP_PROMPT"
    const val ACTION_PROMPT_END = "nodomain.freeyourgadget.gadgetbridge.voz.BIP_PROMPT_END"

    fun announce(context: Context, session: UnlockSession, hideName: Boolean) {
        val leftMs = (session.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(5_000L)
        val i = Intent(ACTION_PROMPT).setPackage(FORK_PKG)
            .putExtra("session", session.id)
            .putExtra("pc", if (hideName) "Seu computador" else session.pc.displayName)
            .putExtra("expiresMs", leftMs)
        runCatching { context.sendBroadcast(i) }
            .onFailure { DiagLog.w("ponte do relógio: anúncio falhou: ${it.javaClass.simpleName}") }
    }

    fun endPrompt(context: Context, sessionId: String) {
        val i = Intent(ACTION_PROMPT_END).setPackage(FORK_PKG).putExtra("session", sessionId)
        runCatching { context.sendBroadcast(i) }
    }
}
