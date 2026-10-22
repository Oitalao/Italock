package br.italo.italock.wear

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import java.util.Locale

/**
 * Idioma do relógio: o mesmo escolhido no Ítalock do celular, que manda o campo "lang" em toda mensagem.
 * Antes da primeira mensagem (ou com um celular de versão antiga) fica em português do Brasil, o padrão.
 * "system" = segue o idioma do próprio relógio.
 */
object WearLang {
    const val DEFAULT = "pt-BR"
    const val SYSTEM = "system"
    private const val PREFS = "lang"
    private const val K_LANG = "lang"

    fun tag(ctx: Context): String =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LANG, null) ?: DEFAULT

    /** Guarda o idioma vindo do celular. Devolve true se mudou. */
    fun save(ctx: Context, lang: String): Boolean {
        val clean = lang.trim()
        if (clean.isEmpty() || (clean != SYSTEM && Locale.forLanguageTag(clean).language.isEmpty())) return false
        if (clean == tag(ctx)) return false
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(K_LANG, clean) }
        return true
    }

    /** Contexto no idioma escolhido (no "system", o próprio contexto). Usado no attachBaseContext das telas. */
    fun wrap(base: Context): Context {
        val t = tag(base)
        if (t == SYSTEM) return base
        val locale = Locale.forLanguageTag(t)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocales(LocaleList(locale))
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** Texto no idioma escolhido, para serviço, tile e notificação. */
    fun s(ctx: Context, @StringRes id: Int, vararg args: Any): String {
        val r = wrap(ctx.applicationContext).resources
        return if (args.isEmpty()) r.getString(id) else r.getString(id, *args)
    }
}
