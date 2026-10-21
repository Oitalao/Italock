package br.italo.italock

import br.italo.italock.nfc.TagUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagUriTest {
    private val id = "0123456789abcdef0123456789abcdef"

    @Test fun buildAndParse() {
        assertEquals("italock://pc/$id", TagUri.build(id))
        assertEquals(id, TagUri.parse(TagUri.build(id)))
        // Esquema e host sem diferença de maiúsculas; id devolvido em minúsculas.
        assertEquals(id, TagUri.parse("ITALOCK://PC/${id.uppercase()}"))
        assertEquals(id, TagUri.parse("italock://pc/$id/"))
    }

    @Test fun rejectsOthers() {
        assertNull(TagUri.parse(null))
        assertNull(TagUri.parse("https://pc/$id"))
        assertNull(TagUri.parse("italock://computador/$id"))
        assertNull(TagUri.parse("italock://pc/"))
        assertNull(TagUri.parse("italock://pc/nao-hex"))
        assertNull(TagUri.parse("italock://pc/abc")) // curto demais
        assertNull(TagUri.parse("italock://pc/$id/extra"))
    }

    @Test fun ndefUriRecordWithoutPrefix() {
        // Como NdefRecord.createUri grava um esquema próprio: código 0x00 + a URI inteira.
        val payload = TagUri.encodeUriPayload(TagUri.build(id))
        assertEquals(0, payload[0].toInt())
        val records = listOf(
            TagUri.Record(TagUri.TNF_WELL_KNOWN, TagUri.RTD_URI, payload),
            // Android Application Record (TNF externo, tipo "android.com:pkg"): ignorado na leitura.
            TagUri.Record(0x04, "android.com:pkg".toByteArray(), TagUri.AAR_PACKAGE.toByteArray()),
        )
        assertEquals(id, TagUri.fromRecords(records))
    }

    @Test fun ndefUriPrefixTableAndAbsoluteUri() {
        assertEquals("https://www.exemplo.com", TagUri.decodeUriPayload(byteArrayOf(0x02) + "exemplo.com".toByteArray()))
        assertNull(TagUri.decodeUriPayload(byteArrayOf(0x7F)))
        assertNull(TagUri.decodeUriPayload(byteArrayOf()))
        // Registro com prefixo http não é do Ítalock.
        assertNull(TagUri.fromRecords(listOf(TagUri.Record(TagUri.TNF_WELL_KNOWN, TagUri.RTD_URI, byteArrayOf(0x03) + "x.com".toByteArray()))))
        // TNF de URI absoluta: a URI vem no tipo.
        assertEquals(id, TagUri.fromRecords(listOf(TagUri.Record(TagUri.TNF_ABSOLUTE_URI, TagUri.build(id).toByteArray(), byteArrayOf()))))
    }

    @Test fun firstMatchingRecordWins() {
        val other = TagUri.Record(TagUri.TNF_WELL_KNOWN, "T".toByteArray(), "texto".toByteArray())
        val ours = TagUri.Record(TagUri.TNF_WELL_KNOWN, TagUri.RTD_URI, TagUri.encodeUriPayload(TagUri.build(id)))
        assertEquals(id, TagUri.fromRecords(listOf(other, ours)))
        assertNull(TagUri.fromRecords(listOf(other)))
    }
}
