package br.italo.italock.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Parcelable
import androidx.core.content.IntentCompat
import br.italo.italock.data.PairingStore
import br.italo.italock.data.Origin
import br.italo.italock.data.TagRule
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.ui.QuickUnlockActivity
import kotlin.concurrent.thread

/**
 * Tag NFC encostada (intent NDEF_DISCOVERED com italock://pc/<id>). Tela transparente. O que ela faz vem dos dois
 * interruptores dos Detalhes do computador ([TagRule]):
 *  - Travar e Destravar pela tag: consulta o cadeado; travado → destravar; destravado → travar (pergunta antes, se a
 *    opção estiver ligada); sem resposta → mensagem.
 *  - Só um dos dois: faz direto.
 *  - Nenhum: avisa que a tag desse computador está desligada.
 * Destravar pela tag SEMPRE pede a biometria, mesmo no modo sem confirmação: a tag fica colada em algum lugar e qualquer
 * um com o celular destravado na mão poderia encostar.
 * Com o celular bloqueado o Android não lê tags NFC (a leitura só acontece com a tela destravada), então isto só roda
 * com o celular já desbloqueado.
 */
class NfcTagActivity : QuickUnlockActivity() {
    override val origin: Origin get() = Origin.TAG

    override fun begin() {
        val id = idFrom(intent) ?: run { toast("Esta tag não é do Ítalock."); finish(); return }
        val pc = PairingStore(this).byPublicId(id) ?: run {
            toast("Esta tag é de um computador que não está pareado neste celular (ele foi removido?)."); finish(); return
        }
        if (TagRule.first(pc.tagLock, pc.tagUnlock) == TagRule.Step.NOTHING) {
            toast("A tag de ${pc.displayName} está desligada. Ligue Travar pela tag ou Destravar pela tag nos detalhes dele.")
            finish(); return
        }
        requireUnlocked {
            when (TagRule.first(pc.tagLock, pc.tagUnlock)) {
                TagRule.Step.UNLOCK -> perform(pc, ACTION_UNLOCK, unverified = true)
                TagRule.Step.LOCK -> perform(pc, ACTION_LOCK, unverified = false)
                TagRule.Step.NOTHING -> finish()
                TagRule.Step.ASK_STATE -> {
                    progress = "Consultando ${pc.displayName}"
                    thread {
                        val s = runCatching { LockClient.status(pc) }.getOrNull()
                        runOnUiThread {
                            progress = null
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            when (TagRule.afterState(pc.tagLock, pc.tagUnlock, s?.status)) {
                                TagRule.Step.UNLOCK -> perform(pc, ACTION_UNLOCK, unverified = true)
                                TagRule.Step.LOCK -> perform(pc, ACTION_LOCK, unverified = s?.verified != true)
                                else -> {
                                    toast(when (s?.status) {
                                        LockStatus.CLOCK_OFF -> "Relógio do celular fora de hora. Ative a data e hora automáticas."
                                        LockStatus.NOT_PAIRED, LockStatus.ERROR -> s.detail.ifEmpty { "${pc.displayName} recusou o pedido." }
                                        else -> "${pc.displayName} não respondeu. Ele está ligado e na mesma rede (ou no Tailscale)?"
                                    })
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Outra tag encostada com esta tela aberta: ignora (a primeira ação continua).
    }

    private fun idFrom(intent: Intent): String? {
        TagUri.parse(intent.dataString)?.let { return it }
        val raw: Array<Parcelable>? = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, Parcelable::class.java)
        val records = raw.orEmpty().filterIsInstance<NdefMessage>().flatMap { m ->
            m.records.map { TagUri.Record(it.tnf, it.type, it.payload) }
        }
        return TagUri.fromRecords(records)
    }
}
