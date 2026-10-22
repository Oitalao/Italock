package br.italo.italock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import br.italo.italock.ui.theme.ItalockIcons
import br.italo.italock.R
import br.italo.italock.i18n.L10n
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.italo.italock.data.PairedPc
import br.italo.italock.data.HistoryEvent
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.WakeOnLan
import br.italo.italock.net.NetInfo
import br.italo.italock.ui.components.DangerStrip
import br.italo.italock.ui.components.InnerTopBar
import br.italo.italock.ui.components.Panel
import br.italo.italock.ui.components.PrimaryButton
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.components.SecondaryButton
import br.italo.italock.ui.components.SectionTitle
import br.italo.italock.ui.components.SettingRow
import br.italo.italock.ui.components.StatusIcon
import br.italo.italock.ui.components.SwitchRow
import br.italo.italock.ui.components.ThinDivider
import br.italo.italock.ui.components.statusLook
import br.italo.italock.ui.theme.Italock
import br.italo.italock.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quais recursos experimentais estão ligados (as opções por computador só aparecem para esses). */
data class ExperimentalFlags(val nearby: Boolean, val notification: Boolean, val watch: Boolean, val wear: Boolean) {
    val any get() = nearby || notification || watch || wear
}

/** Caminho de conexão (rede local, Bluetooth, Tailscale). */
enum class PathKind { LAN, BLUETOOTH, TAILSCALE }

class DetailsActions(
    val onBack: () -> Unit,
    val onMain: (RowAction) -> Unit,
    val onRename: (String) -> Unit,
    val onMove: (Int) -> Unit,
    val onTile: (Boolean) -> Unit,
    val onWake: () -> Unit,
    val onMacs: (List<String>) -> Unit,
    val onPath: (PathKind, Boolean) -> Unit,
    val onWriteTag: () -> Unit,
    val onTagLock: (Boolean) -> Unit,
    val onTagUnlock: (Boolean) -> Unit,
    val onClearHistory: () -> Unit,
    val onNoConfirm: (Boolean) -> Unit,
    val onNearby: (Boolean) -> Unit,
    val onNotification: (Boolean) -> Unit,
    val onWatch: (Boolean) -> Unit,
    val onWear: (Boolean) -> Unit,
    val onRemove: () -> Unit,
)

