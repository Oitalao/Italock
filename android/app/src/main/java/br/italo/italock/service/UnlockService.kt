package br.italo.italock.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.italo.italock.R
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.DiagLog
import br.italo.italock.data.EventKind
import br.italo.italock.data.Events
import br.italo.italock.data.Origin
import br.italo.italock.data.PairingStore
import br.italo.italock.i18n.L10n
import br.italo.italock.net.NetInfo
import br.italo.italock.ui.UnlockActivity
import br.italo.italock.watch.WearBridge
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Serviço em primeiro plano que fica escutando os pedidos de desbloqueio:
 *  - UDP (broadcast da rede local / ancoragem USB e unicast do Tailscale) → conecta de volta no PC por TCP;
 *  - TCP em [PairingStore.TCP_PORT] (PC cliente);
 *  - Bluetooth RFCOMM com o UUID do protocolo (PC cliente).
 */
class UnlockService : Service() {
    private lateinit var store: PairingStore
    private lateinit var prefs: AppPrefs
    @Volatile private var running = true
    private val sockets = mutableListOf<java.io.Closeable>()
    private var multicastLock: WifiManager.MulticastLock? = null
    /** Evita abrir várias conexões para o mesmo pedido: o PC repete o broadcast a cada 2 s em cada interface. */
    private val inFlight = ConcurrentHashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        store = PairingStore(this)
        prefs = AppPrefs(this)
        val app = applicationContext
        // Agente que responde cifrado uma vez: dali em diante resposta em claro dele é recusada. Antes só a tela
        // principal gravava isso; consultas do atalho rápido, widget, tag e relógio (sem o app aberto) não contavam.
        br.italo.italock.lock.LockClient.onSealedSeen = { pc -> PairingStore(app).update(pc.deviceId) { it.copy(sealedSeen = true) } }
        UnlockSession.onApproved = { s, origin ->
            Events.record(app, s.pc.deviceId, EventKind.UNLOCKED, origin, NetInfo.viaLabel(s.via))
            // O computador leva uns segundos para entrar: o widget confere o cadeado depois disso (só se houver widget dele).
            br.italo.italock.widget.WidgetStatus.refreshLater(app, s.pc.deviceId, 4_000)
            // Aviso silencioso "Destravar X?" que tinha ficado para trás não serve mais.
            UnlockFlow.cancelAsk(app, s.pc)
        }
        UnlockSession.onDenied = { s, origin, expired ->
            Events.record(app, s.pc.deviceId, if (expired) EventKind.EXPIRED else EventKind.DENIED, origin, NetInfo.viaLabel(s.via))
        }
        prefs.registerListener(prefListener)
        createChannels(this)
        ServiceCompat.startForeground(
            this, NOTIF_ID, statusNotification(this),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        // Economia de bateria: a trava de multicast deixava o Wi-Fi acordado para todo broadcast da rede, o tempo todo.
        // Os PCs também mandam o pedido direto (unicast) para os IPs do celular, inclusive o do Tailscale.
        // multicastLock = getSystemService(WifiManager::class.java)?.createMulticastLock("italock")?.apply {
        //     setReferenceCounted(false); acquire()
        // }
        listenUdp(PairingStore.UDP_PORT)
        listenUdp(PairingStore.UDP_MANUAL_PORT)
        listenTcp()
        listenBluetooth()
        watchTailscale()
        alive = true
    }

    private var vpnCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val tailscaleCheck = Runnable { checkTailscale() }
    private val mainHandler by lazy { android.os.Handler(mainLooper) }
    @Volatile private var tailscaleWarned = false

