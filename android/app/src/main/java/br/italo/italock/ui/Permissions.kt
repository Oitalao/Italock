package br.italo.italock.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.italo.italock.service.UnlockService

/** O que falta liberar no celular. Cada item só aparece quando falta; cada botão abre o ajuste certo. */
enum class Missing { NOTIFICATIONS, FULL_SCREEN, BATTERY, BLUETOOTH }

object Permissions {
    fun missing(ctx: Context): List<Missing> = buildList {
        if (!notificationsOk(ctx)) add(Missing.NOTIFICATIONS)
        // Só falta se a pessoa quer o pedido na tela de bloqueio.
        if (Build.VERSION.SDK_INT >= 34 && br.italo.italock.data.AppPrefs(ctx).fullScreenPrompt &&
            !NotificationManagerCompat.from(ctx).canUseFullScreenIntent()
        ) add(Missing.FULL_SCREEN)
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) add(Missing.BATTERY)
        if (Build.VERSION.SDK_INT >= 31 && !granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)) add(Missing.BLUETOOTH)
    }

    /** App com notificações liberadas E o canal dos pedidos não silenciado. */
    fun notificationsOk(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(UnlockService.CHANNEL_PROMPT)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun granted(ctx: Context, permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    /** Tela de notificações do app (ou do canal dos pedidos, se só ele estiver desligado). */
    fun notificationSettings(ctx: Context): Intent = when {
        Build.VERSION.SDK_INT >= 26 && NotificationManagerCompat.from(ctx).areNotificationsEnabled() ->
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, UnlockService.CHANNEL_PROMPT)
        Build.VERSION.SDK_INT >= 26 ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        else -> appDetails(ctx)
    }

    fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    @SuppressLint("BatteryLife") // o app precisa ficar escutando os pedidos do computador
    fun batterySettings(ctx: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    fun fullScreenSettings(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
        else appDetails(ctx)

    /** Abre o ajuste; se o aparelho não tiver essa tela, cai nos detalhes do app. */
    fun open(activity: Activity, intent: Intent) {
        runCatching { activity.startActivity(intent) }.onFailure { runCatching { activity.startActivity(appDetails(activity)) } }
    }
}
