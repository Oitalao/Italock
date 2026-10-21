package br.italo.italock.ui

import android.content.Context
import android.os.Build
import br.italo.italock.data.AppPrefs

/**
 * Vibração curta ao concluir uma ação (leve se deu certo, dupla se falhou), se "Vibrar ao concluir" estiver ligado.
 * Usada pelo app, atalho rápido, widget, tag NFC e pedidos do relógio. Nunca lança exceção.
 */
object Haptics {
    fun done(context: Context, ok: Boolean) {
        runCatching {
            if (!AppPrefs(context).vibrate) return
            val ctx = context.applicationContext
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") ctx.getSystemService(android.os.Vibrator::class.java)
            } ?: return
            if (!vibrator.hasVibrator()) return
            when {
                Build.VERSION.SDK_INT >= 29 -> vibrator.vibrate(
                    if (ok) android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                    else android.os.VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1),
                )
                Build.VERSION.SDK_INT >= 26 -> vibrator.vibrate(
                    if (ok) android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    else android.os.VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1),
                )
                else -> @Suppress("DEPRECATION") vibrator.vibrate(if (ok) longArrayOf(0, 20) else longArrayOf(0, 60, 80, 60), -1)
            }
        }
    }
}
