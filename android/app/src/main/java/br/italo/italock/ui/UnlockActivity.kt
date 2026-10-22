package br.italo.italock.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.italo.italock.BuildConfig
import br.italo.italock.R
import br.italo.italock.i18n.L10n
import br.italo.italock.net.NetInfo
import br.italo.italock.service.UnlockService
import br.italo.italock.service.UnlockSession
import br.italo.italock.ui.components.PrimaryButton
import br.italo.italock.ui.components.SecondaryButton
import br.italo.italock.ui.theme.Italock
import br.italo.italock.ui.theme.ItalockTheme
import br.italo.italock.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlin.concurrent.thread

/**
 * Pedido que o computador iniciou: aparece por cima da tela de bloqueio e pede rosto → digital → senha.
 * Sempre exige a confirmação (inclusive com o modo "sem confirmação" ligado — ele só vale para o botão Destravar do celular).
 * Com o celular bloqueado, antes da confirmação o celular tem de ser desbloqueado, a não ser que a opção
 * "Destravar com o celular bloqueado" esteja ligada (ver [br.italo.italock.data.AppPrefs.approveWhileLocked]).
 */
class UnlockActivity : GatedActivity() {
    private var session: UnlockSession? = null

    private val endedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.getStringExtra(EXTRA_SESSION) == session?.id) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Só com "Pedido na tela de bloqueio" ligado a tela acende e aparece por cima do bloqueio. Desligado, o Android
        // pede o desbloqueio do celular antes de abrir esta tela (toque na notificação).
        if (br.italo.italock.data.AppPrefs(this).fullScreenPrompt) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        ContextCompat.registerReceiver(this, endedReceiver, IntentFilter(UnlockService.ACTION_SESSION_ENDED), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Só na versão de teste: mostra a tela com dados de exemplo, sem pedido de verdade (para conferir o visual).
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEMO, false)) {
            val started = SystemClock.elapsedRealtime()
            setContent { ItalockTheme { UnlockPrompt("Notebook", "italo", "rede local", "", started + 10_000, false, {}, {}) } }
            return
        }
        load(intent, askNow = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // O mesmo pedido de novo (toque na notificação com a tela aberta): a confirmação já está na tela, não pede outra.
        val id = intent.getStringExtra(EXTRA_SESSION)
        if (id != null && id == session?.id && session?.finished == false) return
        load(intent, askNow = true)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(endedReceiver) }
        super.onDestroy()
    }

    private fun load(intent: Intent, askNow: Boolean) {
        val s = UnlockSession.active[intent.getStringExtra(EXTRA_SESSION) ?: ""]
        if (s == null || s.finished) { finish(); return }
        session = s
        // "Windows-Login" é o normal; outro programa (ex.: sudo no Linux) aparece, para você saber quem pediu.
        val program = s.program.takeUnless { it.isBlank() || it == "Windows-Login" } ?: ""
        // Privacidade: com o celular bloqueado e a opção ligada, o nome do computador não aparece.
        val prefs = br.italo.italock.data.AppPrefs(this)
        val hide = prefs.hideNameNow(this)
        setContent {
            ItalockTheme {
                UnlockPrompt(if (hide) L10n.s(R.string.prompt_your_computer) else s.pc.displayName, if (hide) "" else s.user, NetInfo.viaLabel(s.via),
                    if (hide) "" else program, s.expiresAt, prefs.needsPhoneUnlock(this), onUnlock = { confirm(s) }, onDeny = { deny(s) })
            }
        }
        if (askNow) confirm(s)
    }

    /**
     * Celular bloqueado e a opção "Destravar com o celular bloqueado" desligada: primeiro o desbloqueio do próprio
     * celular (a tela do Android), depois a biometria do pedido. Desistiu do desbloqueio: a tela fica com os botões.
     */
    private fun confirm(s: UnlockSession) {
        if (!br.italo.italock.data.AppPrefs(this).needsPhoneUnlock(this)) { authenticate(s); return }
        requireUnlocked(onCancel = {}) { if (!s.finished) authenticate(s) }
    }

    /** Recusar manda a resposta pela rede: fora da thread da tela. */
    private fun deny(s: UnlockSession, error: String = "CANCEL") {
        thread(name = "deny", isDaemon = true) { s.deny(error) }
        finish()
    }

    private fun authenticate(s: UnlockSession) {
        BioAuth.openPasswordKey(this, s.pc, L10n.s(R.string.bio_unlock_title, s.pc.displayName), s.user,
            onOk = { key ->
                // O celular voltou a bloquear entre o desbloqueio e a biometria: não vale.
                if (br.italo.italock.data.AppPrefs(this).needsPhoneUnlock(this)) {
                    Toast.makeText(this, L10n.s(R.string.prompt_unlock_phone_first), Toast.LENGTH_LONG).show()
                    return@openPasswordKey
                }
                thread(name = "approve", isDaemon = true) {
                    val alreadyAnswered = s.finished
                    val sent = s.approve(key)
                    runOnUiThread {
                        if (!sent && !alreadyAnswered) Toast.makeText(this, L10n.s(R.string.prompt_reply_failed, s.pc.displayName), Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            },
            onFail = { msg ->
                // Cancelou: a tela fica com os botões para tentar de novo ou recusar.
                if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            },
        )
    }

    companion object {
        const val EXTRA_SESSION = "session"
        const val EXTRA_DEMO = "demo"
    }
}

/**
 * A tela do pedido: nome do computador grande, a pergunta, Destravar (laranja) e Recusar; tempo restante discreto.
 * [via]: rótulo interno do caminho (NetInfo.viaLabel); aparece no idioma escolhido.
 */
@Composable
fun UnlockPrompt(
    name: String,
    user: String,
    via: String,
    program: String,
    expiresAt: Long,
    needsPhoneUnlock: Boolean,
    onUnlock: () -> Unit,
    onDeny: () -> Unit,
) {
    var left by remember(expiresAt) { mutableLongStateOf(secondsLeft(expiresAt)) }
    LaunchedEffect(expiresAt) { while (left > 0) { delay(1000); left = secondsLeft(expiresAt) } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                if (left > 0) stringResource(R.string.prompt_expires_in, left / 60, left % 60) else stringResource(R.string.prompt_expired),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Italock.colors.lockedIcon, modifier = Modifier.size(40.dp))
                Spacer(Modifier.padding(top = 20.dp))
                Text(
                    name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.prompt_wants_unlock), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(top = 16.dp))
                Text(
                    listOf(user.takeIf { it.isNotBlank() }?.let { stringResource(R.string.prompt_user, it) },
                        stringResource(R.string.prompt_via, L10n.via(via))).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                if (program.isNotEmpty()) Text(
                    program, style = MaterialTheme.typography.bodySmall, fontFamily = Mono, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (needsPhoneUnlock) Text(
                stringResource(R.string.prompt_unlock_phone_to_unlock), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            PrimaryButton(stringResource(R.string.action_unlock), onUnlock, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
            Spacer(Modifier.padding(top = 8.dp))
            SecondaryButton(stringResource(R.string.prompt_deny), onDeny, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
        }
    }
}

private fun secondsLeft(expiresAt: Long): Long =
    ((expiresAt - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0)
