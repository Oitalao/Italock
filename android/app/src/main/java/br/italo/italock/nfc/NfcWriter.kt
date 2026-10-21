package br.italo.italock.nfc

import android.net.Uri
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

/**
 * Grava a tag NFC de um computador (documentação oficial "NFC basics" e "Advanced NFC"): mensagem NDEF com o registro
 * URI `italock://pc/<id público>` e o Android Application Record. Roda na thread do leitor (enableReaderMode), nunca
 * na da tela: connect/write bloqueiam.
 */
object NfcWriter {
    enum class Result(val ok: Boolean, val message: String) {
        OK(true, "Tag gravada. Com o celular desbloqueado, encoste nela para usar."),
        OK_LOCKED(true, "Tag gravada e protegida contra escrita."),
        OK_LOCK_UNSUPPORTED(true, "Tag gravada, mas este modelo não permite proteger contra escrita."),
        READ_ONLY(false, "Esta tag está protegida contra escrita. Use outra."),
        TOO_SMALL(false, "A tag não tem espaço suficiente. Use uma NTAG213 ou maior."),
        NOT_NDEF(false, "Esta tag não aceita o formato NDEF. Use outra, por exemplo NTAG213, 215 ou 216."),
        LOST(false, "A tag se afastou antes de terminar. Encoste de novo e segure até o aviso."),
        FAILED(false, "Não deu para gravar a tag. Tente de novo ou use outra."),
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
