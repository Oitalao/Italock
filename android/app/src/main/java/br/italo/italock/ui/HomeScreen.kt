package br.italo.italock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import br.italo.italock.R
import br.italo.italock.data.EventKind
import br.italo.italock.i18n.L10n
import br.italo.italock.data.PairedPc
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.ui.components.DangerStrip
import br.italo.italock.ui.components.FixRow
import br.italo.italock.ui.components.Panel
import br.italo.italock.ui.components.PrimaryButton
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.components.SecondaryButton
import br.italo.italock.ui.components.StatusIcon
import br.italo.italock.ui.components.ThinDivider
import br.italo.italock.ui.components.statusLook
import br.italo.italock.ui.theme.Italock

/** O que o botão da linha faz, conforme o estado. */
enum class RowAction(@androidx.annotation.StringRes val labelRes: Int) {
    UNLOCK(R.string.action_unlock), LOCK(R.string.action_lock), WAKE(R.string.action_wake), FIX_CLOCK(R.string.action_fix_clock),
    REPAIR(R.string.action_pair);

    val label: String get() = L10n.s(labelRes)
}

fun rowAction(pc: PairedPc, status: LockStatus?, needsRepair: Boolean): RowAction? = when {
    needsRepair || status == LockStatus.NOT_PAIRED -> RowAction.REPAIR
    status == LockStatus.LOCKED -> RowAction.UNLOCK
    status == LockStatus.UNLOCKED -> RowAction.LOCK
    status == LockStatus.CLOCK_OFF -> RowAction.FIX_CLOCK
    // Sem resposta: ligar pela rede se der; senão, travar (travar nunca faz mal e funciona com agente antigo).
    status == LockStatus.NO_ANSWER -> if (pc.macAddresses.isNotEmpty()) RowAction.WAKE else RowAction.LOCK
    status == LockStatus.ERROR -> RowAction.LOCK
    else -> null // consultando
}

