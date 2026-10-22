package br.italo.italock.watch

import br.italo.italock.R
import br.italo.italock.crypto.KeyGuard
import br.italo.italock.i18n.L10n
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.DiagLog
import br.italo.italock.data.EventKind
import br.italo.italock.data.Events
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.lock.WakeOnLan
import br.italo.italock.service.UnlockFlow
import br.italo.italock.service.UnlockSession
import br.italo.italock.ui.Haptics
import br.italo.italock.ui.QuickUnlockActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Mensagens que chegam do relógio Wear OS. Só é chamado pelo Google Play Services (sem ele, nunca roda).
 * Tudo exige a chave global "Relógio Wear OS" ligada nos recursos experimentais.
 */
class PhoneWearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val prefs = AppPrefs(this)
        DiagLog.init(this)
        val json = runCatching { JSONObject(String(event.data, Charsets.UTF_8)) }.getOrDefault(JSONObject())
        when (event.path) {
            WearBridge.PATH_ANSWER -> if (prefs.expWear) answer(json)
            WearBridge.PATH_PCS_GET -> sendPcs(event.sourceNodeId, prefs.expWear)
            WearBridge.PATH_ACTION -> action(event.sourceNodeId, json, prefs.expWear)
        }
    }

    /** Resposta ao pedido do computador mostrado no relógio. */
    private fun answer(json: JSONObject) {
        val s = UnlockSession.active[json.optString("session")] ?: return
        if (s.finished) return
        if (!json.optBoolean("approve")) {
            thread(isDaemon = true) { s.deny(origin = Origin.WATCH) }
            return
        }
        // O relógio precisa ter bloqueio de tela: sem ele, quem pegar o relógio destravaria o computador.
        if (!json.optBoolean("secure")) { DiagLog.i("relógio sem bloqueio de tela: aprovação ignorada"); return }
        val pc = PairingStore(this).byDeviceId(s.pc.deviceId) ?: return
        if (!(pc.wearApprove && pc.hasAutoKey)) return
        thread(isDaemon = true) {
            val key = runCatching { KeyGuard.openAuto(pc.autoKeyEnc, pc.autoKeyIv) }.getOrNull()
            if (key != null) s.approve(key, Origin.WATCH) else s.deny("APP_ERROR")
        }
    }

    /**
     * Nome mostrado no relógio: com "Esconder nome no bloqueio" ligado vira "Computador 1", "Computador 2"... (o relógio
     * fica à mostra no pulso, como a tela de bloqueio).
     */
    private fun watchName(pc: PairedPc, all: List<PairedPc>, hide: Boolean): String {
        if (!hide) return pc.displayName
        val i = all.indexOfFirst { it.deviceId == pc.deviceId }
        return if (i < 0) L10n.s(R.string.wearmsg_computer) else L10n.s(R.string.wearmsg_computer_n, i + 1)
    }

    /** Lista dos computadores com o cadeado consultado agora (só quando o relógio pede: nada em segundo plano). */
    private fun sendPcs(nodeId: String, enabled: Boolean) {
        // Só os computadores com "Relógio Wear OS" ligado: os outros recusariam qualquer toque.
        val pcs = if (enabled) PairingStore(this).all().filter { it.wearApprove } else emptyList()
        val hide = AppPrefs(this).hideNameOnLockscreen
        val states = java.util.concurrent.ConcurrentHashMap<String, String>()
        val latch = CountDownLatch(pcs.size)
        for (pc in pcs) thread(isDaemon = true) {
            val r = runCatching { LockClient.status(pc) }.getOrNull()
            states[pc.deviceId] = when (r?.status) {
                LockStatus.LOCKED -> "locked"
                LockStatus.UNLOCKED -> "unlocked"
                LockStatus.CLOCK_OFF -> "clock"
                LockStatus.NO_ANSWER -> "no_answer"
                else -> "unknown"
            }
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        val arr = JSONArray()
        pcs.forEach { pc ->
            arr.put(JSONObject().put("id", pc.publicId).put("name", watchName(pc, PairingStore(this).all(), hide))
                .put("state", states[pc.deviceId] ?: "unknown").put("wake", pc.macAddresses.isNotEmpty()))
        }
        WearBridge.reply(this, nodeId, WearBridge.PATH_PCS,
            WearBridge.withLang(this, JSONObject().put("pcs", arr).put("at", System.currentTimeMillis())))
    }

    private fun action(nodeId: String, json: JSONObject, enabled: Boolean) {
        val id = json.optString("id")
        val act = json.optString("action")
        val store = PairingStore(this)
        val all = store.all()
        val pc = all.firstOrNull { it.publicId == id }
        val hide = AppPrefs(this).hideNameOnLockscreen
        fun result(ok: Boolean, message: String, confirmOnPhone: Boolean = false) {
            // Mesma regra de privacidade da lista: o nome do computador não aparece no relógio.
            val text = if (pc != null && hide && pc.displayName.isNotBlank()) message.replace(pc.displayName, watchName(pc, all, true)) else message
            if (!confirmOnPhone) Haptics.done(this, ok)
            WearBridge.reply(this, nodeId, WearBridge.PATH_RESULT, WearBridge.withLang(this,
                JSONObject().put("id", id).put("action", act).put("ok", ok).put("message", text).put("confirmOnPhone", confirmOnPhone)))
        }
        if (!enabled) { result(false, L10n.s(R.string.wearmsg_feature_off)); return }
        if (pc == null) { result(false, L10n.s(R.string.wearmsg_pc_not_found)); return }
        // A opção é por computador: desligada, o relógio não manda nada nele.
        if (!pc.wearApprove) { result(false, L10n.s(R.string.wearmsg_pc_off, pc.displayName)); return }
        when (act) {
            "lock" -> {
                // "Confirmar antes de travar" vale aqui também: a pergunta aparece no celular.
                if (AppPrefs(this).confirmLock) {
                    UnlockFlow.askOnPhone(this, pc, QuickUnlockActivity.ACTION_LOCK)
                    result(false, L10n.s(R.string.wearmsg_confirm_on_phone), true)
                    return
                }
                val r = LockClient.lockOutcome(pc)
                if (r.ok) {
                    Events.record(this, pc.deviceId, EventKind.LOCKED, Origin.WATCH, r.via)
                    br.italo.italock.service.UnlockedNotice.cancel(this, pc.deviceId)
                    br.italo.italock.widget.WidgetStatus.refreshLater(this, pc.deviceId, 1_500)
                }
                result(r.ok, r.message(pc))
            }
            "wake" -> WakeOnLan.wake(pc.macAddresses).let { result(it.ok, it.message) }
            "unlock" -> unlock(pc) { ok, msg, confirm -> result(ok, msg, confirm) }
            else -> result(false, L10n.s(R.string.wearmsg_unknown_action))
        }
    }

    /**
     * Destravar tocado no relógio: SEMPRE com a biometria no celular (notificação que abre a confirmação), nunca pelo
     * modo sem confirmação. Quem aprova pelo relógio sem o celular é só o fluxo normal: o computador pede, o pedido
     * aparece no relógio e a pessoa aprova lá ([answer], que exige a opção do computador e o relógio destravado).
     */
    private fun unlock(pc: PairedPc, result: (Boolean, String, Boolean) -> Unit) {
        if (br.italo.italock.service.PreApproval.openSession(pc.deviceId) != null) {
            result(false, L10n.s(R.string.wearmsg_already_asking, pc.displayName), true); return
        }
        UnlockFlow.askOnPhone(this, pc)
        result(false, L10n.s(R.string.wearmsg_confirm_on_phone), true)
    }
}
