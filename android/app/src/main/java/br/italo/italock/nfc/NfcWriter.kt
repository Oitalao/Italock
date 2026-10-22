package br.italo.italock.nfc

import android.net.Uri
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import br.italo.italock.R
import br.italo.italock.i18n.L10n
import java.io.IOException

/**
 * Grava a tag NFC de um computador (documentação oficial "NFC basics" e "Advanced NFC"): mensagem NDEF com o registro
 * URI `italock://pc/<id público>` e o Android Application Record. Roda na thread do leitor (enableReaderMode), nunca
 * na da tela: connect/write bloqueiam.
 */
object NfcWriter {
    enum class Result(val ok: Boolean, private val messageRes: Int) {
        OK(true, R.string.nfc_write_ok),
        OK_LOCKED(true, R.string.nfc_write_ok_locked),
        OK_LOCK_UNSUPPORTED(true, R.string.nfc_write_ok_lock_unsupported),
        READ_ONLY(false, R.string.nfc_write_read_only),
        TOO_SMALL(false, R.string.nfc_write_too_small),
        NOT_NDEF(false, R.string.nfc_write_not_ndef),
        LOST(false, R.string.nfc_write_lost),
        FAILED(false, R.string.nfc_write_failed);

        /** Frase para a tela, no idioma escolhido. */
        val message: String get() = L10n.s(messageRes)
    }

    fun message(publicId: String, packageName: String): NdefMessage = NdefMessage(
        arrayOf(
            NdefRecord.createUri(Uri.parse(TagUri.build(publicId))),
            NdefRecord.createApplicationRecord(packageName),
        ),
    )

    fun write(tag: Tag, msg: NdefMessage, makeReadOnly: Boolean): Result {
        Ndef.get(tag)?.let { ndef -> return writeNdef(ndef, msg, makeReadOnly) }
        NdefFormatable.get(tag)?.let { f -> return format(f, msg, makeReadOnly) }
        return Result.NOT_NDEF
    }

    private fun writeNdef(ndef: Ndef, msg: NdefMessage, makeReadOnly: Boolean): Result = try {
        ndef.connect()
        when {
            !ndef.isWritable -> Result.READ_ONLY
            ndef.maxSize < msg.byteArrayLength -> Result.TOO_SMALL
            else -> {
                ndef.writeNdefMessage(msg)
                if (!makeReadOnly) Result.OK
                else if (ndef.canMakeReadOnly() && ndef.makeReadOnly()) Result.OK_LOCKED
                else Result.OK_LOCK_UNSUPPORTED
            }
        }
    } catch (_: TagLostException) {
        Result.LOST
    } catch (_: IOException) {
        Result.LOST
    } catch (_: FormatException) {
        Result.FAILED
    } catch (_: Exception) {
        Result.FAILED
    } finally {
        runCatching { ndef.close() }
    }

    /** Tag vazia de fábrica (ainda sem NDEF): formata já com a mensagem. */
    private fun format(f: NdefFormatable, msg: NdefMessage, makeReadOnly: Boolean): Result = try {
        f.connect()
        if (makeReadOnly) { f.formatReadOnly(msg); Result.OK_LOCKED } else { f.format(msg); Result.OK }
    } catch (_: TagLostException) {
        Result.LOST
    } catch (_: IOException) {
        Result.LOST
    } catch (_: FormatException) {
        Result.FAILED
    } catch (_: Exception) {
        Result.FAILED
    } finally {
        runCatching { f.close() }
    }
}
