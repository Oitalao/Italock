package br.italo.italock.data

import android.content.Context
import org.json.JSONArray

/**
 * Preferências gerais e chaves dos RECURSOS EXPERIMENTAIS (desligadas por padrão).
 * Com a chave global desligada, o serviço nem chega a olhar a opção do PC: nada roda e nada gasta bateria.
 */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("italock", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- Geral

    /** Aparência: escuro é o padrão de fábrica. */
    var theme: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(K_THEME, null))
        set(v) { prefs.edit().putString(K_THEME, v.key).apply() }

    /** Vibração curta ao concluir (ou falhar) uma ação. */
    var vibrate: Boolean
        get() = prefs.getBoolean(K_VIBRATE, true)
        set(v) { prefs.edit().putBoolean(K_VIBRATE, v).apply() }

    /** Pergunta "Travar?" antes de travar (app, widget, atalho rápido e tag NFC). */
    var confirmLock: Boolean
        get() = prefs.getBoolean(K_CONFIRM_LOCK, false)
        set(v) { prefs.edit().putBoolean(K_CONFIRM_LOCK, v).apply() }

    /**
     * Quanto tempo um pedido do computador fica aberto esperando resposta. Começa em 10 s (chave nova desde a 1.6.1:
     * quem vinha dos 90 s antigos também passa a 10).
     */
    var promptTimeoutSec: Int
        get() = prefs.getInt(K_PROMPT_TIMEOUT, PROMPT_TIMEOUT_DEFAULT).let { if (it in PROMPT_TIMEOUTS) it else PROMPT_TIMEOUT_DEFAULT }
        set(v) { prefs.edit().putInt(K_PROMPT_TIMEOUT, v).apply() }

    val promptTimeoutMs: Long get() = promptTimeoutSec * 1000L

    /**
     * Aceitar o pedido do computador direto na tela de bloqueio do celular, só com a biometria. Desligado (padrão):
     * o celular tem de ser desbloqueado antes; a biometria do pedido vem depois.
     */
    var approveWhileLocked: Boolean
        get() = prefs.getBoolean(K_APPROVE_LOCKED, false)
        set(v) { prefs.edit().putBoolean(K_APPROVE_LOCKED, v).apply() }

    /**
     * Celular bloqueado agora e aprovar na tela de bloqueio não vale: é preciso desbloquear antes de destravar o PC.
     * Aprovar bloqueado só vale com as duas opções ligadas (sem o pedido na tela de bloqueio, não há onde aprovar).
     */
    fun needsPhoneUnlock(context: Context): Boolean = !(approveWhileLocked && fullScreenPrompt) &&
        context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Pedido do computador aparece sozinho por cima da tela de bloqueio e acende a tela. Desligado (padrão desde a
     * 1.6.2, chave nova): só a notificação, sem acender a tela; tocar nela pede o desbloqueio do celular antes.
     * Evita gasto de bateria e a tela acesa sozinha (de madrugada, com pedidos repetidos).
     */
    var fullScreenPrompt: Boolean
        get() = prefs.getBoolean(K_FULL_SCREEN, false)
        set(v) { prefs.edit().putBoolean(K_FULL_SCREEN, v).apply() }

    /** Com o celular bloqueado, o pedido não mostra o nome do computador (notificação e tela). */
    var hideNameOnLockscreen: Boolean
        get() = prefs.getBoolean(K_HIDE_NAME, false)
        set(v) { prefs.edit().putBoolean(K_HIDE_NAME, v).apply() }

    /**
     * "Esconder nome no bloqueio" ligado E o celular bloqueado agora. Notificação "privada" com versão pública só é
     * escondida se a pessoa tiver mandado o Android ocultar conteúdo sensível (o padrão de fábrica mostra tudo): por
     * isso, com o celular bloqueado, o próprio texto da notificação já sai sem o nome.
     */
    fun hideNameNow(context: Context): Boolean = hideNameOnLockscreen &&
        context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Aceita PIN, padrão ou senha do celular além da biometria. Desligado: só biometria forte. */
    var allowDeviceCredential: Boolean
        get() = prefs.getBoolean(K_ALLOW_CREDENTIAL, true)
        set(v) { prefs.edit().putBoolean(K_ALLOW_CREDENTIAL, v).apply() }

    // ---------------------------------------------------------------- Conexão

    /** Aviso "Tailscale desligado" (só existe se o Tailscale estiver instalado). */
    var tailscaleWarning: Boolean
        get() = prefs.getBoolean(K_TAILSCALE, true)
        set(v) { prefs.edit().putBoolean(K_TAILSCALE, v).apply() }

    /** De quantos em quantos segundos o cadeado é consultado com o app aberto; 0 = só ao abrir. */
    var pollSec: Int
        get() = prefs.getInt(K_POLL, 15).let { if (it in POLL_OPTIONS) it else 15 }
        set(v) { prefs.edit().putInt(K_POLL, v).apply() }

    // ---------------------------------------------------------------- Experimentais

    /** Destravar sozinho quando o pedido vem por Bluetooth ou pela rede local (nunca pelo Tailscale). */
    var expNearby: Boolean
        get() = prefs.getBoolean(K_NEARBY, false)
        set(v) { prefs.edit().putBoolean(K_NEARBY, v).commit() }

    /**
     * "Destravar" direto na notificação, sem biometria. Até a 1.1 era uma opção comum por PC: quem já tinha ligado em
     * algum PC continua com ela ligada depois de atualizar (a chave global nasce ligada só nesse caso).
     */
    var expNotification: Boolean
        get() {
            if (!prefs.contains(K_NOTIF)) {
                val used = runCatching {
                    val arr = JSONArray(prefs.getString("pcs", "[]"))
                    (0 until arr.length()).any { arr.getJSONObject(it).optBoolean("notificationApprove") }
                }.getOrDefault(false)
                prefs.edit().putBoolean(K_NOTIF, used).commit()
            }
            return prefs.getBoolean(K_NOTIF, false)
        }
        set(v) { prefs.edit().putBoolean(K_NOTIF, v).commit() }

    /** Aprovar pela chamada falsa no relógio Amazfit (via fork Gadgetbridge Voz): ignorar destrava, recusar nega. */
    var expWatch: Boolean
        get() = prefs.getBoolean(K_WATCH, false)
        set(v) { prefs.edit().putBoolean(K_WATCH, v).commit() }

    /** Aprovar pelo relógio Wear OS (app Ítalock no relógio, pela Data Layer do Google). */
    var expWear: Boolean
        get() = prefs.getBoolean(K_WEAR, false)
        set(v) { prefs.edit().putBoolean(K_WEAR, v).commit() }

    val anyExperimental get() = expNearby || expNotification || expWatch || expWear

    fun registerListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val K_THEME = "theme"
        const val K_VIBRATE = "vibrate"
        const val K_CONFIRM_LOCK = "confirm_lock"
        const val K_PROMPT_TIMEOUT = "prompt_timeout_s"
        const val K_APPROVE_LOCKED = "approve_while_locked"
        const val K_FULL_SCREEN = "lockscreen_prompt"
        const val K_HIDE_NAME = "hide_name_lockscreen"
        const val K_ALLOW_CREDENTIAL = "allow_credential"
        const val K_POLL = "poll_sec"
        const val K_NEARBY = "exp_nearby"
        const val K_NOTIF = "exp_notification"
        const val K_WATCH = "exp_watch"
        const val K_WEAR = "exp_wear"
        const val K_TAILSCALE = "tailscale_warning"

        const val PROMPT_TIMEOUT_DEFAULT = 10
        val PROMPT_TIMEOUTS = listOf(10, 20, 30, 60, 90)
        val POLL_OPTIONS = listOf(5, 15, 30, 0)
    }
}

/** Aparência escolhida pelo usuário. */
enum class ThemeMode(val key: String, @androidx.annotation.StringRes val labelRes: Int) {
    DARK("dark", br.italo.italock.R.string.theme_dark), LIGHT("light", br.italo.italock.R.string.theme_light),
    SYSTEM("system", br.italo.italock.R.string.theme_system);

    val label: String get() = br.italo.italock.i18n.L10n.s(labelRes)

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: DARK
    }
}
