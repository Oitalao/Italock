package br.italo.italock.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Idioma do app. Português (Brasil) é o padrão de fábrica, mesmo com o celular em outro idioma; "Automático" segue o
 * idioma do sistema. A escolha fica nas preferências ("language") e vale para telas, notificações, atalho rápido,
 * widget e relógio Wear OS (que recebe a escolha junto com as mensagens do celular).
 *
 * Telas: cada atividade embrulha o contexto ([wrap]) e usa stringResource. Fora das telas (serviço, notificações,
 * widget, mensagens de erro): [s] e [q], que leem os textos já no idioma escolhido.
 * Android 13+: a escolha também vai para o idioma do app nos ajustes do sistema (e de lá volta, se mudar lá).
 */
object L10n {
    const val SYSTEM = "system"
    const val DEFAULT = "pt-BR"
    private const val PREFS = "italock"
    const val K_LANGUAGE = "language"
    /** O que foi gravado por último no idioma do app do Android 13+ (para perceber mudança feita nos ajustes). */
    private const val K_MIRROR = "language_mirror"

    /** Opções, na ordem da tela: Português (Brasil), Automático e cada idioma no próprio nome. */
    val TAGS = listOf(DEFAULT, SYSTEM, "en", "es", "fr", "de", "it", "ja", "ko", "zh-CN", "ru", "pl", "hi", "ar")

    /** Nome de cada idioma nele mesmo (não se traduz). "Automático" vem dos textos do app. */
    fun nativeName(tag: String): String? = when (tag) {
        DEFAULT -> "Português (Brasil)"
        "en" -> "English"
        "es" -> "Español"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "it" -> "Italiano"
        "ja" -> "日本語"
        "ko" -> "한국어"
        "zh-CN" -> "简体中文"
        "ru" -> "Русский"
        "pl" -> "Polski"
        "hi" -> "हिन्दी"
        "ar" -> "العربية"
        else -> null
    }

    @Volatile private var app: Context? = null
    @Volatile private var res: Resources? = null

    /** Só para os testes da JVM (sem Android): devolve o texto de um id. */
    @Volatile var testLookup: ((id: Int, quantity: Int?, args: Array<out Any>) -> String)? = null

