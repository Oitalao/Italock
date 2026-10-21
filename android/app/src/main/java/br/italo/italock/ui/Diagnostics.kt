package br.italo.italock.ui

import android.content.Context
import android.os.Build
import br.italo.italock.BuildConfig
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.DiagLog
import br.italo.italock.data.PairingStore
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
        sb.append("Registros do Ítalock\n")
        line("Gerado em", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date()))
        line("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("Aparelho", "${Build.MANUFACTURER} ${Build.MODEL}")
        sb.append('\n')
        line("Aparência", p.theme.label)
        line("Vibrar", p.vibrate)
        line("Confirmar antes de travar", p.confirmLock)
        line("Tempo do pedido (s)", p.promptTimeoutSec)
        line("Destravar com o celular bloqueado", p.approveWhileLocked)
        line("Pedido na tela de bloqueio", p.fullScreenPrompt)
        line("Esconder nome", p.hideNameOnLockscreen)
        line("Aceitar PIN, padrão ou senha", p.allowDeviceCredential)
        line("Atualizar cadeado (s, 0 = ao abrir)", p.pollSec)
        line("Aviso do Tailscale", p.tailscaleWarning)
        line("Tailscale instalado", UnlockService.tailscaleInstalled(context))
        line("Tailscale conectado", UnlockService.tailscaleUp(context))
        line("Serviços do Google", WearBridge.available(context))
        line("Experimentais (perto, notificação, Zepp, Wear OS)", "${p.expNearby}, ${p.expNotification}, ${p.expWatch}, ${p.expWear}")
        line("Falta permitir", Permissions.missing(context).joinToString().ifEmpty { "nada" })
        sb.append('\n')
        val pcs = store.all()
        line("Computadores", pcs.size)
        pcs.forEachIndexed { i, pc ->
            sb.append("\n[${i + 1}] ${pc.displayName}\n")
            line("  Sistema", pc.pcOs)
            line("  Forma de chamar", pc.pairingMethod)
            line("  Endereços conhecidos", pc.pcIps.size.coerceAtLeast(if (pc.pcIp.isNotBlank()) 1 else 0))
            line("  Caminhos (rede local, Bluetooth, Tailscale)", "${pc.allowLan}, ${pc.allowBluetooth}, ${pc.allowTailscale}")
            line("  Resposta cifrada", pc.sealedSeen)
            line("  Sem confirmação", pc.noConfirmActive)
            line("  Experimentais (perto, notificação, Zepp, Wear OS)", "${pc.autoUnlock}, ${pc.notificationApprove}, ${pc.watchApprove}, ${pc.wearApprove}")
            line("  Tag NFC", br.italo.italock.data.TagRule.summary(pc.tagLock, pc.tagUnlock))
            line("  Ligar pela rede", pc.macAddresses.isNotEmpty())
            line("  Último uso", if (pc.lastEventAt == 0L) "nenhum" else lastEventText(pc))
        }
        sb.append("\nEventos recentes\n")
        sb.append(DiagLog.read().ifEmpty { "(nenhum)\n" })
        return sb.toString()
    }
}