class HomeState(
    val pcs: List<PairedPc>,
    val statuses: Map<String, LockClient.StatusResult>,
    val busy: Set<String>,
    val needsRepair: (PairedPc) -> Boolean,
    val notificationsMissing: Boolean,
    val settingsAttention: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    snackbar: SnackbarHostState,
    onOpen: (PairedPc) -> Unit,
    onAction: (PairedPc, RowAction) -> Unit,
    onPair: () -> Unit,
    onSettings: () -> Unit,
    onFixNotifications: () -> Unit,
    /** "Travar todos" (só aparece com mais de um computador). */
    onLockAll: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                actions = {
                    // Discreto, só texto: a ação principal de cada computador continua sendo o botão da linha.
                    if (onLockAll != null) QuietButton(stringResource(R.string.home_lock_all), onLockAll)
                    if (state.pcs.isNotEmpty()) IconButton(onClick = onPair) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_pair_computer))
                    }
                    IconButton(onClick = onSettings) {
                        Box {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(
                                if (state.settingsAttention) R.string.home_settings_attention else R.string.settings_title))
                            // Ponto laranja: há permissão faltando nas Configurações.
                            if (state.settingsAttention) Box(
                                Modifier.align(Alignment.TopEnd).size(8.dp).background(Italock.colors.action, CircleShape),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (state.pcs.isEmpty()) {
            EmptyState(Modifier.padding(pad), state.notificationsMissing, onFixNotifications, onPair)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            if (state.notificationsMissing) item(key = "notif") {
                Panel(Modifier.padding(bottom = 12.dp)) {
                    FixRow(stringResource(R.string.home_notifications_off), stringResource(R.string.common_allow), onFixNotifications)
                }
            }
            // Um cartão por computador: cada um é uma coisa separada, com o próprio botão.
            state.pcs.forEach { pc ->
                item(key = pc.deviceId) {
                    Panel(Modifier.padding(bottom = 12.dp)) {
                        PcRow(
                            pc = pc,
                            result = state.statuses[pc.deviceId],
                            busy = pc.deviceId in state.busy,
                            needsRepair = state.needsRepair(pc),
                            onOpen = { onOpen(pc) },
                            onAction = { onAction(pc, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, notificationsMissing: Boolean, onFixNotifications: () -> Unit, onPair: () -> Unit) {
    Column(modifier.fillMaxSize()) {
        if (notificationsMissing) Panel(Modifier.padding(top = 8.dp)) {
            FixRow(stringResource(R.string.home_notifications_off), stringResource(R.string.common_allow), onFixNotifications)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.home_empty_text),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.padding(top = 24.dp))
            PrimaryButton(stringResource(R.string.home_pair_computer), onPair)
        }
    }
}

@Composable
private fun PcRow(pc: PairedPc, result: LockClient.StatusResult?, busy: Boolean, needsRepair: Boolean, onOpen: () -> Unit, onAction: (RowAction) -> Unit) {
    val status = result?.status
    val look = statusLook(status)
    val action = rowAction(pc, status, needsRepair)
    val last = lastEventText(pc)
    val waitingFor = stringResource(R.string.home_waiting_for, pc.displayName)
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.home_open_details), onClick = onOpen)
            .heightIn(min = 80.dp)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cadeado dentro de um círculo tingido com a cor do estado.
        Box(Modifier.size(44.dp).background(look.tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            StatusIcon(status)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pc.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (needsRepair) {
                Text(stringResource(R.string.home_needs_repair), style = MaterialTheme.typography.bodyMedium, color = Italock.colors.dangerText)
            } else {
                Text(unverified(look.label, result), style = MaterialTheme.typography.bodyMedium, color = look.textColor, maxLines = 2)
            }
            if (last != null) Text(last, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (pc.noConfirmActive) DangerStrip(modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> Box(Modifier.size(48.dp).semantics { contentDescription = waitingFor }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                action != null -> SecondaryButton(stringResource(action.labelRes), { onAction(action) })
            }
        }
    }
}

/** "Destravado hoje às 20:10 · Tailscale" (ontem / data quando não for hoje). */
fun lastEventText(pc: PairedPc): String? {
    if (pc.lastEvent.isEmpty() || pc.lastEventAt == 0L) return null
    val via = if (pc.lastEventVia.isNotEmpty()) " · ${L10n.via(pc.lastEventVia)}" else ""
    return "${EventKind.displayStored(pc.lastEvent)} ${whenText(pc.lastEventAt)}$via"
}

/** "hoje às 20:10", "ontem às 20:10" ou "28/09 às 20:10" (no idioma escolhido). */
fun whenText(at: Long): String {
    val then = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val today = java.util.Calendar.getInstance()
    val hour = L10n.hourMinute(at)
    fun same(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    val sameDay = same(then, today)
    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    return when {
        sameDay -> L10n.s(R.string.when_today, hour)
        same(then, today) -> L10n.s(R.string.when_yesterday, hour)
        else -> L10n.s(R.string.when_date, hour, L10n.dayMonth(at))
    }
}

/** Resumo do "Travar todos": "Todos os 3 travados", "2 de 3 travados. Não travou: Notebook". */
fun lockAllSummary(total: Int, notLocked: List<String>): String {
    val ok = total - notLocked.size
    val names = notLocked.joinToString(L10n.s(R.string.list_separator))
    return when {
        notLocked.isEmpty() -> if (total == 1) L10n.s(R.string.lock_all_one) else L10n.q(R.plurals.lock_all_done, total, total)
        ok <= 0 -> L10n.s(R.string.lock_all_none, names)
        else -> L10n.q(R.plurals.lock_all_partial, total, ok, total) + " " + L10n.q(R.plurals.lock_all_failed, notLocked.size, names)
    }
}

/** Estado que veio em claro (agente antigo): aparece, mas com o aviso. */
fun unverified(label: String, r: LockClient.StatusResult?): String =
    if (r != null && !r.verified && (r.status == LockStatus.LOCKED || r.status == LockStatus.UNLOCKED)) L10n.s(R.string.status_unverified, label) else label

/** Só o aviso, para quem junta com o estado já escrito (" · não verificado"). */
fun unverifiedSuffix(r: LockClient.StatusResult?): String = unverified("", r)
