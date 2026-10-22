package br.italo.italock.wear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Manda uma ação (travar, destravar, ligar) ao celular e mostra a resposta. Aberta pelo tile e pela lista. */
class ActionActivity : ComponentActivity() {

    private var title by mutableStateOf("")
    private var message by mutableStateOf("")
    private var closeJob: Job? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(WearLang.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = getString(R.string.sending)
        val id = intent.getStringExtra(EXTRA_ID)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (id.isNullOrEmpty() || action == null || action !in ACTIONS) {
            finish()
            return
        }
        val name = PcStore.load(this).firstOrNull { it.id == id }?.name.orEmpty()
        title = listOf(Pc.actionLabel(this, action), name).filter { it.isNotBlank() }.joinToString(" ")

        setContent {
            ItalockTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClickLabel = getString(R.string.close), role = Role.Button) { finish() }
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CenterText(title, secondary = true)
                    CenterText(message)
                }
            }
        }

        if (savedInstanceState == null) send(id, action)
    }

    private fun send(id: String, action: String) {
        lifecycleScope.launch {
            // Assina antes de mandar, para não perder uma resposta rápida.
            val waiting = launch {
                val r = withTimeoutOrNull(TIMEOUT_MS) {
                    Bus.results.first { it.id == id && (it.action.isEmpty() || it.action == action) }
                }
                show(if (r == null) getString(R.string.phone_no_answer) else resultText(this@ActionActivity, r))
            }
            val sent = Phone.send(
                this@ActionActivity, Proto.ACTION, JSONObject().put("id", id).put("action", action),
            )
            if (!sent) {
                waiting.cancel()
                show(getString(R.string.phone_missing))
            }
        }
    }

    private fun show(text: String) {
        message = text
        closeJob?.cancel()
        closeJob = lifecycleScope.launch {
            delay(CLOSE_MS)
            finish()
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_ACTION = "action"
        private val ACTIONS = setOf("lock", "unlock", "wake")
        private const val TIMEOUT_MS = 20_000L
        private const val CLOSE_MS = 5_000L
    }
}