@Composable
fun DetailsScreen(
    pc: PairedPc,
    index: Int,
    count: Int,
    status: LockClient.StatusResult?,
    busy: Boolean,
    needsRepair: Boolean,
    isTile: Boolean,
    /** Últimos eventos deste computador (null = ainda lendo). */
    history: List<HistoryEvent>?,
    hasNfc: Boolean,
    experimental: ExperimentalFlags,
    snackbar: SnackbarHostState,
    a: DetailsActions,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingMac by rememberSaveable { mutableStateOf(false) }
    var allHistory by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var technical by rememberSaveable { mutableStateOf(false) }
    val look = statusLook(status?.status)
    val action = rowAction(pc, status?.status, needsRepair)
    // Endereços do celular: só com "Detalhes técnicos" aberto, e fora da tela (consulta as interfaces de rede).
    var phoneIps by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(technical) { if (technical) phoneIps = withContext(Dispatchers.IO) { NetInfo.allIpv4() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { InnerTopBar(pc.displayName, a.onBack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Estado + a ação principal (a única laranja da tela).
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(look.tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    StatusIcon(status?.status, size = 28)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (needsRepair) stringResource(R.string.details_repair_needed) else unverified(look.label, status),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        color = if (needsRepair) Italock.colors.dangerText else look.textColor)
                    status?.detail?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    lastEventText(pc)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
            }
            if (pc.noConfirmActive) DangerStrip(modifier = Modifier.padding(start = 82.dp, bottom = 4.dp))
            if (action != null) PrimaryButton(
                action.label, { a.onMain(action) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            SectionTitle(stringResource(R.string.details_section_general))
            Panel {
                SettingRow(stringResource(R.string.details_name), subtitle = pc.displayName, icon = ItalockIcons.Edit, onClick = { renaming = true }) {
                    Text(stringResource(R.string.common_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                ThinDivider()
                SettingRow(stringResource(R.string.details_order), subtitle = stringResource(R.string.details_order_value, index + 1, count), icon = ItalockIcons.SwapVert) {
                    IconButton(onClick = { a.onMove(-1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.details_move_up))
                    }
                    IconButton(onClick = { a.onMove(+1) }, enabled = index < count - 1) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.details_move_down))
                    }
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    ThinDivider()
                    SwitchRow(stringResource(R.string.details_tile), stringResource(R.string.details_tile_sub), isTile, a.onTile, icon = ItalockIcons.TouchApp)
                }
                ThinDivider()
                if (pc.macAddresses.isNotEmpty()) {
                    SettingRow(stringResource(R.string.details_wake), subtitle = stringResource(R.string.details_wake_sub), icon = ItalockIcons.Power) {
                        SecondaryButton(stringResource(R.string.action_wake), a.onWake)
                    }
                    ThinDivider()
                }
                SettingRow(
                    stringResource(R.string.details_mac),
                    subtitle = pc.macAddresses.joinToString(", ").ifEmpty { stringResource(R.string.details_mac_empty) },
                    icon = ItalockIcons.Label,
                    onClick = { editingMac = true },
                ) { Text(stringResource(R.string.common_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }

            SectionTitle(stringResource(R.string.details_section_paths))
            Panel {
                SwitchRow(stringResource(R.string.details_path_lan), stringResource(R.string.details_path_lan_sub), pc.allowLan, { a.onPath(PathKind.LAN, it) }, icon = ItalockIcons.Wifi)
                ThinDivider()
                SwitchRow("Bluetooth", stringResource(R.string.details_path_bt_sub), pc.allowBluetooth, { a.onPath(PathKind.BLUETOOTH, it) }, icon = ItalockIcons.Bluetooth)
                ThinDivider()
                SwitchRow("Tailscale", stringResource(R.string.details_path_ts_sub), pc.allowTailscale, { a.onPath(PathKind.TAILSCALE, it) }, icon = ItalockIcons.VpnLock)
            }

            if (hasNfc) {
                SectionTitle(stringResource(R.string.details_section_tag))
                Panel {
                    SettingRow(stringResource(R.string.details_tag_write), subtitle = tagSubtitle(pc), icon = ItalockIcons.Nfc) {
                        SecondaryButton(stringResource(R.string.details_tag_write_button), a.onWriteTag)
                    }
                    ThinDivider()
                    SwitchRow(stringResource(R.string.details_tag_lock), stringResource(R.string.details_tag_lock_sub), pc.tagLock, a.onTagLock, icon = ItalockIcons.Security)
                    ThinDivider()
                    SwitchRow(stringResource(R.string.details_tag_unlock), stringResource(R.string.details_tag_unlock_sub), pc.tagUnlock, a.onTagUnlock, icon = ItalockIcons.Fingerprint)
                }
            }

            SectionTitle(stringResource(R.string.details_section_security))
            Panel {
                SwitchRow(
                    stringResource(R.string.details_no_confirm),
                    stringResource(R.string.details_no_confirm_sub),
                    pc.noConfirmActive, a.onNoConfirm, danger = true, icon = ItalockIcons.Fingerprint,
                )
            }

            if (experimental.any) {
                SectionTitle(stringResource(R.string.details_section_experimental))
                Panel {
                    var first = true
                    fun sep(): Boolean { val show = !first; first = false; return show }
                    if (experimental.nearby) {
                        if (sep()) ThinDivider()
                        SwitchRow(stringResource(R.string.exp_nearby), stringResource(R.string.details_exp_nearby_sub),
                            pc.autoUnlock && pc.hasAutoKey, a.onNearby, icon = ItalockIcons.Bluetooth)
                    }
                    if (experimental.notification) {
                        if (sep()) ThinDivider()
                        SwitchRow(stringResource(R.string.exp_notification), stringResource(R.string.details_exp_notification_sub),
                            pc.notificationApprove && pc.hasAutoKey, a.onNotification, icon = ItalockIcons.Notify)
                    }
                    if (experimental.watch) {
                        if (sep()) ThinDivider()
                        SwitchRow(stringResource(R.string.exp_watch), stringResource(R.string.details_exp_watch_sub),
                            pc.watchApprove && pc.hasAutoKey, a.onWatch, icon = ItalockIcons.LockClock)
                    }
                    if (experimental.wear) {
                        if (sep()) ThinDivider()
                        SwitchRow(stringResource(R.string.exp_wear), stringResource(R.string.details_exp_wear_sub),
                            pc.wearApprove && pc.hasAutoKey, a.onWear, icon = ItalockIcons.LockClock)
                    }
                }
            }

            SectionTitle(stringResource(R.string.details_section_history))
            Panel {
                val events = history.orEmpty()
                if (history != null && events.isEmpty()) {
                    Text(stringResource(R.string.details_history_empty),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp))
                }
                val shown = if (allHistory) events else events.take(HISTORY_PREVIEW)
                shown.forEachIndexed { i, e ->
                    if (i > 0) ThinDivider()
                    HistoryRow(e)
                }
                if (events.isNotEmpty()) {
                    ThinDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (events.size > HISTORY_PREVIEW) {
                            QuietButton(if (allHistory) stringResource(R.string.details_history_less) else stringResource(R.string.details_history_all, events.size), { allHistory = !allHistory })
                        }
                        Spacer(Modifier.weight(1f))
                        QuietButton(stringResource(R.string.details_history_clear), a.onClearHistory)
                    }
                }
            }

            Spacer(Modifier.padding(top = 16.dp))
            val techState = stringResource(if (technical) R.string.common_expanded else R.string.common_collapsed)
            Panel {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = stringResource(if (technical) R.string.common_collapse else R.string.common_show)) { technical = !technical }
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp)
                        .semantics { stateDescription = techState },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.common_technical_details), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(if (technical) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (technical) {
                    ThinDivider()
                    Column(Modifier.padding(16.dp)) {
                        TechLine(stringResource(R.string.details_tech_pc_name), pc.pcName)
                        TechLine(stringResource(R.string.details_tech_user), pc.userName)
                        TechLine(stringResource(R.string.details_tech_os), pc.pcOs)
                        TechLine(stringResource(R.string.details_tech_method), pc.pairingMethod)
                        TechLine(stringResource(R.string.details_tech_pc_addresses), pc.pcIps.ifEmpty { listOf(pc.pcIp) }.joinToString("\n"), mono = true)
                        TechLine(stringResource(R.string.details_tech_phone_addresses), phoneIps.joinToString("\n").ifEmpty { stringResource(R.string.common_none) }, mono = true)
                        TechLine(stringResource(R.string.details_tech_response), stringResource(if (pc.sealedSeen) R.string.details_tech_sealed else R.string.details_tech_plain))
                        TechLine(stringResource(R.string.details_tech_paired_at), L10n.dateTime(pc.pairedAt))
                        TechLine(stringResource(R.string.details_tech_public_id), pc.publicId, mono = true)
                        TechLine(stringResource(R.string.details_tech_identifier), pc.deviceId, mono = true, last = true)
                    }
                }
            }

            QuietButton(stringResource(R.string.details_remove_pc), { confirmRemove = true }, danger = true, modifier = Modifier.padding(start = 8.dp, top = 16.dp))
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(stringResource(R.string.details_remove_title, pc.displayName)) },
        text = { Text(stringResource(R.string.details_remove_text)) },
        confirmButton = { QuietButton(stringResource(R.string.common_remove), { confirmRemove = false; a.onRemove() }, danger = true) },
        dismissButton = { QuietButton(stringResource(R.string.common_cancel), { confirmRemove = false }) },
    )
    if (renaming) {
        var name by remember { mutableStateOf(pc.nickname) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.details_rename_title)) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    placeholder = { Text(pc.pcName) }, supportingText = { Text(stringResource(R.string.details_rename_hint)) },
                )
            },
            confirmButton = { QuietButton(stringResource(R.string.common_save), { renaming = false; a.onRename(name) }) },
            dismissButton = { QuietButton(stringResource(R.string.common_cancel), { renaming = false }) },
        )
    }
    if (editingMac) {
        var text by remember { mutableStateOf(pc.macAddresses.joinToString(", ")) }
        val parts = text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val valid = parts.all { WakeOnLan.parseMac(it) != null }
        AlertDialog(
            onDismissRequest = { editingMac = false },
            title = { Text(stringResource(R.string.details_mac)) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(120) }, singleLine = true, isError = !valid,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    supportingText = { Text(stringResource(if (valid) R.string.details_mac_help else R.string.details_mac_invalid)) },
                )
            },
            confirmButton = { QuietButton(stringResource(R.string.common_save), { editingMac = false; a.onMacs(parts) }, enabled = valid) },
            dismissButton = { QuietButton(stringResource(R.string.common_cancel), { editingMac = false }) },
        )
    }
}

