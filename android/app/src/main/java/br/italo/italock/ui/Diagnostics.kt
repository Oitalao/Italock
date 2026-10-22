package br.italo.italock.ui

import android.content.Context
import android.os.Build
import br.italo.italock.BuildConfig
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.DiagLog
import br.italo.italock.data.PairingStore
import br.italo.italock.R
import br.italo.italock.i18n.L10n
import br.italo.italock.service.UnlockService
import br.italo.italock.watch.WearBridge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Arquivo de texto de "Exportar registros": versão, aparelho, opções, computadores (sem chaves, sem senhas, sem ids
 * internos) e o registro de eventos. Fica na pasta de cache do app e sai por compartilhamento (FileProvider).
 */
object Diagnostics {
    fun write(context: Context): File {
        val dir = File(context.cacheDir, "diagnostico").apply { mkdirs() }
        File(dir, "italock-registros.txt").delete() // nome antigo
        val file = File(dir, "ItalockRegistros.txt")
        file.writeText(build(context))
        return file
    }

    fun build(context: Context): String {
        val p = AppPrefs(context)
        val store = PairingStore(context)
        val sb = StringBuilder()
        fun line(k: String, v: Any?) { sb.append(k).append(": ").append(v).append('\n') }
        fun t(id: Int) = L10n.s(id)
        sb.append(t(R.string.diag_header)).append('\n')
        line(t(R.string.diag_generated), SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date()))
        line(t(R.string.diag_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line(t(R.string.diag_device), "${Build.MANUFACTURER} ${Build.MODEL}")
        sb.append('\n')
        line(t(R.string.diag_theme), p.theme.label)
        line(t(R.string.diag_vibrate), p.vibrate)
        line(t(R.string.diag_confirm_lock), p.confirmLock)
        line(t(R.string.diag_prompt_timeout), p.promptTimeoutSec)
        line(t(R.string.diag_approve_locked), p.approveWhileLocked)
        line(t(R.string.diag_fullscreen), p.fullScreenPrompt)
        line(t(R.string.diag_hide_name), p.hideNameOnLockscreen)
        line(t(R.string.diag_allow_credential), p.allowDeviceCredential)
        line(t(R.string.diag_poll), p.pollSec)
        line(t(R.string.diag_ts_warning), p.tailscaleWarning)
        line(t(R.string.diag_ts_installed), UnlockService.tailscaleInstalled(context))
        line(t(R.string.diag_ts_up), UnlockService.tailscaleUp(context))
        line(t(R.string.diag_google), WearBridge.available(context))
        line(t(R.string.diag_experimental), "${p.expNearby}, ${p.expNotification}, ${p.expWatch}, ${p.expWear}")
        line(t(R.string.diag_missing), Permissions.missing(context).joinToString().ifEmpty { t(R.string.diag_nothing) })
        sb.append('\n')
        val pcs = store.all()
        line(t(R.string.diag_pcs), pcs.size)
        pcs.forEachIndexed { i, pc ->
            sb.append("\n[${i + 1}] ${pc.displayName}\n")
            line("  " + t(R.string.diag_pc_os), pc.pcOs)
            line("  " + t(R.string.diag_pc_method), pc.pairingMethod)
            line("  " + t(R.string.diag_pc_addresses), pc.pcIps.size.coerceAtLeast(if (pc.pcIp.isNotBlank()) 1 else 0))
            line("  " + t(R.string.diag_pc_paths), "${pc.allowLan}, ${pc.allowBluetooth}, ${pc.allowTailscale}")
            line("  " + t(R.string.diag_pc_sealed), pc.sealedSeen)
            line("  " + t(R.string.diag_pc_no_confirm), pc.noConfirmActive)
            line("  " + t(R.string.diag_experimental), "${pc.autoUnlock}, ${pc.notificationApprove}, ${pc.watchApprove}, ${pc.wearApprove}")
            line("  " + t(R.string.diag_pc_tag), br.italo.italock.data.TagRule.summary(pc.tagLock, pc.tagUnlock))
            line("  " + t(R.string.diag_pc_wake), pc.macAddresses.isNotEmpty())
            line("  " + t(R.string.diag_pc_last), if (pc.lastEventAt == 0L) t(R.string.diag_none) else lastEventText(pc))
        }
        sb.append('\n').append(t(R.string.diag_events)).append('\n')
        sb.append(DiagLog.read().ifEmpty { t(R.string.diag_no_events) + "\n" })
        return sb.toString()
    }
}