    /** Ligar ou desligar o aviso nas Configurações vale na hora (a flag de "já avisei" é zerada). */
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppPrefs.K_TAILSCALE) {
            if (tailscaleWarned) getSystemService(NotificationManager::class.java)?.cancel(TAILSCALE_NOTIF_ID)
            tailscaleWarned = false
            scheduleTailscaleCheck(0)
        }
    }

    /**
     * Sem Tailscale o 4G não alcança os PCs: avisa (com toque para religar) quando a VPN do Tailscale cair.
     * Sem ficar consultando: o Android avisa quando uma rede VPN aparece ou some.
     */
    private fun watchTailscale() {
        if (!tailscaleInstalled(this)) return
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        val request = android.net.NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            // Espera uns segundos antes de avisar: a VPN costuma cair e voltar sozinha ao trocar de rede.
            override fun onAvailable(network: android.net.Network) = scheduleTailscaleCheck(2_000)
            override fun onLost(network: android.net.Network) = scheduleTailscaleCheck(15_000)
        }
        runCatching { cm.registerNetworkCallback(request, cb) }.onSuccess { vpnCallback = cb }
        scheduleTailscaleCheck(15_000)
    }

    private fun scheduleTailscaleCheck(delayMs: Long) {
        mainHandler.removeCallbacks(tailscaleCheck)
        mainHandler.postDelayed(tailscaleCheck, delayMs)
    }

    private fun checkTailscale() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!prefs.tailscaleWarning || !tailscaleInstalled(this)) {
            // Aviso desligado nas Configurações (ou Tailscale desinstalado).
            if (tailscaleWarned) { nm.cancel(TAILSCALE_NOTIF_ID); tailscaleWarned = false }
            return
        }
        val up = tailscaleUp(this)
        if (!up && !tailscaleWarned) {
            val launch = packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE) ?: return
            val pi = PendingIntent.getActivity(this, 77, launch, PendingIntent.FLAG_IMMUTABLE)
            nm.notify(TAILSCALE_NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_PROMPT)
                .setSmallIcon(R.drawable.ic_lock).setColor(BRAND_BLUE)
                .setContentTitle(L10n.s(R.string.notif_tailscale_off_title))
                .setContentText(L10n.s(R.string.notif_tailscale_off_text))
                .setContentIntent(pi).setAutoCancel(true).build())
            tailscaleWarned = true
            DiagLog.i("Tailscale desligado: aviso mostrado")
        } else if (up && tailscaleWarned) {
            nm.cancel(TAILSCALE_NOTIF_ID)
            tailscaleWarned = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A tela avisa quando o usuário concede permissões: a thread do Bluetooth, que dormia sem prazo, acorda.
        if (intent?.action == ACTION_PERMISSIONS_CHANGED) synchronized(btSignal) { btSignal.notifyAll() }
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        running = false
        synchronized(btSignal) { btSignal.notifyAll() } // a thread do Bluetooth pode estar dormindo sem prazo
        synchronized(sockets) { sockets.forEach { runCatching { it.close() } } }
        multicastLock?.release()
        prefs.unregisterListener(prefListener)
        mainHandler.removeCallbacks(tailscaleCheck)
        vpnCallback?.let { cb -> runCatching { getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        btStateReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    private fun listenUdp(port: Int) = thread(name = "udp-$port", isDaemon = true) {
        while (running) {
            try {
                DatagramSocket(null).use { sock ->
                    synchronized(sockets) { sockets += sock }
                    sock.reuseAddress = true
                    sock.broadcast = true
                    sock.bind(InetSocketAddress(port))
                    val buf = ByteArray(2048)
                    while (running) {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        val o = runCatching { JSONObject(String(pkt.data, 0, pkt.length)) }.getOrNull() ?: continue
                        val deviceId = o.optString("deviceId")
                        val pc = store.byDeviceId(deviceId) ?: continue
                        val ip = o.optString("pcbuIP").ifEmpty { pkt.address.hostAddress }
                        val pcPort = o.optInt("pcbuPort", pc.unlockServerPort)
                        // O IP só é guardado depois que o PC provar a chave (em connectBack): UDP forjado não suja a lista.
                        val key = deviceId.lowercase()
                        val now = System.currentTimeMillis()
                        if ((inFlight[key] ?: 0) > now) continue
                        // Caminho desligado para este PC: nem conecta (o PC tenta pelos outros).
                        if (!pc.allows(NetInfo.viaOf(ip))) continue
                        inFlight[key] = now + 15_000
                        thread(name = "udp-connect", isDaemon = true) { connectBack(ip, pcPort, deviceId, key) }
                    }
                }
            } catch (e: Exception) {
                if (running) { DiagLog.w("UDP $port: ${e.javaClass.simpleName}"); Thread.sleep(3000) }
            }
        }
    }

    /** Modo servidor do PC: conectar em pcbuIP:pcbuPort, mandar DEVICE_ID e esperar o UNLOCK_REQUEST. */
    private fun connectBack(ip: String, port: Int, deviceId: String, key: String) {
        val s = Socket()
        var authenticated = false
        try {
            s.connect(InetSocketAddress(ip, port), 5000)
            s.tcpNoDelay = true
            // O caminho real sai da interface usada: IP privado alcançado pela VPN (rota de sub rede) não é "perto".
            val via = NetInfo.viaOf(ip, s.localAddress?.hostAddress)
            br.italo.italock.net.Frame.write(s.getOutputStream(), br.italo.italock.net.Frame.DEVICE_ID, deviceId.toByteArray())
            handleSession(s.getInputStream(), s.getOutputStream(), s, via, socket = s) {
                authenticated = true
                store.rememberPcIp(deviceId, ip)
            }
        } catch (e: Exception) {
            DiagLog.w("conexão de volta a $ip:$port falhou: ${e.javaClass.simpleName}")
            runCatching { s.close() }
        } finally {
            // Falhou antes de o PC provar a chave (endereço errado/forjado): libera logo para o próximo broadcast.
            if (authenticated) inFlight[key] = System.currentTimeMillis() + 3000 else inFlight.remove(key)
        }
    }

    private fun listenTcp() = thread(name = "tcp", isDaemon = true) {
        while (running) {
            try {
                ServerSocket().use { server ->
                    synchronized(sockets) { sockets += server }
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(PairingStore.TCP_PORT))
                    while (running) {
                        val s = server.accept()
                        s.tcpNoDelay = true
                        val via = NetInfo.viaOf(s.inetAddress.hostAddress ?: "", s.localAddress?.hostAddress)
                        thread(isDaemon = true) {
                            try { handleSession(s.getInputStream(), s.getOutputStream(), s, via, socket = s) }
                            catch (e: Exception) { DiagLog.w("TCP sessão: ${e.javaClass.simpleName}"); runCatching { s.close() } }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) { DiagLog.w("TCP: ${e.javaClass.simpleName}"); Thread.sleep(3000) }
            }
        }
    }

    private val btSignal = Object()
    private var btStateReceiver: android.content.BroadcastReceiver? = null

    /** Bluetooth pode ser usado agora? (adaptador ligado e, no Android 12+, permissão BLUETOOTH_CONNECT) */
    @SuppressLint("MissingPermission")
    private fun bluetoothReady(): Boolean {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        return allowed && runCatching { adapter.isEnabled }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun listenBluetooth() = thread(name = "bt", isDaemon = true) {
        // Bluetooth desligado: dorme até o Android avisar que ligou (em vez de conferir a cada 10 s).
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = synchronized(btSignal) { btSignal.notifyAll() }
        }
        btStateReceiver = receiver
        registerReceiver(receiver, android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED))
        while (running) {
            if (!bluetoothReady()) {
                // Sem prazo: acorda quando o Bluetooth liga (broadcast) ou quando a permissão é concedida (onStartCommand).
                // Confere de novo DENTRO do bloco: um aviso que chegue entre a conferência e o wait não se perde.
                synchronized(btSignal) { if (running && !bluetoothReady()) btSignal.wait() }
                continue
            }
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: continue
            var server: BluetoothServerSocket? = null
            try {
                server = adapter.listenUsingRfcommWithServiceRecord("Italock", BT_UUID)
                synchronized(sockets) { sockets += server }
                while (running) {
                    val s = server.accept()
                    thread(isDaemon = true) {
                        try { handleSession(s.inputStream, s.outputStream, s, NetInfo.VIA_BLUETOOTH) }
                        catch (e: Exception) { DiagLog.w("BT sessão: ${e.javaClass.simpleName}"); runCatching { s.close() } }
                    }
                }
            } catch (e: Exception) {
                if (running) { DiagLog.w("BT: ${e.javaClass.simpleName}"); Thread.sleep(5000) }
            } finally {
                runCatching { server?.close() }
            }
        }
    }

    /**
     * Uma conexão do PC. Conexão muda ou lenta não prende a thread: prazo TOTAL de 15 s para chegar o pedido (relógio que
     * fecha a conexão, em TCP e Bluetooth; o soTimeout do TCP sozinho seria renovado a cada byte pingado), e no máximo
     * [Frame.MAX_JUNK] bytes estranhos antes do início de um frame.
     * [onAuthenticated] roda só depois que o pedido foi decifrado com a chave do pareamento.
     */
    private fun handleSession(
        input: java.io.InputStream, output: java.io.OutputStream, closeable: java.io.Closeable, via: String,
        socket: Socket? = null, onAuthenticated: () -> Unit = {},
    ) {
        socket?.soTimeout = REQUEST_TIMEOUT_MS
        val watchdog = Runnable { runCatching { closeable.close() } }.also { mainHandler.postDelayed(it, REQUEST_TIMEOUT_MS.toLong()) }
        val session = try {
            UnlockSession.fromRequest(store, input, output, closeable, via)
        } catch (e: Exception) {
            DiagLog.w("pedido inválido via $via: ${e.javaClass.simpleName}")
            null
        } finally {
            mainHandler.removeCallbacks(watchdog)
        }
        if (session == null) { runCatching { closeable.close() }; return }
        if (!session.pc.allows(via)) {
            // Caminho desligado nos detalhes deste PC: fecha sem responder (o PC tenta pelos outros caminhos).
            DiagLog.i("pedido de ${session.pc.displayName} via $via ignorado: caminho desligado")
            session.close()
            return
        }
        DiagLog.i("pedido de ${session.pc.displayName} via $via")
        val timeoutMs = prefs.promptTimeoutMs
        session.expiresAt = session.receivedAt + timeoutMs
        // Depois do pedido, a conexão fica aberta enquanto a confirmação estiver na tela (com limite).
        runCatching { socket?.soTimeout = (timeoutMs + 30_000).toInt() }
        runCatching { onAuthenticated() }
        UnlockSession.active[session.id] = session
        var bipWatch = false
        try {
            PreApproval.take(session)?.let { granted ->
                if (session.approve(granted.passwordKey, granted.origin)) {
                    UnlockedNotice.show(this, session.pc, NetInfo.viaLabel(session.via))
                }
                Thread.sleep(1500)
                return
            }
            val nearby = session.via == NetInfo.VIA_BLUETOOTH || session.via == NetInfo.VIA_LAN
            // EXPERIMENTAL "destravar automático quando perto": só com a chave global ligada, o PC marcado e o pedido
            // vindo por Bluetooth ou pela rede local; nunca pelo Tailscale nem por VPN.
            if (nearby && session.pc.autoUnlock && session.pc.hasAutoKey && prefs.expNearby) {
                val key = runCatching { br.italo.italock.crypto.KeyGuard.openAuto(session.pc.autoKeyEnc, session.pc.autoKeyIv) }.getOrNull()
                if (key != null) {
                    if (session.approve(key, Origin.AUTO)) {
                        UnlockedNotice.show(this, session.pc, NetInfo.viaLabel(session.via),
                            L10n.s(R.string.notice_unlocked_auto, session.pc.displayName))
                    }
                } else session.deny("APP_ERROR")
                Thread.sleep(1500)
                return
            }
            // Pedido que o PC fez sozinho (tela de entrada do boot, ninguém na frente dele): sem alerta nem tela, que
            // às 5h ninguém quer. Fica um aviso silencioso "Destravar X?" que vale o dia todo; tocar nele, de qualquer
            // lugar, pede pelo agente (wake) e o PC entra sem ninguém tocar no teclado. "WAITING" faz o PC parar de
            // tentar sozinho.
            if (session.automatic) {
                DiagLog.i("pedido automático de ${session.pc.displayName} via ${session.via}: aviso silencioso")
                UnlockFlow.askOnPhone(this, session.pc, origin = Origin.NOTIFICATION, quiet = true)
                session.deny("WAITING")
                Thread.sleep(1500)
                return
            }
            // EXPERIMENTAL "relógio Amazfit": anuncia ao fork do Gadgetbridge, que mostra a chamada falsa no relógio;
            // ignorar no relógio aprova esta sessão (a resposta chega pelo BipBridgeProvider, não por aqui).
            bipWatch = session.pc.watchApprove && session.pc.hasAutoKey && prefs.expWatch
            if (bipWatch) br.italo.italock.watch.BipBridge.announce(this, session, prefs.hideNameOnLockscreen)
            // EXPERIMENTAL "relógio Wear OS": o pedido vai também para o relógio; vale a primeira resposta.
            if (session.pc.wearApprove && session.pc.hasAutoKey && prefs.expWear) {
                WearBridge.sendPrompt(this, session, prefs.hideNameOnLockscreen)
            }
            showPrompt(session)
            // Fica lendo até o PC encerrar (CLOSE/EOF) para derrubar a tela se ele cancelar ou outro caminho vencer.
            thread(isDaemon = true) {
                try { while (true) br.italo.italock.net.Frame.read(input) } catch (_: Exception) { }
                if (!session.finished) session.close()
                UnlockSession.active.remove(session.id)
                cancelPrompt(session)
            }
            if (!session.awaitFinish(timeoutMs)) {
                // Ninguém respondeu a tempo: o pedido some, mas fica o aviso silencioso para destravar depois sem
                // precisar apertar tecla no PC de novo.
                if (session.deny("CANCEL", expired = true))
                    UnlockFlow.askOnPhone(this, session.pc, origin = Origin.NOTIFICATION, quiet = true)
            }
            Thread.sleep(1500)
        } finally {
            if (bipWatch) br.italo.italock.watch.BipBridge.endPrompt(this, session.id)
            session.close()
            UnlockSession.active.remove(session.id)
            cancelPrompt(session)
        }
    }

    private fun showPrompt(session: UnlockSession) {
        val intent = Intent(this, UnlockActivity::class.java)
            .putExtra(UnlockActivity.EXTRA_SESSION, session.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(this, session.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val fullScreen = prefs.fullScreenPrompt
        val hide = prefs.hideNameOnLockscreen
        // Opção ligada: o texto sai sem nome sempre; o nome aparece só na tela do pedido, depois do desbloqueio.
        val hideNow = hide
        val n = NotificationCompat.Builder(this, CHANNEL_PROMPT)
            .setSmallIcon(R.drawable.ic_lock).setColor(BRAND_BLUE)
            .setContentTitle(if (hideNow) L10n.s(R.string.prompt_generic_title) else L10n.s(R.string.prompt_title, session.pc.displayName))
            .setContentText(if (hideNow) L10n.s(R.string.prompt_generic_text) else
                listOfNotNull(session.user.takeIf { it.isNotBlank() }, L10n.s(R.string.prompt_via, L10n.via(NetInfo.viaLabel(session.via)))).joinToString(" · "))
            .setCategory(if (fullScreen) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                if (hide) {
                    // Na tela de bloqueio aparece só um aviso genérico, sem o nome do computador.
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(this@UnlockService, CHANNEL_PROMPT)
                            .setSmallIcon(R.drawable.ic_lock).setColor(BRAND_BLUE)
                            .setContentTitle(L10n.s(R.string.prompt_generic_title)).setContentText(L10n.s(R.string.prompt_generic_text))
                            .build(),
                    )
                } else setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Android 14+: tela cheia só com a permissão especial; sem ela fica a notificação (o app mostra um aviso).
                if (fullScreen && NotificationManagerCompat.from(this@UnlockService).canUseFullScreenIntent()) setFullScreenIntent(pi, true)
            }
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter((session.expiresAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1000))
            .addAction(actionFor(session, ActionReceiver.ACTION_DENY, L10n.s(R.string.prompt_deny)))
            // Sem aprovação direta, o botão abre a confirmação com biometria (atividade direta: o Android bloqueia
            // abrir tela a partir de um receiver em segundo plano).
            .addAction(
                if (session.pc.notificationApprove && session.pc.hasAutoKey && prefs.expNotification) approveAction(session)
                else NotificationCompat.Action.Builder(R.drawable.ic_lock, L10n.s(R.string.action_unlock), pi).build(),
            )
            .build()
        getSystemService(NotificationManager::class.java).notify(session.id.hashCode(), n)
        // Tela cheia desligada: só a notificação (nada abre sozinho por cima da tela).
        if (fullScreen) runCatching { startActivity(intent) }
    }

    /**
     * "Destravar" direto na notificação (sem biometria), mas nunca com o celular travado:
     * Android 12+ exige o desbloqueio antes de disparar a ação; no 11 ou anterior a ação abre uma tela que pede o desbloqueio.
     */
    private fun approveAction(session: UnlockSession): NotificationCompat.Action =
        if (Build.VERSION.SDK_INT >= 31) {
            NotificationCompat.Action.Builder(R.drawable.ic_lock, L10n.s(R.string.action_unlock), broadcastFor(session, ActionReceiver.ACTION_APPROVE))
                .setAuthenticationRequired(true).build()
        } else {
            val intent = Intent(this, br.italo.italock.ui.ApproveActivity::class.java)
                .putExtra(UnlockActivity.EXTRA_SESSION, session.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(this, (session.id + "approve").hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(R.drawable.ic_lock, L10n.s(R.string.action_unlock), pi).build()
        }

    private fun broadcastFor(session: UnlockSession, action: String): PendingIntent {
        val intent = Intent(this, ActionReceiver::class.java).setAction(action).putExtra(UnlockActivity.EXTRA_SESSION, session.id)
        return PendingIntent.getBroadcast(this, (session.id + action).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionFor(session: UnlockSession, action: String, label: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(R.drawable.ic_lock, label, broadcastFor(session, action)).build()

    private fun cancelPrompt(session: UnlockSession) {
        getSystemService(NotificationManager::class.java).cancel(session.id.hashCode())
        if (session.pc.wearApprove) WearBridge.endPrompt(this, session.id)
        sendBroadcast(Intent(ACTION_SESSION_ENDED).setPackage(packageName).putExtra(UnlockActivity.EXTRA_SESSION, session.id))
    }

    companion object {
        /** Aviso fixo do serviço (no idioma escolhido). */
        private fun statusNotification(ctx: Context): android.app.Notification = NotificationCompat.Builder(ctx, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_lock).setColor(BRAND_BLUE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle(L10n.s(R.string.notif_service_title))
            .setContentText(L10n.s(R.string.notif_service_text))
            .setOngoing(true)
            .build()

        /** O serviço está de pé (para repor o aviso fixo ao mudar o idioma). */
        @Volatile private var alive = false

        /** Idioma mudou: nomes dos canais e o aviso fixo do serviço passam para o idioma novo. */
        @SuppressLint("MissingPermission")
        fun languageChanged(ctx: Context) {
            createChannels(ctx)
            if (alive && NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, statusNotification(ctx)) }
            }
        }

        const val NOTIF_ID = 1
        const val TAILSCALE_NOTIF_ID = 2
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_PROMPT = "prompt"
        /** Aviso silencioso "Destravar X?" (pedido automático ou pedido que expirou). */
        const val CHANNEL_WAITING = "esperando"
        const val ACTION_SESSION_ENDED = "br.italo.italock.SESSION_ENDED"
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
        /** Tempo máximo para o PC mandar o pedido depois de conectar. */
        const val REQUEST_TIMEOUT_MS = 15_000
        const val ACTION_PERMISSIONS_CHANGED = "br.italo.italock.PERMISSIONS_CHANGED"
        const val BRAND_BLUE = 0xFF003399.toInt()
        val BT_UUID: UUID = UUID.fromString("62182bf7-97c8-45f9-aa2c-53c5f2008bdf")

        fun tailscaleInstalled(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0) }.isSuccess

        /**
         * Tailscale conectado de verdade: uma rede VPN cuja interface é tun e tem endereço 100.64.0.0/10
         * (outra VPN qualquer não conta).
         */
        fun tailscaleUp(ctx: Context): Boolean {
            val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            return runCatching {
                cm.allNetworks.any { n ->
                    val caps = cm.getNetworkCapabilities(n)
                    if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) != true) return@any false
                    val lp = cm.getLinkProperties(n) ?: return@any false
                    val tun = lp.interfaceName?.let { NetInfo.isVpnInterface(it) } ?: true
                    tun && lp.linkAddresses.any { NetInfo.isTailscale(it.address.hostAddress ?: "") }
                }
            }.getOrDefault(false)
        }

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return // antes do Android 8 não existem canais
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_STATUS, L10n.s(R.string.channel_status), NotificationManager.IMPORTANCE_MIN))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PROMPT, L10n.s(R.string.channel_prompt), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_WAITING, L10n.s(R.string.channel_waiting), NotificationManager.IMPORTANCE_LOW))
        }

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, UnlockService::class.java)) }
                .onFailure { DiagLog.w("não deu para iniciar o serviço: ${it.javaClass.simpleName}") }
        }

        /** Avisa o serviço (se estiver rodando) que permissões mudaram. Só chamar com o app na tela. */
        fun permissionsChanged(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, UnlockService::class.java).setAction(ACTION_PERMISSIONS_CHANGED)) }
        }
    }
}
