package br.italo.italock.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import br.italo.italock.data.ThemeMode
import br.italo.italock.ui.components.FixRow
import br.italo.italock.ui.components.NavRow
import br.italo.italock.ui.components.Tag
import br.italo.italock.ui.theme.Italock
import br.italo.italock.ui.theme.ItalockIcons
import br.italo.italock.ui.components.InnerTopBar
import br.italo.italock.ui.components.Panel
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.components.SectionTitle
import br.italo.italock.ui.components.SettingRow
import br.italo.italock.ui.components.SwitchRow
import br.italo.italock.ui.components.ThinDivider

/** Valores das opções gerais, lidos das preferências a cada volta à tela. */
data class SettingsValues(
    val theme: ThemeMode = ThemeMode.DARK,
    val vibrate: Boolean = true,
    val confirmLock: Boolean = false,
    val promptTimeoutSec: Int = AppPrefs.PROMPT_TIMEOUT_DEFAULT,
    val approveWhileLocked: Boolean = false,
    val fullScreenPrompt: Boolean = false,
    val hideName: Boolean = false,
    val allowCredential: Boolean = true,
    val pollSec: Int = 15,
    val tailscaleWarning: Boolean = true,
) {
    companion object {
        fun from(p: AppPrefs) = SettingsValues(
            p.theme, p.vibrate, p.confirmLock, p.promptTimeoutSec, p.approveWhileLocked, p.fullScreenPrompt, p.hideNameOnLockscreen,
            p.allowDeviceCredential, p.pollSec, p.tailscaleWarning,
        )
    }
}

/** Uma opção mudada na tela de Configurações. */
sealed interface SettingChange {
    data class Theme(val v: ThemeMode) : SettingChange
    data class Vibrate(val v: Boolean) : SettingChange
    data class ConfirmLock(val v: Boolean) : SettingChange
    data class PromptTimeout(val sec: Int) : SettingChange
    data class ApproveWhileLocked(val v: Boolean) : SettingChange
    data class FullScreen(val v: Boolean) : SettingChange
    data class HideName(val v: Boolean) : SettingChange
    data class AllowCredential(val v: Boolean) : SettingChange
    data class Poll(val sec: Int) : SettingChange
    data class TailscaleWarning(val v: Boolean) : SettingChange
}

class SettingsActions(
    val onBack: () -> Unit,
    val onFix: (Missing) -> Unit,
    val onChange: (SettingChange) -> Unit,
    val onTilePc: (String) -> Unit,
    val onAbout: () -> Unit,
    val onExperimental: () -> Unit,
)

private enum class Picker { THEME, TIMEOUT, POLL, TILE }