    /** Chamado pela Application, antes de qualquer tela, serviço ou receptor. */
    fun init(base: Context) {
        app = base
        res = wrap(base).resources
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Escolha atual: um item de [TAGS]. */
    fun tag(ctx: Context): String = prefs(ctx).getString(K_LANGUAGE, null)?.takeIf { it in TAGS } ?: DEFAULT

    /** null = idioma do sistema. */
    fun localeFor(tag: String): Locale? = if (tag == SYSTEM) null else Locale.forLanguageTag(tag)

    /** Contexto com o idioma escolhido (no Automático, o próprio contexto). */
    fun wrap(base: Context): Context {
        val locale = localeFor(tag(base)) ?: return base
        val cfg = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= 24) cfg.setLocales(LocaleList(locale)) else @Suppress("DEPRECATION") cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** Recursos no idioma escolhido, para quem não tem tela. */
    private fun resources(): Resources = res ?: app?.let { wrap(it).resources.also { r -> res = r } }
        ?: error("L10n sem init")

    /** Texto no idioma escolhido (com argumentos, formatado no idioma). */
    fun s(@StringRes id: Int, vararg args: Any): String {
        testLookup?.let { return it(id, null, args) }
        val r = resources()
        return if (args.isEmpty()) r.getString(id) else r.getString(id, *args)
    }

    /** Plural no idioma escolhido; [count] escolhe a forma e os argumentos preenchem o texto. */
    fun q(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        testLookup?.let { return it(id, count, args) }
        return resources().getQuantityString(id, count, *args)
    }

    /** Idioma em que os textos estão saindo agora (para datas e para o relógio). */
    fun currentLocale(): Locale {
        val r = runCatching { resources() }.getOrNull() ?: return Locale.forLanguageTag(DEFAULT)
        return if (Build.VERSION.SDK_INT >= 24) r.configuration.locales[0] else @Suppress("DEPRECATION") r.configuration.locale
    }

    /** Caminho gravado (rótulos internos de [br.italo.italock.net.NetInfo]) no idioma escolhido. */
    fun via(v: String): String = when (v) {
        br.italo.italock.net.NetInfo.VIA_LAN -> s(br.italo.italock.R.string.via_lan)
        br.italo.italock.net.NetInfo.VIA_INTERNET -> s(br.italo.italock.R.string.via_internet)
        else -> v
    }

    /** "20:10" (24 horas, como sempre foi). */
    fun hourMinute(at: Long): String = pattern("Hm").format(java.util.Date(at))

    /** Dia e mês no jeito do idioma ("28/09", "9/28", "9月28日"). */
    fun dayMonth(at: Long): String = pattern("dM").format(java.util.Date(at))

    /** Data completa com hora ("28/09/2026 20:10" no padrão). */
    fun dateTime(at: Long): String = pattern("yMdHm").format(java.util.Date(at))

    private fun pattern(skeleton: String): java.text.SimpleDateFormat {
        val l = currentLocale()
        return java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, skeleton), l)
    }

    /**
     * Grava a escolha e atualiza o que não tem tela (canais e aviso fixo do serviço, atalho rápido, widget, atalhos do
     * ícone). A tela aberta se recria sozinha (quem chama faz recreate).
     */
    fun set(ctx: Context, tag: String) {
        if (tag !in TAGS) return
        val appCtx = ctx.applicationContext
        prefs(appCtx).edit().putString(K_LANGUAGE, tag).commit()
        app = appCtx
        res = wrap(appCtx).resources
        mirrorToSystem(appCtx, tag)
        onChanged(appCtx)
    }

    /** Atualiza os textos fora das telas depois de mudar o idioma. */
    private fun onChanged(ctx: Context) {
        runCatching { br.italo.italock.service.UnlockService.languageChanged(ctx) }
        runCatching { br.italo.italock.lock.TileRefresher.refresh(ctx) }
        runCatching { br.italo.italock.widget.PcWidgetProvider.updateAll(ctx) }
        Thread {
            runCatching { br.italo.italock.ui.AppShortcuts.sync(ctx, br.italo.italock.data.PairingStore(ctx).all()) }
        }.apply { isDaemon = true }.start()
    }

    // ---------------------------------------------------------------- Android 13+: idioma do app nos ajustes

    private fun systemTagFor(tag: String) = if (tag == SYSTEM) "" else tag

    private fun mirrorToSystem(ctx: Context, tag: String) {
        if (Build.VERSION.SDK_INT < 33) return
        runCatching {
            val lm = ctx.getSystemService(LocaleManager::class.java) ?: return
            val want = systemTagFor(tag)
            prefs(ctx).edit().putString(K_MIRROR, want).commit()
            if (lm.applicationLocales.toLanguageTags() != want) lm.applicationLocales = LocaleList.forLanguageTags(want)
        }
    }

    /** Idioma do sistema (ex.: "zh-Hans-CN", "pt-BR") para uma das opções; idioma que o app não tem vira Automático. */
    fun matchTag(systemTag: String): String {
        if (systemTag.isEmpty()) return SYSTEM
        val l = Locale.forLanguageTag(systemTag.split(',').first())
        return when (l.language) {
            "pt" -> DEFAULT
            "zh" -> "zh-CN"
            else -> TAGS.firstOrNull { it == l.language } ?: SYSTEM
        }
    }

    /**
     * Android 13+: acompanha o idioma do app escolhido nos ajustes do sistema e, na primeira vez, leva a escolha do app
     * para lá. Devolve true se a escolha do app mudou (a tela aberta deve se recriar).
     */
    fun syncWithSystem(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            val appCtx = ctx.applicationContext ?: ctx
            val lm = appCtx.getSystemService(LocaleManager::class.java) ?: return false
            val sys = lm.applicationLocales.toLanguageTags()
            val p = prefs(appCtx)
            val mirrored = p.getString(K_MIRROR, null)
            if (mirrored == null || sys == mirrored) {
                if (mirrored == null) mirrorToSystem(appCtx, tag(appCtx))
                return false
            }
            // Mudou nos ajustes do Android.
            val chosen = matchTag(sys)
            p.edit().putString(K_LANGUAGE, chosen).putString(K_MIRROR, sys).commit()
            app = appCtx
            res = wrap(appCtx).resources
            onChanged(appCtx)
            true
        }.getOrDefault(false)
    }
}
