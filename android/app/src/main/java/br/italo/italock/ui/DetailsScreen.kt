package br.italo.italock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import br.italo.italock.ui.theme.ItalockIcons
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
                    Text(if (needsRepair) "Pareie de novo: a senha guardada se perdeu" else look.label + unverifiedSuffix(status),
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

            SectionTitle("Geral")
            Panel {
                SettingRow("Nome", subtitle = pc.displayName, icon = ItalockIcons.Edit, onClick = { renaming = true }) {
                    Text("Mudar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                ThinDivider()
                SettingRow("Ordem na lista", subtitle = "${index + 1}º de $count", icon = ItalockIcons.SwapVert) {
                    IconButton(onClick = { a.onMove(-1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Subir na lista")
                    }
                    IconButton(onClick = { a.onMove(+1) }, enabled = index < count - 1) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Descer na lista")
                    }
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    ThinDivider()
                    SwitchRow("Usar no atalho rápido", "O botão Ítalock dos ajustes rápidos controla este computador.", isTile, a.onTile, icon = ItalockIcons.TouchApp)
                }
                ThinDivider()
                if (pc.macAddresses.isNotEmpty()) {
                    SettingRow("Ligar pela rede", subtitle = "Só na mesma rede, com o computador no cabo e a opção ligada nele.", icon = ItalockIcons.Power) {
                        SecondaryButton("Ligar", a.onWake)
                    }
                    ThinDivider()
                }
                SettingRow(
                    "Endereço da placa com cabo",
                    subtitle = pc.macAddresses.joinToString(", ").ifEmpty { "Não informado. Sem ele não dá para ligar pela rede." },
                    icon = ItalockIcons.Label,
                    onClick = { editingMac = true },
                ) { Text("Mudar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }

            SectionTitle("Caminhos")
            Panel {
                SwitchRow("Rede local", "Mesma rede Wi-Fi ou cabo USB com ancoragem.", pc.allowLan, { a.onPath(PathKind.LAN, it) }, icon = ItalockIcons.Wifi)
                ThinDivider()
                SwitchRow("Bluetooth", "O computador chama o celular sem rede.", pc.allowBluetooth, { a.onPath(PathKind.BLUETOOTH, it) }, icon = ItalockIcons.Bluetooth)
                ThinDivider()
                SwitchRow("Tailscale", "Fora de casa, pela internet.", pc.allowTailscale, { a.onPath(PathKind.TAILSCALE, it) }, icon = ItalockIcons.VpnLock)
            }

            if (hasNfc) {
                SectionTitle("Tag NFC")
                Panel {
                    SettingRow("Gravar tag NFC", subtitle = tagSubtitle(pc), icon = ItalockIcons.Nfc) {
                        SecondaryButton("Gravar", a.onWriteTag)
                    }
                    ThinDivider()
                    SwitchRow("Travar pela tag", "Encostar o celular desbloqueado trava este computador.", pc.tagLock, a.onTagLock, icon = ItalockIcons.Security)
                    ThinDivider()
                    SwitchRow("Destravar pela tag", "Sempre pede a biometria.", pc.tagUnlock, a.onTagUnlock, icon = ItalockIcons.Fingerprint)
                }
            }

            SectionTitle("Segurança")
            Panel {
                SwitchRow(
                    "Destravar sem confirmação",
                    "O botão Destravar do celular não pede biometria. Pedidos do computador continuam pedindo.",
                    pc.noConfirmActive, a.onNoConfirm, danger = true, icon = ItalockIcons.Fingerprint,
                )
            }

            if (experimental.any) {
                SectionTitle("Experimental")
                Panel {
                    var first = true
                    fun sep(): Boolean { val show = !first; first = false; return show }
                    if (experimental.nearby) {
                        if (sep()) ThinDivider()
                        SwitchRow("Destravar automático quando perto", "Por Bluetooth ou na mesma rede, sem tocar no celular. Nunca pelo Tailscale.",
                            pc.autoUnlock && pc.hasAutoKey, a.onNearby, icon = ItalockIcons.Bluetooth)
                    }
                    if (experimental.notification) {
                        if (sep()) ThinDivider()
                        SwitchRow("Destravar pela notificação", "Botão Destravar na notificação, sem biometria.",
                            pc.notificationApprove && pc.hasAutoKey, a.onNotification, icon = ItalockIcons.Notify)
                    }
                    if (experimental.watch) {
                        if (sep()) ThinDivider()
                        SwitchRow("Relógio Amazfit (chamada falsa)", "O pedido toca como chamada no relógio: ignorar destrava, recusar nega (bloqueado fora do pulso).",
                            pc.watchApprove && pc.hasAutoKey, a.onWatch, icon = ItalockIcons.LockClock)
                    }
                    if (experimental.wear) {
                        if (sep()) ThinDivider()
                        SwitchRow("Relógio Wear OS", "O pedido aparece no relógio com Destravar e Recusar.",
                            pc.wearApprove && pc.hasAutoKey, a.onWear, icon = ItalockIcons.LockClock)
                    }
                }
            }

            SectionTitle("Histórico")
            Panel {
                val events = history.orEmpty()
                if (history != null && events.isEmpty()) {
                    Text("Nada por enquanto. Travar, destravar e pedidos do computador aparecem aqui.",
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
                            QuietButton(if (allHistory) "Mostrar menos" else "Mostrar todos (${events.size})", { allHistory = !allHistory })
                        }
                        Spacer(Modifier.weight(1f))
                        QuietButton("Limpar histórico", a.onClearHistory)
                    }
                }
            }

            Spacer(Modifier.padding(top = 16.dp))
            Panel {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = if (technical) "Recolher" else "Mostrar") { technical = !technical }
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp)
                        .semantics { stateDescription = if (technical) "aberto" else "fechado" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Detalhes técnicos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(if (technical) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (technical) {
                    ThinDivider()
                    Column(Modifier.padding(16.dp)) {
                        TechLine("Nome no computador", pc.pcName)
                        TechLine("Usuário", pc.userName)
                        TechLine("Sistema", pc.pcOs)
                        TechLine("Forma de chamar o celular", pc.pairingMethod)
                        TechLine("Endereços do computador", pc.pcIps.ifEmpty { listOf(pc.pcIp) }.joinToString("\n"), mono = true)
                        TechLine("Endereços deste celular", phoneIps.joinToString("\n").ifEmpty { "Nenhum" }, mono = true)
                        TechLine("Resposta do cadeado", if (pc.sealedSeen) "Cifrada" else "Em claro (programa antigo no computador)")
                        TechLine("Pareado em", java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("pt-BR")).format(java.util.Date(pc.pairedAt)))
                        TechLine("Id público (tag e relógio)", pc.publicId, mono = true)
                        TechLine("Identificador", pc.deviceId, mono = true, last = true)
                    }
                }
            }

            QuietButton("Remover computador", { confirmRemove = true }, danger = true, modifier = Modifier.padding(start = 8.dp, top = 16.dp))
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remover ${pc.displayName}?") },
        text = { Text("O celular deixa de destravar este computador. Para voltar, será preciso parear de novo.") },
        confirmButton = { QuietButton("Remover", { confirmRemove = false; a.onRemove() }, danger = true) },
        dismissButton = { QuietButton("Cancelar", { confirmRemove = false }) },
    )
    if (renaming) {
        var name by remember { mutableStateOf(pc.nickname) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nome do computador") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    placeholder = { Text(pc.pcName) }, supportingText = { Text("Vazio usa o nome do computador.") },
                )
            },
            confirmButton = { QuietButton("Salvar", { renaming = false; a.onRename(name) }) },
            dismissButton = { QuietButton("Cancelar", { renaming = false }) },
        )
    }
    if (editingMac) {
        var text by remember { mutableStateOf(pc.macAddresses.joinToString(", ")) }
        val parts = text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val valid = parts.all { WakeOnLan.parseMac(it) != null }
        AlertDialog(
            onDismissRequest = { editingMac = false },
            title = { Text("Endereço da placa com cabo") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(120) }, singleLine = true, isError = !valid,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    supportingText = { Text(if (valid) "Vários endereços: separe por vírgula. Vazio desliga Ligar pela rede." else "Formato: seis pares separados por dois pontos.") },
                )
            },
            confirmButton = { QuietButton("Salvar", { editingMac = false; a.onMacs(parts) }, enabled = valid) },
            dismissButton = { QuietButton("Cancelar", { editingMac = false }) },
        )
    }
}

