package br.italo.italock.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import br.italo.italock.R
import br.italo.italock.data.DiagLog
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.i18n.L10n

/**
 * Atalhos do ícone do app (toque longo no ícone, Android 7.1+): "Destravar <PC>" e "Travar <PC>" dos primeiros
 * computadores da lista, até [MAX]. Abrem a mesma tela transparente do widget: destravar pede a biometria (salvo o modo
 * sem confirmação, porque o atalho é um pedido iniciado no celular); travar respeita "Confirmar antes de travar".
 * Só mexe no sistema quando a lista de atalhos muda de verdade (nome, ordem, computadores): gravar o "último uso" não
 * gasta nada.
 */
object AppShortcuts {
    const val MAX = 4

    /** O que cada atalho mostra, sem nada de Android (para os testes). */
    data class Entry(val id: String, val deviceId: String, val action: String, val label: String)

    /** Pares Destravar/Travar dos primeiros computadores, até [max] atalhos. */
    fun plan(pcs: List<PairedPc>, max: Int = MAX): List<Entry> = pcs.flatMap { pc ->
        listOf(
            Entry(id(pc.deviceId, QuickUnlockActivity.ACTION_UNLOCK), pc.deviceId, QuickUnlockActivity.ACTION_UNLOCK, L10n.s(R.string.shortcut_unlock, pc.displayName)),
            Entry(id(pc.deviceId, QuickUnlockActivity.ACTION_LOCK), pc.deviceId, QuickUnlockActivity.ACTION_LOCK, L10n.s(R.string.shortcut_lock, pc.displayName)),
        )
    }.take(max.coerceAtLeast(0))

    fun id(deviceId: String, action: String) = "$action:${deviceId.lowercase()}"

    /** Publica os atalhos da lista atual (se mudaram). Faz IPC: fora da thread da tela quando possível. */
    fun sync(context: Context, pcs: List<PairedPc>) {
        val app = context.applicationContext
        runCatching {
            // Todo lançador aceita pelo menos 4 atalhos por atividade; sem consultar o sistema a cada gravação.
            val entries = plan(pcs, MAX)
            val signature = entries.joinToString("\n") { "${it.id}|${it.label}" }
            val prefs = app.getSharedPreferences("italock", Context.MODE_PRIVATE)
            if (prefs.getString(K_SIGNATURE, null) == signature) return
            fun info(e: Entry): ShortcutInfoCompat {
                val intent = Intent(app, QuickUnlockActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .putExtra(QuickUnlockActivity.EXTRA_DEVICE, e.deviceId)
                    .putExtra(QuickUnlockActivity.EXTRA_ACTION, e.action)
                    .putExtra(QuickUnlockActivity.EXTRA_ORIGIN, Origin.SHORTCUT.key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val lock = e.action == QuickUnlockActivity.ACTION_LOCK
                return ShortcutInfoCompat.Builder(app, e.id)
                    .setShortLabel(e.label)
                    .setLongLabel(e.label)
                    .setIcon(IconCompat.createWithResource(app, if (lock) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock))
                    .setIntent(intent)
                    .build()
            }
            val infos = entries.map(::info)
            // Atalhos fixados na tela inicial de um computador que saiu da lista: desativados com um aviso claro.
            val keep = pcs.flatMap { listOf(id(it.deviceId, QuickUnlockActivity.ACTION_UNLOCK), id(it.deviceId, QuickUnlockActivity.ACTION_LOCK)) }.toSet()
            val gone = runCatching {
                ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED)
                    .map { it.id }.filter { it !in keep }
            }.getOrDefault(emptyList())
            if (gone.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(app, gone, L10n.s(R.string.shortcut_removed))
            // Computador pareado de novo: um atalho fixado que tinha sido desativado volta a funcionar.
            runCatching { ShortcutManagerCompat.enableShortcuts(app, plan(pcs, Int.MAX_VALUE).map(::info)) }
            if (!ShortcutManagerCompat.setDynamicShortcuts(app, infos)) return // limite de chamadas: tenta na próxima mudança
            // Nome novo também nos atalhos fixados (inclusive de computadores fora dos primeiros da lista).
            runCatching { ShortcutManagerCompat.updateShortcuts(app, plan(pcs, Int.MAX_VALUE).map(::info)) }
            prefs.edit().putString(K_SIGNATURE, signature).apply()
        }.onFailure { DiagLog.w("atalhos do ícone: ${it.javaClass.simpleName}") }
    }

    private const val K_SIGNATURE = "shortcutsSignature"
}