private const val HISTORY_PREVIEW = 5

/** O que a tag faz, pelos dois interruptores. */
@Composable
private fun tagSubtitle(pc: PairedPc): String = stringResource(when {
    pc.tagLock && pc.tagUnlock -> R.string.details_tag_both
    pc.tagUnlock -> R.string.details_tag_only_unlock
    pc.tagLock -> R.string.details_tag_only_lock
    else -> R.string.details_tag_none
})

/** Uma linha do histórico: o que houve e, embaixo, quando, de onde e por qual caminho. */
@Composable
private fun HistoryRow(e: HistoryEvent) {
    val detail = listOf(whenText(e.at), e.origin.label, L10n.via(e.via)).filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp)
        .semantics(mergeDescendants = true) {}) {
        Text(e.kind.label, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Diálogo da gravação da tag NFC (a leitura fica ligada enquanto ele estiver aberto). */
@Composable
fun NfcWriteDialog(
    name: String,
    nfcOff: Boolean,
    makeReadOnly: Boolean,
    message: String?,
    done: Boolean,
    onMakeReadOnly: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (nfcOff) stringResource(R.string.details_nfc_off_title) else stringResource(R.string.details_nfc_write_title, name)) },
        text = {
            Column {
                if (nfcOff) {
                    Text(stringResource(R.string.details_nfc_off_text))
                } else {
                    Text(if (done) message.orEmpty() else stringResource(R.string.details_nfc_hold))
                    if (!done) {
                        message?.let { Text(it, color = Italock.colors.dangerText, modifier = Modifier.padding(top = 8.dp)) }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
                                .toggleable(makeReadOnly, role = Role.Checkbox) { onMakeReadOnly(it) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = makeReadOnly, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(stringResource(R.string.details_nfc_readonly))
                                Text(stringResource(R.string.details_nfc_readonly_warn),
                                    style = MaterialTheme.typography.bodySmall, color = Italock.colors.dangerText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (nfcOff) QuietButton(stringResource(R.string.details_nfc_open_settings), onOpenSettings) else QuietButton(stringResource(if (done) R.string.common_close else R.string.common_cancel), onClose)
        },
        dismissButton = { if (nfcOff) QuietButton(stringResource(R.string.common_cancel), onClose) },
    )
}

@Composable
private fun TechLine(label: String, value: String, mono: Boolean = false, last: Boolean = false) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value.ifBlank { stringResource(R.string.common_not_informed) }, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) Mono else null,
        modifier = Modifier.padding(bottom = if (last) 0.dp else 12.dp))
}
