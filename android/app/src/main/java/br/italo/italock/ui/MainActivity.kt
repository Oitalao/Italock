package br.italo.italock.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import br.italo.italock.BuildConfig
import br.italo.italock.crypto.KeyGuard
import br.italo.italock.R
import br.italo.italock.i18n.L10n
import androidx.compose.ui.res.stringResource
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.DiagLog
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import br.italo.italock.data.EventKind
import br.italo.italock.data.Events
import br.italo.italock.data.HistoryEvent
import br.italo.italock.data.HistoryStore
import br.italo.italock.data.Origin
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.lock.TileRefresher
import br.italo.italock.lock.WakeOnLan
import br.italo.italock.nfc.NfcWriter
import br.italo.italock.pair.PairResult
import br.italo.italock.pair.PairingClient
import br.italo.italock.pair.QrData
import br.italo.italock.pair.describePairingError
import br.italo.italock.service.PreApproval
import br.italo.italock.service.UnlockFlow
import br.italo.italock.service.UnlockService
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.theme.ItalockTheme
import br.italo.italock.ui.theme.ThemeState
import br.italo.italock.watch.WearBridge
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Telas do app. A navegação é uma pilha simples: voltar desempilha. */
sealed interface Route {
    data object Home : Route
    data class Details(val deviceId: String) : Route
    data object Pair : Route
    data object Settings : Route
    data object Experimental : Route
    data object About : Route

    fun encode(): String = when (this) {
        Home -> "home"; Pair -> "pair"; Settings -> "settings"; Experimental -> "experimental"; About -> "about"
        is Details -> "details:$deviceId"
    }

    companion object {
        fun decode(s: String): Route? = when {
            s == "home" -> Home; s == "pair" -> Pair; s == "settings" -> Settings
            s == "experimental" -> Experimental; s == "about" -> About
            s.startsWith("details:") -> Details(s.removePrefix("details:"))
            else -> null
        }
    }
}

/** Recursos experimentais que usam a cópia da senha sem biometria. */
enum class ExpFeature { NEARBY, NOTIFICATION, WATCH, WEAR }

/**
 * Tela principal. Guarda o estado e faz as ações; o desenho de cada tela fica em *Screen.kt.
 * O cadeado de cada computador é consultado SÓ com o app aberto (intervalo nas Configurações; nada em segundo plano).
 * Gravações e leituras da lista rodam fora da thread da tela.
 */