@Composable
fun SettingsScreen(
    missing: List<Missing>,
    values: SettingsValues,
    tailscaleInstalled: Boolean,
    pcs: List<PairedPc>,
    tileDeviceId: String,
    openTileChooser: Boolean,
    experimentalOn: Int,
    a: SettingsActions,
) {
    var picker by rememberSaveable { mutableStateOf(if (openTileChooser) Picker.TILE else null) }
    var askLocked by rememberSaveable { mutableStateOf(false) }
    val tilePc = PairingStore.tilePcOf(pcs, tileDeviceId)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar("Configurações", a.onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (missing.isNotEmpty()) {
                SectionTitle("Falta permitir")
                Panel {
                    missing.forEachIndexed { i, m ->
                        if (i > 0) ThinDivider()
                        val (text, action) = when (m) {
                            Missing.NOTIFICATIONS -> "Notificações: sem elas, os pedidos do computador não aparecem." to "Permitir"
                            Missing.FULL_SCREEN -> "Tela cheia: para o pedido aparecer com o celular bloqueado." to "Permitir"
                            Missing.BATTERY -> "Economia de bateria: o Android pode fechar o Ítalock." to "Liberar"
                            Missing.BLUETOOTH -> "Bluetooth: para o computador chamar o celular sem rede." to "Permitir"
                        }
                        FixRow(text, action, { a.onFix(m) })
                    }
                }
            }

            SectionTitle("Pedidos do computador")
            Panel {
                SettingRow("Tempo para responder", subtitle = "${values.promptTimeoutSec} segundos", icon = ItalockIcons.Timer,
                    onClick = { picker = Picker.TIMEOUT })
                ThinDivider()
                SwitchRow("Pedido na tela de bloqueio",
                    if (values.fullScreenPrompt) "Acende a tela e aparece por cima do bloqueio. Gasta mais bateria."
                    else "Só notificação, sem acender a tela. Para responder, desbloqueie o celular.",
                    values.fullScreenPrompt, { a.onChange(SettingChange.FullScreen(it)) }, icon = ItalockIcons.PhoneLock)
                SwitchRow("Destravar com o celular bloqueado",
                    if (values.approveWhileLocked) "Aceita o pedido na tela de bloqueio, sem desbloquear o celular. Menos seguro."
                    else "Desligado: desbloqueie o celular antes de aceitar.",
                    values.approveWhileLocked && values.fullScreenPrompt,
                    { on -> if (on) askLocked = true else a.onChange(SettingChange.ApproveWhileLocked(false)) },
                    enabled = values.fullScreenPrompt, dependent = true,
                    disabledReason = "Ligue antes \"Pedido na tela de bloqueio\"")
                ThinDivider()
                SwitchRow("Esconder o nome do computador", "Com o celular bloqueado, pedidos e avisos não dizem qual é.",
                    values.hideName, { a.onChange(SettingChange.HideName(it)) }, icon = ItalockIcons.VisibilityOff)
                ThinDivider()
                SwitchRow("Aceitar PIN, padrão ou senha", "Além da biometria. Desligado: só digital ou rosto seguro.",
                    values.allowCredential, { a.onChange(SettingChange.AllowCredential(it)) }, icon = ItalockIcons.Pin)
            }

            SectionTitle("Travar")
            Panel {
                SwitchRow("Pedir confirmação antes de travar", "Pergunta antes de travar por app, widget, atalhos, tag, relógio ou notificação.",
                    values.confirmLock, { a.onChange(SettingChange.ConfirmLock(it)) }, icon = ItalockIcons.Security)
                if (Build.VERSION.SDK_INT >= 24 && pcs.isNotEmpty()) {
                    ThinDivider()
                    SettingRow("Atalho rápido", subtitle = "Controla: ${tilePc?.displayName ?: "nenhum computador"}", icon = ItalockIcons.TouchApp,
                        onClick = { picker = Picker.TILE })
                }
            }

            SectionTitle("Aparência e aviso")
            Panel {
                SettingRow("Aparência", subtitle = values.theme.label, icon = ItalockIcons.Palette, onClick = { picker = Picker.THEME })
                ThinDivider()
                SwitchRow("Vibrar ao concluir", "Curta quando dá certo, dupla quando falha.",
                    values.vibrate, { a.onChange(SettingChange.Vibrate(it)) }, icon = ItalockIcons.Vibration)
            }

            SectionTitle("Conexão")
            Panel {
                SettingRow("Atualizar o cadeado", subtitle = pollLabel(values.pollSec), icon = ItalockIcons.Sync, onClick = { picker = Picker.POLL })
                if (tailscaleInstalled) {
                    ThinDivider()
                    SwitchRow("Avisar quando o Tailscale desligar", "Sem ele, destravar fora de casa não funciona.",
                        values.tailscaleWarning, { a.onChange(SettingChange.TailscaleWarning(it)) }, icon = ItalockIcons.VpnLock)
                }
            }
            Text(
                "Rede local, Bluetooth e Tailscale se escolhem por computador, nos detalhes dele.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionTitle("Mais")
            Panel {
                NavRow(
                    "Recursos experimentais",
                    subtitle = when (experimentalOn) { 0 -> "Nenhum ligado"; 1 -> "1 ligado"; else -> "$experimentalOn ligados" },
                    icon = ItalockIcons.Science,
                    badge = { Tag("BETA", Italock.colors.amberBg, Italock.colors.amberText) },
                    onClick = a.onExperimental,
                )
                ThinDivider()
                NavRow("Sobre o Ítalock", subtitle = "Créditos, licença, detalhes técnicos e registros", icon = ItalockIcons.Info, onClick = a.onAbout)
            }
        }
    }

    if (askLocked) AlertDialog(
        onDismissRequest = { askLocked = false },
        title = { Text("Destravar com o celular bloqueado?") },
        text = {
            Text("O computador poderá ser destravado direto da tela de bloqueio do celular, com a biometria " +
                "(ou o PIN, se \"Aceitar PIN, padrão ou senha\" estiver ligado), sem desbloquear o celular. É mais cômodo e menos seguro.")
        },
        confirmButton = { QuietButton("Ligar", { askLocked = false; a.onChange(SettingChange.ApproveWhileLocked(true)) }, danger = true) },
        dismissButton = { QuietButton("Cancelar", { askLocked = false }) },
    )

    when (picker) {
        Picker.THEME -> Choice("Aparência", ThemeMode.entries.map { it to it.label }, values.theme, { picker = null }) {
            a.onChange(SettingChange.Theme(it))
        }
        Picker.TIMEOUT -> Choice("Tempo para responder", AppPrefs.PROMPT_TIMEOUTS.map { it to "$it segundos" }, values.promptTimeoutSec, { picker = null }) {
            a.onChange(SettingChange.PromptTimeout(it))
        }
        Picker.POLL -> Choice("Atualizar o cadeado", AppPrefs.POLL_OPTIONS.map { it to pollLabel(it) }, values.pollSec, { picker = null }) {
            a.onChange(SettingChange.Poll(it))
        }
        // Com um computador só também dá para escolher "nenhum" (o atalho fica sem ação).
        Picker.TILE -> Choice(
            "Atalho rápido controla",
            pcs.map { it.deviceId to it.displayName } + (PairingStore.TILE_NONE to "Nenhum"),
            tilePc?.deviceId ?: PairingStore.TILE_NONE, { picker = null },
        ) { a.onTilePc(it) }
        null -> Unit
    }
}

fun pollLabel(sec: Int) = if (sec == 0) "Só ao abrir o app" else "A cada $sec segundos com o app aberto"

@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onClose: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(value == selected, role = Role.RadioButton) { onClose(); onPick(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { QuietButton("Fechar", onClose) },
    )
}
