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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.italo.italock.R
import br.italo.italock.i18n.L10n
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
    /** Idioma escolhido (um item de [L10n.TAGS]). */
    val language: String = L10n.DEFAULT,
) {
    companion object {
        fun from(p: AppPrefs, language: String = L10n.DEFAULT) = SettingsValues(
            p.theme, p.vibrate, p.confirmLock, p.promptTimeoutSec, p.approveWhileLocked, p.fullScreenPrompt, p.hideNameOnLockscreen,
            p.allowDeviceCredential, p.pollSec, p.tailscaleWarning, language,
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
    data class Language(val tag: String) : SettingChange
}

class SettingsActions(
    val onBack: () -> Unit,
    val onFix: (Missing) -> Unit,
    val onChange: (SettingChange) -> Unit,
    val onTilePc: (String) -> Unit,
    val onAbout: () -> Unit,
    val onExperimental: () -> Unit,
)

private enum class Picker { THEME, TIMEOUT, POLL, TILE, LANGUAGE }

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
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar(stringResource(R.string.settings_title), a.onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (missing.isNotEmpty()) {
                SectionTitle(stringResource(R.string.settings_missing))
                Panel {
                    missing.forEachIndexed { i, m ->
                        if (i > 0) ThinDivider()
                        val (text, action) = when (m) {
                            Missing.NOTIFICATIONS -> R.string.settings_missing_notifications to R.string.common_allow
                            Missing.FULL_SCREEN -> R.string.settings_missing_full_screen to R.string.common_allow
                            Missing.BATTERY -> R.string.settings_missing_battery to R.string.settings_missing_battery_action
                            Missing.BLUETOOTH -> R.string.settings_missing_bluetooth to R.string.common_allow
                        }
                        FixRow(stringResource(text), stringResource(action), { a.onFix(m) })
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_requests))
            Panel {
                SettingRow(stringResource(R.string.settings_timeout), subtitle = seconds(values.promptTimeoutSec), icon = ItalockIcons.Timer,
                    onClick = { picker = Picker.TIMEOUT })
                ThinDivider()
                SwitchRow(stringResource(R.string.settings_lockscreen_prompt),
                    stringResource(if (values.fullScreenPrompt) R.string.settings_lockscreen_prompt_on else R.string.settings_lockscreen_prompt_off),
                    values.fullScreenPrompt, { a.onChange(SettingChange.FullScreen(it)) }, icon = ItalockIcons.PhoneLock)
                SwitchRow(stringResource(R.string.settings_approve_locked),
                    stringResource(if (values.approveWhileLocked) R.string.settings_approve_locked_on else R.string.settings_approve_locked_off),
                    values.approveWhileLocked && values.fullScreenPrompt,
                    { on -> if (on) askLocked = true else a.onChange(SettingChange.ApproveWhileLocked(false)) },
                    enabled = values.fullScreenPrompt, dependent = true,
                    disabledReason = stringResource(R.string.settings_approve_locked_needs, stringResource(R.string.settings_lockscreen_prompt)))
                ThinDivider()
                SwitchRow(stringResource(R.string.settings_hide_name), stringResource(R.string.settings_hide_name_sub),
                    values.hideName, { a.onChange(SettingChange.HideName(it)) }, icon = ItalockIcons.VisibilityOff)
                ThinDivider()
                SwitchRow(stringResource(R.string.settings_allow_credential), stringResource(R.string.settings_allow_credential_sub),
                    values.allowCredential, { a.onChange(SettingChange.AllowCredential(it)) }, icon = ItalockIcons.Pin)
            }

            SectionTitle(stringResource(R.string.settings_section_lock))
            Panel {
                SwitchRow(stringResource(R.string.settings_confirm_lock), stringResource(R.string.settings_confirm_lock_sub),
                    values.confirmLock, { a.onChange(SettingChange.ConfirmLock(it)) }, icon = ItalockIcons.Security)
                if (Build.VERSION.SDK_INT >= 24 && pcs.isNotEmpty()) {
                    ThinDivider()
                    SettingRow(stringResource(R.string.settings_tile),
                        subtitle = stringResource(R.string.settings_tile_controls, tilePc?.displayName ?: stringResource(R.string.settings_tile_no_pc)),
                        icon = ItalockIcons.TouchApp, onClick = { picker = Picker.TILE })
                }
            }

            SectionTitle(stringResource(R.string.settings_section_look))
            Panel {
                SettingRow(stringResource(R.string.settings_language), subtitle = languageName(values.language), icon = ItalockIcons.Language,
                    onClick = { picker = Picker.LANGUAGE })
                ThinDivider()
                SettingRow(stringResource(R.string.settings_theme), subtitle = stringResource(values.theme.labelRes), icon = ItalockIcons.Palette,
                    onClick = { picker = Picker.THEME })
                ThinDivider()
                SwitchRow(stringResource(R.string.settings_vibrate), stringResource(R.string.settings_vibrate_sub),
                    values.vibrate, { a.onChange(SettingChange.Vibrate(it)) }, icon = ItalockIcons.Vibration)
            }

            SectionTitle(stringResource(R.string.settings_section_connection))
            Panel {
                SettingRow(stringResource(R.string.settings_poll), subtitle = pollLabel(values.pollSec), icon = ItalockIcons.Sync, onClick = { picker = Picker.POLL })
                if (tailscaleInstalled) {
                    ThinDivider()
                    SwitchRow(stringResource(R.string.settings_tailscale_warning), stringResource(R.string.settings_tailscale_warning_sub),
                        values.tailscaleWarning, { a.onChange(SettingChange.TailscaleWarning(it)) }, icon = ItalockIcons.VpnLock)
                }
            }
            Text(
                stringResource(R.string.settings_paths_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionTitle(stringResource(R.string.settings_section_more))
            Panel {
                NavRow(
                    stringResource(R.string.settings_experimental),
                    subtitle = if (experimentalOn == 0) stringResource(R.string.settings_experimental_none)
                    else pluralStringResource(R.plurals.settings_experimental_on, experimentalOn, experimentalOn),
                    icon = ItalockIcons.Science,
                    badge = { Tag(stringResource(R.string.settings_beta), Italock.colors.amberBg, Italock.colors.amberText) },
                    onClick = a.onExperimental,
                )
                ThinDivider()
                NavRow(stringResource(R.string.settings_about), subtitle = stringResource(R.string.settings_about_sub), icon = ItalockIcons.Info,
                    onClick = a.onAbout)
            }
        }
    }

    if (askLocked) AlertDialog(
        onDismissRequest = { askLocked = false },
        title = { Text(stringResource(R.string.settings_approve_locked_ask)) },
        text = {
            Text(stringResource(R.string.settings_approve_locked_ask_text, stringResource(R.string.settings_allow_credential)))
        },
        confirmButton = { QuietButton(stringResource(R.string.common_turn_on), { askLocked = false; a.onChange(SettingChange.ApproveWhileLocked(true)) }, danger = true) },
        dismissButton = { QuietButton(stringResource(R.string.common_cancel), { askLocked = false }) },
    )

    when (picker) {
        Picker.THEME -> Choice(stringResource(R.string.settings_theme), ThemeMode.entries.map { it to stringResource(it.labelRes) }, values.theme, { picker = null }) {
            a.onChange(SettingChange.Theme(it))
        }
        Picker.TIMEOUT -> Choice(stringResource(R.string.settings_timeout), AppPrefs.PROMPT_TIMEOUTS.map { it to seconds(it) }, values.promptTimeoutSec, { picker = null }) {
            a.onChange(SettingChange.PromptTimeout(it))
        }
        Picker.POLL -> Choice(stringResource(R.string.settings_poll), AppPrefs.POLL_OPTIONS.map { it to pollLabel(it) }, values.pollSec, { picker = null }) {
            a.onChange(SettingChange.Poll(it))
        }
        // Com um computador só também dá para escolher "nenhum" (o atalho fica sem ação).
        Picker.TILE -> Choice(
            stringResource(R.string.settings_tile_choose),
            pcs.map { it.deviceId to it.displayName } + (PairingStore.TILE_NONE to stringResource(R.string.common_none)),
            tilePc?.deviceId ?: PairingStore.TILE_NONE, { picker = null },
        ) { a.onTilePc(it) }
        // Cada idioma no próprio nome; Português (Brasil) é o padrão.
        Picker.LANGUAGE -> Choice(stringResource(R.string.settings_language), L10n.TAGS.map { it to languageName(it) }, values.language, { picker = null }) {
            a.onChange(SettingChange.Language(it))
        }
        null -> Unit
    }
}

@Composable
private fun languageName(tag: String): String = L10n.nativeName(tag) ?: stringResource(R.string.language_system)

@Composable
private fun seconds(sec: Int): String = pluralStringResource(R.plurals.settings_seconds, sec, sec)

@Composable
fun pollLabel(sec: Int) = if (sec == 0) stringResource(R.string.settings_poll_open)
else pluralStringResource(R.plurals.settings_poll_every, sec, sec)

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
        confirmButton = { QuietButton(stringResource(R.string.common_close), onClose) },
    )
}
