package br.italo.italock.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity

/**
 * Tela transparente que só age com o celular DESTRAVADO: se estiver na tela de bloqueio, pede o desbloqueio primeiro.
 * Android 8+: requestDismissKeyguard. Android 6–7: flags da janela e confere quando a tela ganha o foco.
 */
abstract class GatedActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(br.italo.italock.i18n.L10n.wrap(newBase))

    private var pending: (() -> Unit)? = null
    private var cancelled: () -> Unit = { finish() }
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { if (pending != null) { pending = null; cancelled() } }

    /** [onCancel]: a pessoa desistiu do desbloqueio (padrão: fecha a tela). */
    protected fun requireUnlocked(onCancel: () -> Unit = { finish() }, onUnlocked: () -> Unit) {
        cancelled = onCancel
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) { onUnlocked(); return }
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (Build.VERSION.SDK_INT >= 26) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = onUnlocked()
                override fun onDismissCancelled() = onCancel()
                override fun onDismissError() = onCancel()
            })
        } else {
            pending = onUnlocked
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            handler.postDelayed(giveUp, 60_000)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val action = pending ?: return
        if (hasFocus && getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false) {
            pending = null
            handler.removeCallbacks(giveUp)
            action()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        super.onDestroy()
    }
}
