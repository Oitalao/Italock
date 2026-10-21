package br.italo.italock.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.EventKind
import br.italo.italock.data.Events
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import br.italo.italock.lock.LockClient
import br.italo.italock.service.PreApproval
import br.italo.italock.service.UnlockFlow
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.theme.ItalockTheme
import kotlin.concurrent.thread

/**
 * Tela transparente do atalho da barra rápida, do widget, da tag NFC e dos pedidos do relógio: exige o celular
 * destravado, pede a biometria (ou não, no modo perigoso), pré aprova e chama o PC; ou trava (perguntando antes, se a
 * opção "Pedir confirmação antes de travar" estiver ligada). Sem tela própria além de um diálogo pequeno.
 */
open class QuickUnlockActivity : GatedActivity() {
    /** Diálogo "Travar X?" aberto. */
    private var confirmLockFor by mutableStateOf<PairedPc?>(null)
    /** Texto de andamento (ex.: "Consultando X"), mostrado num cartão pequeno. */
    protected var progress by mutableStateOf<String?>(null)
    private var started = false

    /** De onde veio o pedido (widget, atalho do ícone, atalho rápido, tag, relógio, notificação): vai para o histórico. */
    protected open val origin: Origin
        get() = Origin.fromKey(intent?.getStringExtra(EXTRA_ORIGIN)) ?: Origin.WIDGET

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        setContent {
            ItalockTheme(opaqueWindow = false) {
                progress?.let { msg ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(msg, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                confirmLockFor?.let { pc ->
                    AlertDialog(
                        onDismissRequest = { confirmLockFor = null; finish() },
                        title = { Text("Travar ${pc.displayName}?") },
                        confirmButton = { QuietButton("Travar", { confirmLockFor = null; lockNow(pc) }) },
                        dismissButton = { QuietButton("Cancelar", { confirmLockFor = null; finish() }) },
                    )
                }
            }
        }
        begin()
    }

    /** Ponto de partida (a tela da tag NFC troca por outro). */
    protected open fun begin() {
        val pc = PairingStore(this).byDeviceId(intent.getStringExtra(EXTRA_DEVICE) ?: "") ?: run {
            toast("Computador não encontrado (foi removido?)"); finish(); return
        }
        UnlockFlow.cancelAsk(this, pc)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_UNLOCK
        val unverified = intent.getBooleanExtra(EXTRA_UNVERIFIED, false)
        requireUnlocked { perform(pc, action, unverified) }
    }

    /**
     * Executa a ação. [unverified]: a escolha veio de um estado não verificado, ou de um lugar que sempre exige a
     * biometria (tag NFC): o modo sem confirmação não vale.
     */
    protected fun perform(pc: PairedPc, action: String, unverified: Boolean) {
        if (started) return
        started = true
        when (action) {
            ACTION_LOCK -> if (AppPrefs(this).confirmLock) confirmLockFor = pc else lockNow(pc)
            else -> unlock(pc, unverified)
        }
    }

    private fun unlock(pc: PairedPc, unverified: Boolean) {
        // Pedido do computador já aberto: nada de pré aprovar; abre esse pedido (que pede a confirmação normal).
        PreApproval.openSession(pc.deviceId)?.let { s ->
            runCatching { startActivity(android.content.Intent(this, UnlockActivity::class.java).putExtra(UnlockActivity.EXTRA_SESSION, s.id)) }
            finish(); return
        }
        UnlockKey.obtain(this, pc, unverified, onOk = { key -> wake(pc, key, origin); finish() }, onFail = { msg ->
            if (msg != null) toast(msg)
            finish()
        })
    }

    private fun wake(pc: PairedPc, key: String, origin: Origin) {
        val app = applicationContext
        thread {
            val r = UnlockFlow.wakeWithKey(pc, key, origin)
            Haptics.done(app, r.ok)
            android.os.Handler(app.mainLooper).post { Toast.makeText(app, r.message, Toast.LENGTH_LONG).show() }
            if (r.ok) UnlockFlow.releaseLater(pc, r.token)
        }
    }

    private fun lockNow(pc: PairedPc) {
        val app = applicationContext
        val from = origin
        thread {
            val r = LockClient.lockOutcome(pc)
            if (r.ok) {
                Events.record(app, pc.deviceId, EventKind.LOCKED, from, r.via)
                br.italo.italock.service.UnlockedNotice.cancel(app, pc.deviceId)
            }
            br.italo.italock.widget.WidgetStatus.refreshLater(app, pc.deviceId, 1_500)
            Haptics.done(app, r.ok)
            val msg = r.message(pc)
            android.os.Handler(app.mainLooper).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        }
        finish()
    }

    protected fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_DEVICE = "deviceId"
        const val EXTRA_ACTION = "action"
        const val EXTRA_UNVERIFIED = "unverified"
        const val EXTRA_ORIGIN = "origin"
        const val ACTION_UNLOCK = "unlock"
        const val ACTION_LOCK = "lock"
    }
}

/** Senha do PC para o botão Destravar do celular: modo perigoso (sem confirmação) ou com rosto/digital/senha. */
object UnlockKey {
    /** [unverified]: a decisão de destravar veio de um estado não verificado; aí sempre pede a confirmação. */
    fun obtain(activity: FragmentActivity, pc: PairedPc, unverified: Boolean = false, onOk: (String) -> Unit, onFail: (String?) -> Unit) {
        // MODO PERIGOSO: só para pedidos iniciados aqui no celular, que já está destravado.
        UnlockFlow.noConfirmKey(pc, unverified)?.let { onOk(it); return }
        BioAuth.openPasswordKey(activity, pc, "Destravar ${pc.displayName}", onOk = onOk, onFail = onFail)
    }
}
