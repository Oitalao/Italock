package br.italo.italock.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import br.italo.italock.R
import br.italo.italock.crypto.KeyGuard
import kotlin.concurrent.thread

/**
 * EXPERIMENTAL "Relógio Amazfit (Zepp)" (Configurações → Recursos experimentais, e ligado no PC).
 * Relógios sem botões nas notificações (ex.: Amazfit com Zepp) só repassam o CONTROLE DE MÚSICA. Durante um pedido
 * aberto, o Ítalock vira o "player":
 *   Próxima faixa DUAS VEZES em até 2 s = Destravar   •   Faixa anterior = Recusar   •   Play/Pause = ignorado
 * Só vale o comando que vem do app do relógio (Zepp/Zepp Life/Amazfit, pelo pacote de quem mandou, Android 9+):
 * "próxima faixa" de fone Bluetooth, carro ou qualquer outro app é ignorada.
 *
 * Cuidados (achados em testes):
 *  - O Zepp manda PAUSE sozinho ~2 s depois que o player aparece: Play/Pause NUNCA aprova, e todo evento dos primeiros
 *    [ARM_DELAY_MS] é ignorado.
 *  - MediaSession.setCallback recebe um Handler (a thread do serviço não tem Looper).
 *  - Aprovar manda resposta pela rede: sai da thread principal.
 *  - Android 15+: áudio de app em segundo plano sem serviço "mediaPlayback" fica mudo e o sistema pode não tratar este
 *    player como ativo — o relógio pode não mostrar o controle. Está dito na linha de risco da tela experimental.
 * Nada disto existe fora de um pedido: sem pedido, nenhuma sessão de mídia, nenhum áudio, nenhuma bateria.
 */
class WatchControl(private val context: Context, private val session: UnlockSession, private val onDone: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var media: MediaSession? = null
    private var silence: AudioTrack? = null
    private var startedAt = 0L
    private val notifId = (session.id + "watch").hashCode()

    /** "Esconder o nome": o player aparece na tela de bloqueio e no relógio. */
    private val shownName: String
        get() = if (br.italo.italock.data.AppPrefs(context).hideNameOnLockscreen) "o computador" else session.pc.displayName

    /** Chamar na thread principal. */
    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        val m = MediaSession(context, "italock-relogio")
        m.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Destravar $shownName?")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Próxima faixa duas vezes destrava, anterior recusa")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Ítalock")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (session.expiresAt - session.receivedAt).coerceAtLeast(1000))
                .build(),
        )
        m.setPlaybackState(
            PlaybackState.Builder()
                // Play/Pause aparecem (o relógio só mostra um player completo), mas são ignorados.
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .build(),
        )
        m.setCallback(object : MediaSession.Callback() {
            override fun onSkipToNext() { if (armed() && fromWatchApp(m)) onNext() }
            override fun onSkipToPrevious() { if (armed() && fromWatchApp(m)) answer(approve = false) }
            override fun onPlay() = Unit
            override fun onPause() = Unit
            override fun onStop() = Unit
        }, main)
        media = m
        m.isActive = true
        startSilence()
        showPlayer(m.sessionToken)
    }

    private var lastNextAt = 0L

    /** Gesto duplo: a primeira "próxima faixa" só arma; a segunda em até [DOUBLE_WINDOW_MS] destrava. */
    private fun onNext() {
        val now = SystemClock.elapsedRealtime()
        if (lastNextAt != 0L && now - lastNextAt <= DOUBLE_WINDOW_MS) { lastNextAt = 0L; answer(approve = true) }
        else lastNextAt = now
    }

    /**
     * O comando veio do app do relógio? Confere o pacote de quem mandou (Android 9+). Antes do Android 9 não dá para
     * saber a origem, então nada é aceito.
     */
    private fun fromWatchApp(m: MediaSession): Boolean {
        if (Build.VERSION.SDK_INT < 28) { Log.i("Italock", "relógio: origem desconhecida (Android 8 ou anterior), ignorado"); return false }
        val pkg = runCatching { m.currentControllerInfo.packageName }.getOrNull()
        val ok = isWatchPackage(pkg)
        if (!ok) Log.i("Italock", "relógio: comando de $pkg ignorado")
        return ok
    }

    private fun armed(): Boolean {
        val ok = SystemClock.elapsedRealtime() - startedAt >= ARM_DELAY_MS
        if (!ok) Log.i("Italock", "relógio: evento ignorado nos primeiros ${ARM_DELAY_MS / 1000} s")
        return ok
    }

    private fun answer(approve: Boolean) {
        thread(name = "watch-answer", isDaemon = true) {
            if (!session.finished) {
                // A opção pode ter sido desligada com o pedido aberto: vale a situação de agora, não a do começo.
                val current = br.italo.italock.data.PairingStore(context).all().find { it.deviceId == session.pc.deviceId }
                val allowed = br.italo.italock.data.AppPrefs(context).expWatch && current?.watchApprove == true
                // Desligada: o relógio só não aprova; o pedido segue aberto para confirmar no celular.
                if (approve && !allowed) Log.i("Italock", "relógio: aprovação desligada para ${session.pc.displayName}")
                else if (approve) {
                    val pc = current!!
                    val key = runCatching { KeyGuard.openAuto(pc.autoKeyEnc, pc.autoKeyIv) }.getOrNull()
                    if (key != null) session.approve(key, br.italo.italock.data.Origin.WATCH) else session.deny("APP_ERROR")
                } else session.deny(origin = br.italo.italock.data.Origin.WATCH)
            }
            main.post { stop(); onDone() }
        }
    }

    /** Notificação no estilo "player": o Zepp só repassa ao relógio o controle de notificações de mídia. */
    private fun showPlayer(token: MediaSession.Token) {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, UnlockService.CHANNEL_PROMPT)
        else @Suppress("DEPRECATION") Notification.Builder(context)
        val n = builder.setSmallIcon(R.drawable.ic_lock)
            .setContentTitle("Destravar $shownName?")
            .setContentText("No relógio: próxima faixa duas vezes destrava, anterior recusa")
            .setColor(0xFF003399.toInt())
            .setOngoing(true)
            .setStyle(Notification.MediaStyle().setMediaSession(token))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(notifId, n) }
    }

    private fun startSilence() = runCatching {
        val rate = 8000
        val buf = ShortArray(rate) // 1 s de silêncio em loop, só enquanto o pedido estiver aberto
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buf.size * 2)
            .build()
        track.write(buf, 0, buf.size)
        track.setLoopPoints(0, buf.size, -1)
        track.play()
        silence = track
    }

    /** Pode ser chamado de qualquer thread e mais de uma vez. */
    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { stop() }; return }
        runCatching { silence?.stop(); silence?.release() }
        silence = null
        runCatching { media?.isActive = false; media?.release() }
        media = null
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(notifId) }
    }

    companion object {
        /** Eventos antes disso são ignorados (o Zepp dispara sozinho ao mostrar o player). */
        const val ARM_DELAY_MS = 3_000L
        /** Intervalo máximo entre as duas "próxima faixa". */
        const val DOUBLE_WINDOW_MS = 2_000L

        /** Apps que repassam o controle de música do relógio Amazfit/Zepp. */
        val WATCH_PACKAGES = setOf(
            "com.huami.watch.hmwatchmanager", // Zepp
            "com.xiaomi.hm.health",            // Zepp Life (antigo Mi Fit)
            "com.huami.midong",                // Amazfit (antigo)
        )

        fun isWatchPackage(pkg: String?) = pkg != null && pkg in WATCH_PACKAGES
    }
}
