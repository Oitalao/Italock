package br.italo.italock

import br.italo.italock.data.PairedPc
import br.italo.italock.net.Frame
import br.italo.italock.service.UnlockSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable

/** Pedido automático do PC (tela de entrada do boot): o celular responde WAITING, que não conta como recusa. */
class AutoRequestTest {
    private val pc = PairedPc(
        deviceId = "AAAA-2", encKey = "k", passwordKeyEnc = "", passwordKeyIv = "", pcName = "Notebook", pcOs = "Windows",
        userName = "u", pairingMethod = "BLUETOOTH", pcIp = "192.168.0.2", unlockServerPort = 43296, macAddresses = emptyList(),
    )

    @Test fun waitingIsSentAndIsNotADenial() {
        val out = ByteArrayOutputStream()
        val s = UnlockSession(pc = pc, user = "u", program = "", via = "Tailscale", unlockToken = "t", output = out,
            closeable = Closeable { }, automatic = true)
        var denied = false
        val old = UnlockSession.onDenied
        UnlockSession.onDenied = { _, _, _ -> denied = true }
        try {
            assertTrue(s.automatic)
            assertTrue(s.deny("WAITING"))
            assertFalse(denied)
            val p = Frame.read(ByteArrayInputStream(out.toByteArray()))
            assertEquals(Frame.UNLOCK_RESPONSE, p.id)
            assertEquals("WAITING", JSONObject(String(p.payload)).getString("error"))
            // Só uma resposta vale.
            assertFalse(s.approve("x"))
        } finally {
            UnlockSession.onDenied = old
        }
    }

    @Test fun normalRequestIsNotAutomatic() {
        val s = UnlockSession(pc = pc, user = "u", program = "", via = "rede local", unlockToken = "t",
            output = ByteArrayOutputStream(), closeable = Closeable { })
        assertFalse(s.automatic)
    }
}
