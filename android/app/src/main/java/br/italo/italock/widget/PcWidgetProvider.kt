package br.italo.italock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import br.italo.italock.R
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.PairingStore
import br.italo.italock.data.ThemeMode
import br.italo.italock.i18n.L10n
import br.italo.italock.service.ActionReceiver
import br.italo.italock.ui.QuickUnlockActivity

/**
 * Widget por PC: Destravar (abre a confirmação), Travar (pergunta antes, se a opção estiver ligada) e Ligar (quando o
 * PC tem placa com cabo). Tocar no nome escolhe outro computador. Mostra o cadeado da última consulta e a hora dela;
 * consulta só no Atualizar ou depois de uma ação ([WidgetStatus]). Cores conforme a Aparência do app.
 */
class PcWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { prefs.remove("w$it") }
        prefs.apply()
    }

    companion object {
        const val PREFS = "italock-widgets"

        /** Redesenha todos os widgets (depois de salvar, renomear, remover um PC ou mudar a Aparência). */
        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, PcWidgetProvider::class.java))
                ids.forEach { update(context, manager, it) }
            }
        }

        /** Escuro/claro fixos pela escolha do app; "igual ao sistema" usa o layout que segue o sistema (values-night). */
        private fun layoutFor(context: Context): Int = when (AppPrefs(context).theme) {
            ThemeMode.DARK -> R.layout.widget_pc_dark
            ThemeMode.LIGHT -> R.layout.widget_pc_light
            ThemeMode.SYSTEM -> R.layout.widget_pc
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val deviceId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("w$id", null) ?: return
            val pc = PairingStore(context).byDeviceId(deviceId)
            val views = RemoteViews(context.packageName, layoutFor(context))
            views.setTextViewText(R.id.pc_name, pc?.displayName ?: L10n.s(R.string.widget_removed))
            // O lançador desenha o widget no idioma do sistema: todo texto vem daqui, no idioma escolhido no app.
            views.setTextViewText(R.id.btn_lock, L10n.s(R.string.action_lock))
            views.setTextViewText(R.id.btn_unlock, L10n.s(R.string.action_unlock))
            views.setTextViewText(R.id.btn_wake, L10n.s(R.string.action_wake))
            views.setTextViewText(R.id.danger, L10n.s(R.string.danger_strip))
            views.setContentDescription(R.id.btn_refresh, L10n.s(R.string.widget_refresh))
            fun activity(action: String) = PendingIntent.getActivity(
                context, (action + id).hashCode(),
                Intent(context, QuickUnlockActivity::class.java).putExtra(QuickUnlockActivity.EXTRA_DEVICE, deviceId)
                    .putExtra(QuickUnlockActivity.EXTRA_ACTION, action)
                    .putExtra(QuickUnlockActivity.EXTRA_ORIGIN, br.italo.italock.data.Origin.WIDGET.key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val wake = PendingIntent.getBroadcast(
                context, (ActionReceiver.ACTION_WAKE + id).hashCode(),
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_WAKE).putExtra(ActionReceiver.EXTRA_DEVICE, deviceId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Tocar no nome: escolher outro computador para este widget.
            val choose = PendingIntent.getActivity(
                context, ("choose$id").hashCode(),
                Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Destravar e Travar passam pela tela transparente (confirmação com biometria; "Travar?" se ligado).
            views.setOnClickPendingIntent(R.id.btn_unlock, activity(QuickUnlockActivity.ACTION_UNLOCK))
            views.setOnClickPendingIntent(R.id.btn_lock, activity(QuickUnlockActivity.ACTION_LOCK))
            views.setOnClickPendingIntent(R.id.btn_wake, wake)
            views.setOnClickPendingIntent(R.id.header, choose)
            // Atualizar: consulta o cadeado só agora (nada periódico).
            val refresh = PendingIntent.getBroadcast(
                context, (ActionReceiver.ACTION_WIDGET_REFRESH + id).hashCode(),
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_WIDGET_REFRESH)
                    .putExtra(ActionReceiver.EXTRA_DEVICE, deviceId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refresh)
            val shown = WidgetStatus.read(context, deviceId)
            views.setTextViewText(R.id.status, if (pc == null) "" else WidgetStatus.label(shown))
            views.setImageViewResource(R.id.status_icon,
                if (!shown.querying && shown.state == "unlocked") R.drawable.ic_lock_open else R.drawable.ic_lock)
            views.setViewVisibility(R.id.btn_refresh, if (pc == null) View.GONE else View.VISIBLE)
            val present = if (pc == null) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.btn_unlock, present)
            views.setViewVisibility(R.id.btn_lock, present)
            views.setViewVisibility(R.id.btn_wake, if (pc?.macAddresses.isNullOrEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.danger, if (pc?.noConfirmActive == true) View.VISIBLE else View.GONE)
            manager.updateAppWidget(id, views)
        }
    }
}