class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(br.italo.italock.i18n.L10n.wrap(newBase))

    private lateinit var store: PairingStore
    private lateinit var prefs: AppPrefs
    private val snackbar = SnackbarHostState()

    private var pcs by mutableStateOf(listOf<PairedPc>())
    /** Estado de cada computador. Ausente = ainda consultando. */
    private var statuses by mutableStateOf(mapOf<String, LockClient.StatusResult>())
    /** Computadores com pedido em andamento: o botão vira "aguardando" para não repetir a ação. */
    private var busy by mutableStateOf(setOf<String>())
    private var missing by mutableStateOf(listOf<Missing>())
    private var pairProgress by mutableStateOf(PairProgress())
    private var dangerFor by mutableStateOf<String?>(null)
    private var confirmLockFor by mutableStateOf<String?>(null)
    private var confirmLockAll by mutableStateOf(false)
    /** Histórico do computador aberto em Detalhes (deviceId e eventos, mais novo primeiro). */
    private var history by mutableStateOf<Pair<String, List<HistoryEvent>>?>(null)
    private var flags by mutableStateOf(ExperimentalFlags(false, false, false, false))
    private var settings by mutableStateOf(SettingsValues())
    private var tileDeviceId by mutableStateOf("")
    private var keyResetAt by mutableStateOf(0L)
    private var resumeTick by mutableIntStateOf(0)
    private var openTileChooser by mutableStateOf(false)
    /** A lista já foi lida do disco pelo menos uma vez. */
    private var loaded by mutableStateOf(false)
    private val stack = mutableStateListOf<Route>(Route.Home)

    /** Resultado de um pareamento que o computador já gravou mas o celular ainda não (para "Confirmar de novo"). */
    private var pendingPair: PairResult? = null
    private var pairAttempt = 0

    // Gravação da tag NFC
    private val nfcAdapter: NfcAdapter? by lazy { runCatching { NfcAdapter.getDefaultAdapter(this) }.getOrNull() }
    private var nfcFor by mutableStateOf<String?>(null)
    private var nfcReadOnly by mutableStateOf(false)
    private var nfcMessage by mutableStateOf<String?>(null)
    private var nfcDone by mutableStateOf(false)
    private var readerOn = false

    private var hadBluetoothPermission = false

    /** Intervalo do cadeado (0 = só ao abrir); a consulta relê quando a opção muda. */
    private val pollSec = kotlinx.coroutines.flow.MutableStateFlow(15)

    private val scanner = registerForActivityResult(ScanContract()) { r -> r.contents?.let { startPairing(it) } }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Bluetooth liberado: a thread do serviço, que dormia sem prazo, acorda.
        UnlockService.permissionsChanged(this)
        reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DiagLog.init(this)
        store = PairingStore(this)
        prefs = AppPrefs(this)
        ThemeState.load(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            br.italo.italock.ui.theme.windowBackgroundFor(ThemeState.isDark(this))))
        // Agente que responde cifrado uma vez: dali em diante resposta em claro dele é recusada.
        LockClient.onSealedSeen = { pc -> PairingStore(applicationContext).update(pc.deviceId) { it.copy(sealedSeen = true) } }
        hadBluetoothPermission = bluetoothGranted()
        savedInstanceState?.getStringArrayList(KEY_STACK)?.mapNotNull { Route.decode(it) }?.takeIf { it.isNotEmpty() }?.let {
            stack.clear(); stack += it
        }
        savedInstanceState?.getString(KEY_PAIR_MSG)?.let { pairProgress = PairProgress(message = it, error = true) }
        settings = SettingsValues.from(prefs, L10n.tag(this))
        pollSec.value = prefs.pollSec
        reload {
            if (savedInstanceState == null) {
                askStartupPermissions()
                handleIntent(intent)
            }
        }
        UnlockService.start(this)
        setContent { ItalockTheme { App() } }
        // repeatOnLifecycle: sair e voltar nunca cria duas consultas paralelas; em segundo plano, nada roda.
        // "Só ao abrir": uma consulta a cada volta à tela.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    withContext(Dispatchers.IO) { refreshStatuses() }
                    // Pedidos recusados ou expirados chegam pelo serviço: o histórico aberto acompanha (leitura local).
                    loadHistory()
                    // "Só ao abrir": espera a opção mudar (sem consultar nada); mudou para um intervalo, volta a consultar.
                    val every = pollSec.first { it != 0 }
                    // Espera o intervalo, mas acorda se a opção mudar no meio (ex.: de 30 s para 5 s).
                    withTimeoutOrNull(every * 1000L) { pollSec.first { it != every } }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Android 13+: idioma do app trocado nos ajustes do sistema enquanto o app estava aberto.
        if (L10n.syncWithSystem(this)) { recreate(); return }
        reload()
        // Com o app na tela o sistema não limita as chamadas: bom momento para conferir os atalhos do ícone.
        lifecycleScope.launch(Dispatchers.IO) { AppShortcuts.sync(this@MainActivity, store.all()) }
        resumeTick++
        // Bluetooth liberado fora do app (ajustes): acorda a thread do serviço.
        val bt = bluetoothGranted()
        if (bt && !hadBluetoothPermission) UnlockService.permissionsChanged(this)
        hadBluetoothPermission = bt
        if (nfcFor != null && !nfcDone) enableReader()
    }

    override fun onPause() {
        disableReader()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_STACK, ArrayList(stack.map { it.encode() }))
        if (pairProgress.error) outState.putString(KEY_PAIR_MSG, pairProgress.message)
    }

    private fun bluetoothGranted() = Build.VERSION.SDK_INT < 31 || Permissions.granted(this, Manifest.permission.BLUETOOTH_CONNECT)

    /** Relê tudo o que pode ter mudado fora da tela (serviço, widget, ajustes do Android). Leitura fora da tela. */
    private fun reload(then: () -> Unit = {}) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { store.all() }
            val tile = withContext(Dispatchers.IO) { store.tileDeviceId }
            val reset = withContext(Dispatchers.IO) { store.keyResetAt }
            loadHistory()
            pcs = list
            tileDeviceId = tile
            keyResetAt = reset
            missing = Permissions.missing(this@MainActivity)
            flags = ExperimentalFlags(prefs.expNearby, prefs.expNotification, prefs.expWatch, prefs.expWear)
            settings = SettingsValues.from(prefs, L10n.tag(this@MainActivity))
            pollSec.value = prefs.pollSec
            loaded = true
            then()
        }
    }

    /** Relê o histórico do computador aberto em Detalhes (fora da tela). */
    private suspend fun loadHistory() {
        val id = (stack.lastOrNull() as? Route.Details)?.deviceId ?: run { history = null; return }
        history = id to withContext(Dispatchers.IO) { HistoryStore(this@MainActivity).list(id) }
    }

    private fun clearHistory(pc: PairedPc) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                HistoryStore(this@MainActivity).clear(pc.deviceId)
                store.update(pc.deviceId) { it.copy(lastEvent = "", lastEventVia = "", lastEventAt = 0L) }
            }
            reload()
            loadHistory()
            feedback(L10n.s(R.string.main_history_cleared, pc.displayName), ok = true)
        }
    }

    /** Altera a lista fora da tela e depois atualiza a tela (e o atalho rápido). */
    private fun change(work: () -> Unit, after: () -> Unit = {}) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { work(); store.all() }
            pcs = list
            tileDeviceId = withContext(Dispatchers.IO) { store.tileDeviceId }
            TileRefresher.refresh(this@MainActivity)
            after()
        }
    }

    /**
     * "--es tela parear|config|experimental|sobre|detalhes [--es pc <id>]": abre direto numa tela (atalhos e testes).
     * Toque longo no atalho da barra rápida: Configurações, já na escolha do computador do atalho.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            stack.clear(); stack += Route.Home; stack += Route.Settings
            openTileChooser = true
            return
        }
        val route = when (intent?.getStringExtra("tela")) {
            "parear" -> Route.Pair
            "config" -> Route.Settings
            "experimental" -> Route.Experimental
            "sobre" -> Route.About
            "detalhes" -> {
                val id = intent.getStringExtra("pc")
                val pc = pcs.firstOrNull { it.deviceId.equals(id, true) } ?: pcs.firstOrNull()
                pc?.let { Route.Details(it.deviceId) }
            }
            else -> null
        } ?: return
        stack.clear(); stack += Route.Home
        if (route == Route.Experimental || route == Route.About) stack += Route.Settings
        stack += route
        lifecycleScope.launch { loadHistory() }
    }

    private fun go(route: Route) {
        stack += route
        if (route is Route.Details) lifecycleScope.launch { loadHistory() }
    }
    private fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
        openTileChooser = false
    }

    /**
     * Pede notificações e Bluetooth UMA vez por instalação. Negado de vez, alguns Android (ex.: Motorola 16) abrem a
     * tela de ajustes a cada pedido; daí em diante quem cuida é o aviso da tela inicial e das Configurações.
     */
    private fun askStartupPermissions() {
        val flags = getSharedPreferences("italock", MODE_PRIVATE)
        if (flags.getBoolean("askedStartupPermissions", false)) return
        flags.edit().putBoolean("askedStartupPermissions", true).apply()
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filterNot { Permissions.granted(this, it) }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    // ---------------------------------------------------------------- telas

    @Composable
    private fun App() {
        @Suppress("UNUSED_EXPRESSION") resumeTick
        val route = stack.last()
        BackHandler(enabled = stack.size > 1) { back() }
        when (route) {
            Route.Home -> HomeScreen(
                state = HomeState(pcs, statuses, busy, { it.pairedAt < keyResetAt },
                    notificationsMissing = Missing.NOTIFICATIONS in missing, settingsAttention = missing.isNotEmpty()),
                snackbar = snackbar,
                onOpen = { go(Route.Details(it.deviceId)) },
                onAction = ::act,
                onPair = { pairProgress = PairProgress(); go(Route.Pair) },
                onSettings = { go(Route.Settings) },
                onFixNotifications = { fix(Missing.NOTIFICATIONS) },
                onLockAll = if (pcs.size > 1) ({ lockAll() }) else null,
            )
            is Route.Details -> {
                val index = pcs.indexOfFirst { it.deviceId.equals(route.deviceId, true) }
                if (index < 0) {
                    // Computador removido (por exemplo, por outra tela): volta. Antes da primeira leitura, só espera.
                    if (loaded) LaunchedEffect(route) { stack.remove(route) }
                    return
                }
                val pc = pcs[index]
                DetailsScreen(
                    pc = pc, index = index, count = pcs.size,
                    status = statuses[pc.deviceId], busy = pc.deviceId in busy, needsRepair = pc.pairedAt < keyResetAt,
                    isTile = PairingStore.tilePcOf(pcs, tileDeviceId)?.deviceId == pc.deviceId,
                    history = history?.takeIf { it.first.equals(pc.deviceId, true) }?.second,
                    hasNfc = nfcAdapter != null,
                    experimental = flags, snackbar = snackbar,
                    a = DetailsActions(
                        onBack = ::back,
                        onMain = { act(pc, it) },
                        onRename = { name -> change({ store.rename(pc.deviceId, name) }) },
                        onMove = { d -> change({ store.move(pc.deviceId, d) }) },
                        onTile = { on -> setTile(pc, on) },
                        onWake = { wake(pc) },
                        onMacs = { macs -> change({ store.update(pc.deviceId) { it.copy(macAddresses = macs) } }) },
                        onPath = { kind, on -> setPath(pc, kind, on) },
                        onWriteTag = { startTagWrite(pc) },
                        onTagLock = { on -> change({ store.update(pc.deviceId) { it.copy(tagLock = on) } }) },
                        onTagUnlock = { on -> change({ store.update(pc.deviceId) { it.copy(tagUnlock = on) } }) },
                        onClearHistory = { clearHistory(pc) },
                        onNoConfirm = { on -> if (on) dangerFor = pc.deviceId else disableNoConfirm(pc) },
                        onNearby = { setPcFeature(pc, ExpFeature.NEARBY, it) },
                        onNotification = { setPcFeature(pc, ExpFeature.NOTIFICATION, it) },
                        onWatch = { setPcFeature(pc, ExpFeature.WATCH, it) },
                        onWear = { setPcFeature(pc, ExpFeature.WEAR, it) },
                        onRemove = { remove(pc) },
                    ),
                )
            }
            Route.Pair -> PairScreen(
                progress = pairProgress,
                bluetoothMissing = Missing.BLUETOOTH in missing,
                onBack = ::back,
                onScan = ::scanQr,
                onCode = ::startPairing,
                onAllowBluetooth = { fix(Missing.BLUETOOTH) },
                onRetrySave = { pendingPair?.let { saveWhenResumed(it) } },
            )
            Route.Settings -> SettingsScreen(
                missing = missing,
                values = settings,
                tailscaleInstalled = UnlockService.tailscaleInstalled(this),
                pcs = pcs,
                tileDeviceId = tileDeviceId,
                openTileChooser = openTileChooser,
                experimentalOn = listOf(flags.nearby, flags.notification, flags.watch, flags.wear).count { it },
                a = SettingsActions(
                    onBack = ::back,
                    onFix = ::fix,
                    onChange = ::applySetting,
                    onTilePc = { id -> change({ store.tileDeviceId = id }) },
                    onAbout = { go(Route.About) },
                    onExperimental = { go(Route.Experimental) },
                ),
            )
            Route.Experimental -> ExperimentalScreen(
                flags = flags,
                wearAvailable = WearBridge.available(this) || flags.wear,
                onBack = ::back,
                onFeature = ::setGlobal,
            )
            Route.About -> AboutScreen(
                tech = techInfo(),
                a = AboutActions(
                    onBack = ::back,
                    onLink = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    onExport = ::exportDiagnostics,
                ),
            )
        }
        dangerFor?.let { id ->
            val pc = pcs.firstOrNull { it.deviceId == id }
            if (pc == null) dangerFor = null
            else DangerDialog(pc.displayName, onConfirm = { dangerFor = null; enableNoConfirm(pc) }, onDismiss = { dangerFor = null })
        }
        confirmLockFor?.let { id ->
            val pc = pcs.firstOrNull { it.deviceId == id }
            if (pc == null) confirmLockFor = null
            else AlertDialog(
                onDismissRequest = { confirmLockFor = null },
                title = { Text(stringResource(R.string.quick_confirm_lock_title, pc.displayName)) },
                confirmButton = { QuietButton(stringResource(R.string.action_lock), { confirmLockFor = null; lockNow(pc) }) },
                dismissButton = { QuietButton(stringResource(R.string.common_cancel), { confirmLockFor = null }) },
            )
        }
        if (confirmLockAll) AlertDialog(
            onDismissRequest = { confirmLockAll = false },
            title = { Text(stringResource(R.string.main_lock_all_ask)) },
            confirmButton = { QuietButton(stringResource(R.string.home_lock_all), { confirmLockAll = false; lockAllNow() }) },
            dismissButton = { QuietButton(stringResource(R.string.common_cancel), { confirmLockAll = false }) },
        )
        nfcFor?.let { id ->
            val pc = pcs.firstOrNull { it.deviceId == id }
            if (pc == null) closeTagWrite()
            else NfcWriteDialog(
                name = pc.displayName,
                nfcOff = nfcAdapter?.isEnabled == false,
                makeReadOnly = nfcReadOnly,
                message = nfcMessage,
                done = nfcDone,
                onMakeReadOnly = { nfcReadOnly = it },
                onOpenSettings = { runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } } },
                onClose = ::closeTagWrite,
            )
        }
    }

    // ---------------------------------------------------------------- estado dos computadores

    /** Consulta o cadeado de cada computador (em paralelo). Pula quem está com ação em andamento: ela mesma confere. */
    private fun refreshStatuses() {
        val busyNow = busy
        val targets = pcs.filter { it.deviceId !in busyNow }
        if (targets.isEmpty()) return
        val latch = CountDownLatch(targets.size)
        for (pc in targets) thread(isDaemon = true) {
            val s = runCatching { LockClient.status(pc) }.getOrDefault(LockClient.StatusResult(LockStatus.NO_ANSWER, false))
            runOnUiThread {
                statuses = statuses + (pc.deviceId to s)
                // Já destravado: o aviso silencioso "Destravar X?" não serve mais.
                if (s.status == LockStatus.UNLOCKED) UnlockFlow.cancelAsk(this, pc)
            }
            latch.countDown()
        }
        latch.await(15, TimeUnit.SECONDS)
    }

    /** Consulta o cadeado a cada 1,5 s até chegar ao estado esperado (ou dar o tempo). */
    private fun waitForState(pc: PairedPc, locked: Boolean, timeoutMs: Long): Boolean {
        val want = if (locked) LockStatus.LOCKED else LockStatus.UNLOCKED
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val now = runCatching { LockClient.status(pc) }.getOrDefault(LockClient.StatusResult(LockStatus.NO_ANSWER, false))
            runOnUiThread { statuses = statuses + (pc.deviceId to now) }
            if (now.status == want) return true
            Thread.sleep(1500)
        }
        return false
    }

    // ---------------------------------------------------------------- ações

    private fun act(pc: PairedPc, action: RowAction) = when (action) {
        RowAction.UNLOCK -> unlockNow(pc)
        RowAction.LOCK -> lock(pc)
        RowAction.WAKE -> wake(pc)
        RowAction.FIX_CLOCK -> { runCatching { startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }; Unit }
        RowAction.REPAIR -> { pairProgress = PairProgress(); go(Route.Pair) }
    }

    /**
     * Destravar pelo celular: confirma (ou não, no modo sem confirmação com estado verificado), pré aprova e chama o
     * computador. Se o computador JÁ estava pedindo, nada é pré aprovado: abre esse pedido, que pede a confirmação normal.
     */
    private fun unlockNow(pc: PairedPc) {
        if (pc.deviceId in busy) return
        PreApproval.openSession(pc.deviceId)?.let { showOpenRequest(it.id, pc); return }
        // O botão "Destravar" aparece pelo estado; se ele veio em claro (não verificado), sempre pede confirmação.
        val unverified = statuses[pc.deviceId]?.verified == false
        UnlockKey.obtain(this, pc, unverified, onOk = { key ->
            busy = busy + pc.deviceId
            thread {
                val r = UnlockFlow.wakeWithKey(pc, key, Origin.APP)
                if (!r.ok) {
                    runOnUiThread {
                        busy = busy - pc.deviceId
                        if (r.alreadyOpen) PreApproval.openSession(pc.deviceId)?.let { showOpenRequest(it.id, pc) }
                        else feedback(r.message, ok = false)
                    }
                    return@thread
                }
                // Confirma pelo cadeado: o computador ainda precisa chamar o celular e entrar.
                val unlocked = waitForState(pc, locked = false, timeoutMs = 30_000)
                PreApproval.revoke(pc.deviceId, r.token)
                runOnUiThread {
                    busy = busy - pc.deviceId
                    reload()
                    if (unlocked) feedback(L10n.s(R.string.main_unlocked, pc.displayName), ok = true)
                    else feedback(L10n.s(R.string.main_not_unlocked, pc.displayName), ok = false)
                }
            }
        }, onFail = { msg -> if (msg != null) feedback(msg, ok = false) })
    }

    private fun showOpenRequest(sessionId: String, pc: PairedPc) {
        feedback(L10n.s(R.string.flow_already_asking, pc.displayName), ok = false)
        runCatching {
            startActivity(Intent(this, UnlockActivity::class.java).putExtra(UnlockActivity.EXTRA_SESSION, sessionId))
        }
    }

    /** Travar: pergunta antes se "Pedir confirmação antes de travar" estiver ligado. */
    private fun lock(pc: PairedPc) {
        if (pc.deviceId in busy) return
        if (prefs.confirmLock) confirmLockFor = pc.deviceId else lockNow(pc)
    }

    private fun lockNow(pc: PairedPc) {
        if (pc.deviceId in busy) return
        busy = busy + pc.deviceId
        thread {
            val r = LockClient.lockOutcome(pc)
            if (r.ok) {
                Events.record(this, pc.deviceId, EventKind.LOCKED, Origin.APP, r.via)
                br.italo.italock.service.UnlockedNotice.cancel(this, pc.deviceId)
            }
            val confirmed = r.ok && waitForState(pc, locked = true, timeoutMs = 10_000)
            if (r.ok) br.italo.italock.widget.WidgetStatus.refreshLater(this, pc.deviceId, 0)
            runOnUiThread {
                busy = busy - pc.deviceId
                reload()
                when {
                    !r.ok -> feedback(r.message(pc), ok = false)
                    confirmed -> feedback(L10n.s(R.string.err_ok_locked, pc.displayName), ok = true)
                    else -> feedback(L10n.s(R.string.main_lock_unconfirmed, pc.displayName), ok = false)
                }
            }
        }
    }

    /** "Travar todos": pergunta antes se "Confirmar antes de travar" estiver ligado. */
    private fun lockAll() {
        if (prefs.confirmLock) confirmLockAll = true else lockAllNow()
    }

    /** Trava todos ao mesmo tempo (pula quem já tem pedido em andamento) e mostra um resumo. */
    private fun lockAllNow() {
        val targets = pcs.filter { it.deviceId !in busy }
        if (targets.isEmpty()) return
        val ids = targets.map { it.deviceId }.toSet()
        busy = busy + ids
        val app = applicationContext
        thread {
            val locked = java.util.Collections.synchronizedSet(HashSet<String>())
            val latch = CountDownLatch(targets.size)
            for (pc in targets) thread(isDaemon = true) {
                try {
                    val r = runCatching { LockClient.lockOutcome(pc) }.getOrNull()
                    if (r?.ok == true) {
                        Events.record(app, pc.deviceId, EventKind.LOCKED, Origin.APP, r.via)
                        br.italo.italock.service.UnlockedNotice.cancel(app, pc.deviceId)
                        br.italo.italock.widget.WidgetStatus.refreshLater(app, pc.deviceId, 1_500)
                        locked += pc.deviceId
                    }
                } finally {
                    latch.countDown()
                }
            }
            // Quem não respondeu a tempo conta como não travado.
            latch.await(40, TimeUnit.SECONDS)
            val notLocked = targets.filter { it.deviceId !in locked }.map { it.displayName }
            runOnUiThread {
                busy = busy - ids
                reload()
                feedback(lockAllSummary(targets.size, notLocked), ok = notLocked.isEmpty())
                lifecycleScope.launch(Dispatchers.IO) { refreshStatuses() }
            }
        }
    }

    private fun wake(pc: PairedPc) {
        thread { val r = WakeOnLan.wake(pc.macAddresses); runOnUiThread { feedback(r.message, ok = r.ok) } }
    }

    private fun remove(pc: PairedPc) {
        stack.removeAll { it is Route.Details && it.deviceId == pc.deviceId }
        change({
            store.remove(pc.deviceId)
            if (store.tileDeviceId.equals(pc.deviceId, true)) store.tileDeviceId = ""
        }) { feedback(L10n.s(R.string.main_removed, pc.displayName), ok = true) }
    }

    /** Desligar o atalho deste computador deixa o atalho sem computador (vale também com um computador só). */
    private fun setTile(pc: PairedPc, on: Boolean) {
        change({ store.tileDeviceId = if (on) pc.deviceId else PairingStore.TILE_NONE })
    }

    private fun setPath(pc: PairedPc, kind: PathKind, on: Boolean) {
        change({
            store.update(pc.deviceId) {
                when (kind) {
                    PathKind.LAN -> it.copy(allowLan = on)
                    PathKind.BLUETOOTH -> it.copy(allowBluetooth = on)
                    PathKind.TAILSCALE -> it.copy(allowTailscale = on)
                }
            }
        }) {
            val now = pcs.firstOrNull { it.deviceId == pc.deviceId } ?: return@change
            if (!now.allowLan && !now.allowBluetooth && !now.allowTailscale) {
                feedback(L10n.s(R.string.main_no_paths, now.displayName), ok = false)
            }
        }
    }

    // ---------------------------------------------------------------- tag NFC

    private fun startTagWrite(pc: PairedPc) {
        nfcFor = pc.deviceId
        nfcDone = false
        nfcMessage = null
        nfcReadOnly = false
        enableReader()
    }

    private fun closeTagWrite() {
        nfcFor = null
        disableReader()
    }

    /**
     * Leitura em primeiro plano (enableReaderMode, API 19+): enquanto o diálogo estiver aberto, toda tag encostada vem
     * para cá (e não abre outro app).
     */
    private fun enableReader() {
        val a = nfcAdapter ?: return
        if (readerOn || !a.isEnabled) return
        val flagsNfc = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        runCatching { a.enableReaderMode(this, { tag -> onTag(tag) }, flagsNfc, null) }.onSuccess { readerOn = true }
    }

    private fun disableReader() {
        if (!readerOn) return
        runCatching { nfcAdapter?.disableReaderMode(this) }
        readerOn = false
    }

    /** Thread do leitor: gravar bloqueia, então nada disto roda na thread da tela. */
    private fun onTag(tag: Tag) {
        val id = nfcFor ?: return
        if (nfcDone) return
        val pc = store.byDeviceId(id) ?: return
        val r = NfcWriter.write(tag, NfcWriter.message(pc.publicId, packageName), nfcReadOnly)
        DiagLog.i("tag NFC de ${pc.displayName}: $r")
        runOnUiThread {
            nfcMessage = r.message
            if (r.ok) { nfcDone = true; disableReader() }
            vibrate(r.ok)
        }
    }

    // ---------------------------------------------------------------- modo sem confirmação

    /** Liga: exige biometria para copiar a senha do computador para a chave sem biometria. */
    private fun enableNoConfirm(pc: PairedPc) {
        BioAuth.openPasswordKey(this, pc, L10n.s(R.string.main_no_confirm_bio_title), pc.displayName,
            onOk = { key ->
                val sealed = runCatching { KeyGuard.sealAuto(key) }.getOrElse { feedback(L10n.s(R.string.main_key_error), ok = false); return@openPasswordKey }
                change({ store.update(pc.deviceId) { it.copy(noConfirm = true, noConfirmKeyEnc = sealed.first, noConfirmKeyIv = sealed.second) } }) {
                    feedback(L10n.s(R.string.main_no_confirm_on, pc.displayName), ok = true)
                }
            },
            onFail = { msg -> if (msg != null) feedback(msg, ok = false) },
        )
    }

    /** Desliga e apaga a cópia da senha na hora. */
    private fun disableNoConfirm(pc: PairedPc) {
        change({ store.update(pc.deviceId) { it.copy(noConfirm = false, noConfirmKeyEnc = "", noConfirmKeyIv = "") } }) {
            feedback(L10n.s(R.string.main_no_confirm_off, pc.displayName), ok = true)
        }
    }

    // ---------------------------------------------------------------- recursos experimentais

    private fun PairedPc.with(feature: ExpFeature, on: Boolean) = when (feature) {
        ExpFeature.NEARBY -> copy(autoUnlock = on)
        ExpFeature.NOTIFICATION -> copy(notificationApprove = on)
        ExpFeature.WATCH -> copy(watchApprove = on)
        ExpFeature.WEAR -> copy(wearApprove = on)
    }

    /** Sem nenhuma opção experimental usando, a cópia da senha é apagada. */
    private fun PairedPc.dropUnusedAutoKey() = if (usesAutoKey) this else copy(autoKeyEnc = "", autoKeyIv = "")

    /**
     * Liga/desliga um recurso experimental num computador. Ligar pede a biometria uma vez, para guardar uma cópia da
     * senha numa chave sem biometria (é o que permite destravar sem confirmar).
     */
    private fun setPcFeature(pc: PairedPc, feature: ExpFeature, on: Boolean) {
        if (!on) { change({ store.update(pc.deviceId) { it.with(feature, false).dropUnusedAutoKey() } }); return }
        if (pc.hasAutoKey) { change({ store.update(pc.deviceId) { it.with(feature, true) } }); return }
        BioAuth.openPasswordKey(this, pc, L10n.s(R.string.main_confirm_for, pc.displayName),
            onOk = { key ->
                val (enc, iv) = runCatching { KeyGuard.sealAuto(key) }.getOrElse { feedback(L10n.s(R.string.main_key_error), ok = false); return@openPasswordKey }
                change({ store.update(pc.deviceId) { it.with(feature, true).copy(autoKeyEnc = enc, autoKeyIv = iv) } }) {
                    feedback(L10n.s(R.string.main_on_for, pc.displayName), ok = true)
                }
            },
            onFail = { msg -> if (msg != null) feedback(msg, ok = false) },
        )
    }

    /** Chave global. Desligar desliga o recurso em todos os computadores e apaga cópias de senha que sobrarem. */
    private fun setGlobal(feature: ExpFeature, on: Boolean) {
        when (feature) {
            ExpFeature.NEARBY -> prefs.expNearby = on
            ExpFeature.NOTIFICATION -> prefs.expNotification = on
            ExpFeature.WATCH -> prefs.expWatch = on
            ExpFeature.WEAR -> prefs.expWear = on
        }
        flags = ExperimentalFlags(prefs.expNearby, prefs.expNotification, prefs.expWatch, prefs.expWear)
        if (!on) change({ store.all().forEach { pc -> store.update(pc.deviceId) { it.with(feature, false).dropUnusedAutoKey() } } })
    }

    // ---------------------------------------------------------------- configurações

    private fun applySetting(c: SettingChange) {
        when (c) {
            is SettingChange.Theme -> ThemeState.set(this, c.v)
            is SettingChange.Vibrate -> prefs.vibrate = c.v
            is SettingChange.ConfirmLock -> prefs.confirmLock = c.v
            is SettingChange.PromptTimeout -> prefs.promptTimeoutSec = c.sec
            is SettingChange.ApproveWhileLocked -> prefs.approveWhileLocked = c.v
            is SettingChange.FullScreen -> { prefs.fullScreenPrompt = c.v; missing = Permissions.missing(this) }
            is SettingChange.HideName -> prefs.hideNameOnLockscreen = c.v
            is SettingChange.AllowCredential -> prefs.allowDeviceCredential = c.v
            is SettingChange.Poll -> { prefs.pollSec = c.sec; pollSec.value = prefs.pollSec }
            is SettingChange.TailscaleWarning -> {
                prefs.tailscaleWarning = c.v
                if (!c.v) getSystemService(NotificationManager::class.java)?.cancel(UnlockService.TAILSCALE_NOTIF_ID)
            }
            is SettingChange.Language -> {
                if (c.tag == L10n.tag(this)) return
                L10n.set(this, c.tag)
                // A tela se refaz no idioma novo (a pilha de telas é salva e volta igual).
                recreate()
                return
            }
        }
        settings = SettingsValues.from(prefs, L10n.tag(this))
    }

    // ---------------------------------------------------------------- sobre

    private fun techInfo(): TechInfo = TechInfo(listOf(
        L10n.s(R.string.main_tech_version) to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        L10n.s(R.string.main_tech_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
        L10n.s(R.string.main_tech_phone_id) to store.deviceUuid,
        L10n.s(R.string.main_tech_paired) to pcs.size.toString(),
        L10n.s(R.string.main_tech_google) to L10n.s(if (WearBridge.available(this)) R.string.main_tech_available else R.string.main_tech_unavailable),
    ))

    private fun exportDiagnostics() {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { Diagnostics.write(this@MainActivity) }.getOrNull() }
            if (file == null) { feedback(L10n.s(R.string.main_export_failed), ok = false); return@launch }
            val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.arquivos", file)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, L10n.s(R.string.main_export_subject)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { startActivity(Intent.createChooser(send, L10n.s(R.string.main_export_chooser))) }
                .onFailure { feedback(L10n.s(R.string.main_export_no_app), ok = false) }
        }
    }

    // ---------------------------------------------------------------- permissões

    private fun fix(m: Missing) {
        when (m) {
            Missing.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33 && !Permissions.granted(this, Manifest.permission.POST_NOTIFICATIONS) &&
                    shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                else Permissions.open(this, Permissions.notificationSettings(this))
            Missing.FULL_SCREEN -> Permissions.open(this, Permissions.fullScreenSettings(this))
            Missing.BATTERY -> Permissions.open(this, Permissions.batterySettings(this))
            Missing.BLUETOOTH ->
                if (Build.VERSION.SDK_INT >= 31 && shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT))
                    permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                else if (Build.VERSION.SDK_INT >= 31 && !askedBluetooth) {
                    askedBluetooth = true
                    permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                } else Permissions.open(this, Permissions.appDetails(this))
        }
    }

    /** Uma tentativa pelo pedido do sistema; se ele não aparecer (negado de vez), o próximo toque abre os ajustes. */
    private var askedBluetooth = false

    // ---------------------------------------------------------------- pareamento

    private fun scanQr() = scanner.launch(
        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(L10n.s(R.string.main_scan_prompt))
            .setBeepEnabled(false).setOrientationLocked(false),
    )

    private fun startPairing(text: String) {
        val qr = runCatching { QrData.parse(text) }.getOrElse {
            pairProgress = PairProgress(message = L10n.s(R.string.main_pair_invalid_code), error = true); return
        }
        val attempt = ++pairAttempt
        pendingPair = null
        pairProgress = PairProgress(running = true, message = L10n.s(R.string.main_pair_searching))
        // Prazo geral: o estado "procurando/conectando" nunca fica preso na tela.
        lifecycleScope.launch {
            delay(PAIR_TIMEOUT_MS)
            if (attempt == pairAttempt && pairProgress.running && pendingPair == null) {
                pairAttempt++
                pairProgress = PairProgress(message = L10n.s(R.string.main_pair_timeout), error = true)
            }
        }
        thread {
            try {
                val result = PairingClient(this).pair(qr) { msg ->
                    runOnUiThread { if (attempt == pairAttempt) pairProgress = PairProgress(running = true, message = msg) }
                }
                runOnUiThread {
                    if (attempt != pairAttempt) return@runOnUiThread
                    pendingPair = result
                    saveWhenResumed(result)
                }
            } catch (e: Exception) {
                DiagLog.w("pareamento falhou: ${e.javaClass.simpleName}")
                runOnUiThread { if (attempt == pairAttempt) pairProgress = PairProgress(message = describePairingError(e), error = true) }
            }
        }
    }

    /**
     * O computador já gravou o pareamento; o celular só grava depois da confirmação. A confirmação (BiometricPrompt) não
     * aparece com o app fora da tela, então espera o app voltar ao primeiro plano (com prazo). Se não der, explica o que
     * fazer e deixa "Confirmar de novo".
     */
    private fun saveWhenResumed(r: PairResult) {
        pairProgress = PairProgress(running = true, message = L10n.s(R.string.main_pair_confirm))
        lifecycleScope.launch {
            val shown = withTimeoutOrNull(SAVE_WAIT_MS) { lifecycle.withResumed { sealAndSave(r) }; true }
            if (shown == null) pairProgress = PairProgress(
                message = L10n.s(R.string.main_pair_not_saved),
                error = true, canRetrySave = true,
            )
        }
    }

    /** Guarda o passwordKey cifrado por chave do Keystore: exige confirmar com rosto/digital/senha já no pareamento. */
    private fun sealAndSave(r: PairResult) {
        BioAuth.seal(this, L10n.s(R.string.main_pair_bio_title, r.pcName), r.passwordKey,
            onOk = { (enc, iv) ->
                pendingPair = null
                lifecycleScope.launch {
                    val shownName = withContext(Dispatchers.IO) {
                        // Re-parear mantém apelido, lugar, caminhos, tag e o "já respondeu cifrado"; opções que dependem
                        // das chaves antigas (sem confirmação, experimentais) precisam ser ligadas de novo.
                        val previous = store.byDeviceId(r.deviceId)
                        store.save(PairedPc(
                            nickname = previous?.nickname ?: "",
                            deviceId = r.deviceId, encKey = r.encKey, passwordKeyEnc = enc, passwordKeyIv = iv,
                            pcName = r.pcName, pcOs = r.pcOs, userName = r.userName, pairingMethod = r.pairingMethod,
                            pcIp = r.pcIp, pcIps = r.pcIps, unlockServerPort = r.unlockServerPort,
                            macAddresses = r.macAddresses.ifEmpty { previous?.macAddresses.orEmpty() },
                            publicId = previous?.publicId ?: "",
                            allowLan = previous?.allowLan ?: true,
                            allowBluetooth = previous?.allowBluetooth ?: true,
                            allowTailscale = previous?.allowTailscale ?: true,
                            tagLock = previous?.tagLock ?: true,
                            tagUnlock = previous?.tagUnlock ?: true,
                            sealedSeen = previous?.sealedSeen ?: false,
                        ))
                        previous?.displayName ?: r.pcName
                    }
                    TileRefresher.refresh(this@MainActivity)
                    pairProgress = PairProgress()
                    reload()
                    stack.clear(); stack += Route.Home
                    feedback(L10n.s(R.string.main_paired_with, shownName), ok = true)
                }
            },
            onFail = { msg ->
                pairProgress = PairProgress(
                    message = L10n.s(R.string.main_pair_cancelled, msg ?: L10n.s(R.string.main_pair_cancelled_default)),
                    error = true, canRetrySave = true,
                )
            },
        )
    }

    // ---------------------------------------------------------------- retorno ao usuário

    /** Aviso curto na tela e vibração (leve se deu certo, dupla se falhou), se "Vibrar ao concluir" estiver ligado. */
    private fun feedback(message: String, ok: Boolean) {
        vibrate(ok)
        lifecycleScope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) }
    }

    private fun vibrate(ok: Boolean) = Haptics.done(this, ok)

    companion object {
        private const val KEY_STACK = "stack"
        private const val KEY_PAIR_MSG = "pairMsg"
        private const val PAIR_TIMEOUT_MS = 120_000L
        private const val SAVE_WAIT_MS = 60_000L
    }
}
