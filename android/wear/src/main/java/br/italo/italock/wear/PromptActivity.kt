package br.italo.italock.wear

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tela do pedido de destravar. Fecha sozinha quando vence ou quando o pedido é respondido em outro lugar. */
class PromptActivity : ComponentActivity() {

    private var prompt by mutableStateOf<Prompt?>(null)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(WearLang.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prompt = read(intent)
        if (prompt == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            Bus.promptEnded.collect { s -> if (s == prompt?.session) finish() }
        }
        setContent {
            ItalockTheme {
                prompt?.let { p ->
                    LaunchedEffect(p.session, p.expiresAt) {
                        delay((p.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                        PromptNotifier.cancel(this@PromptActivity, p.session)
                        finish()
                    }
                    PromptScreen(p, watchHasLock(this), ::answer)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)?.let { prompt = it }
        if (intent.getBooleanExtra(PromptNotifier.EXTRA_APPROVE, false)) answer(true)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Botão Destravar da notificação: abre esta tela, que pede para destravar o relógio antes de aprovar.
        if (savedInstanceState == null && intent.getBooleanExtra(PromptNotifier.EXTRA_APPROVE, false)) answer(true)
    }

    private fun read(i: Intent?): Prompt? {
        val s = i?.getStringExtra(PromptNotifier.EXTRA_SESSION) ?: return null
        val p = Prompt(
            session = s,
            pc = i.getStringExtra(PromptNotifier.EXTRA_PC).orEmpty(),
            user = i.getStringExtra(PromptNotifier.EXTRA_USER).orEmpty(),
            expiresAt = i.getLongExtra(PromptNotifier.EXTRA_EXPIRES, 0),
        )
        return if (p.expiresAt > System.currentTimeMillis()) p else null
    }

    private fun answer(approve: Boolean) {
        val p = prompt ?: return
        if (approve && !isWatchSecure(this)) {
            if (!watchHasLock(this)) return
            // Relógio travado: pede o PIN/padrão do relógio e só aprova depois de destravar.
            val km = getSystemService(KeyguardManager::class.java) ?: return
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    if (isWatchSecure(this@PromptActivity)) send(p, true)
                }
            })
            return
        }
        send(p, approve)
    }

    private fun send(p: Prompt, approve: Boolean) {
        Phone.answer(this, p.session, approve)
        PromptNotifier.cancel(this, p.session)
        finish()
    }
}

@Composable
private fun PromptScreen(p: Prompt, secure: Boolean, onAnswer: (Boolean) -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                if (p.pc.isNotBlank()) stringResource(R.string.prompt_title, p.pc) else stringResource(R.string.prompt_title_generic),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title2,
                color = Ink.text,
            )
        }
        if (p.user.isNotBlank()) item { CenterText(p.user, secondary = true) }
        if (secure) {
            item {
                WideButton(stringResource(R.string.action_unlock), primary = true, onClick = { onAnswer(true) }, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            item {
                CenterText(
                    stringResource(R.string.prompt_needs_lock),
                    modifier = Modifier.padding(top = 4.dp),
                    secondary = true,
                )
            }
        }
        item { WideButton(stringResource(R.string.action_deny), primary = false, onClick = { onAnswer(false) }) }
    }
}