private const val HISTORY_PREVIEW = 5

/** O que a tag faz, pelos dois interruptores. */
private fun tagSubtitle(pc: PairedPc): String = when {
    pc.tagLock && pc.tagUnlock -> "Encostar o celular desbloqueado na tag trava ou destrava este computador, conforme o cadeado."
    pc.tagUnlock -> "Encostar o celular desbloqueado na tag só destrava este computador."
    pc.tagLock -> "Encostar o celular desbloqueado na tag só trava este computador."
    else -> "Com os dois desligados, a tag deste computador não faz nada."
}

/** Uma linha do histórico: o que houve e, embaixo, quando, de onde e por qual caminho. */
@Composable
private fun HistoryRow(e: HistoryEvent) {
    val detail = listOf(whenText(e.at), e.origin.label, e.via).filter { it.isNotBlank() }.joinToString(" · ")
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
        title = { Text(if (nfcOff) "NFC desligado" else "Gravar tag de $name") },
        text = {
            Column {
                if (nfcOff) {
                    Text("Ligue o NFC nos ajustes do celular e volte aqui.")
                } else {
                    Text(if (done) message.orEmpty() else "Encoste a tag atrás do celular e segure até o aviso.")
                    if (!done) {
                        message?.let { Text(it, color = Italock.colors.dangerText, modifier = Modifier.padding(top = 8.dp)) }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
                                .toggleable(makeReadOnly, role = Role.Checkbox) { onMakeReadOnly(it) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = makeReadOnly, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("Proteger a tag contra escrita")
                                Text("Não tem volta: a tag nunca mais poderá ser regravada.",
                                    style = MaterialTheme.typography.bodySmall, color = Italock.colors.dangerText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (nfcOff) QuietButton("Abrir ajustes", onOpenSettings) else QuietButton(if (done) "Fechar" else "Cancelar", onClose)
        },
        dismissButton = { if (nfcOff) QuietButton("Cancelar", onClose) },
    )
}

@Composable
private fun TechLine(label: String, value: String, mono: Boolean = false, last: Boolean = false) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value.ifBlank { "Não informado" }, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) Mono else null,
        modifier = Modifier.padding(bottom = if (last) 0.dp else 12.dp))
}
