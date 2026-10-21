package br.italo.italock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Sobe o serviço depois de reiniciar o celular (BOOT_COMPLETED) ou atualizar o app (MY_PACKAGE_REPLACED).
 * LOCKED_BOOT_COMPLETED não é usado: o app não roda antes do primeiro desbloqueio (os pareamentos ficam no
 * armazenamento protegido pela senha do celular).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            if (PairingStorePresence.hasPairings(context)) UnlockService.start(context)
        } catch (e: Exception) {
            // Ex.: ForegroundServiceStartNotAllowedException. O serviço sobe na próxima vez que o app abrir.
            Log.w("Italock", "não deu para iniciar o serviço (${intent.action}): ${e.message}")
        }
    }
}

object PairingStorePresence {
    fun hasPairings(context: Context) = br.italo.italock.data.PairingStore(context).all().isNotEmpty()
}
