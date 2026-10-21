package br.italo.italock

import br.italo.italock.data.PairedPc
import br.italo.italock.service.PreApproval
import br.italo.italock.service.UnlockSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable

class PreApprovalTest {
    private var now = 1_000_000L
    private val pc = PairedPc(
        deviceId = "AAAA-1", encKey = "k", passwordKeyEnc = "", passwordKeyIv = "", pcName = "Notebook", pcOs = "Windows",
        userName = "u", pairingMethod = "UDP", pcIp = "192.168.0.2", unlockServerPort = 43296, macAddresses = emptyList(),
    )

    private fun session(receivedAt: Long) = UnlockSession(
        pc = pc, user = "u", program = "", via = "rede local", unlockToken = "t",
        output = ByteArrayOutputStream(), closeable = Closeable { }, receivedAt = receivedAt,
    )

    @Before fun setUp() { PreApproval.clock = { now }; PreApproval.revoke(pc.deviceId); UnlockSession.active.clear() }
    @After fun tearDown() { PreApproval.revoke(pc.deviceId); UnlockSession.active.clear() }

    @Test fun onlyFirstRequestAfterGrantTakesTheKey() {
        val token = PreApproval.grant(pc.deviceId, "senha")
        assertNotNull(token)
        now += 500
        assertEquals("senha", PreApproval.take(session(now))?.passwordKey)
        // Gastou a vez: o segundo pedido não leva nada.
        assertNull(PreApproval.take(session(now + 10))?.passwordKey)
    }

    @Test fun originTravelsWithTheGrant() {
        PreApproval.grant(pc.deviceId, "senha", br.italo.italock.data.Origin.SHORTCUT)
        now += 100
        val g = PreApproval.take(session(now))
        assertEquals("senha", g?.passwordKey)
        assertEquals(br.italo.italock.data.Origin.SHORTCUT, g?.origin)
    }

    @Test fun requestReceivedBeforeGrantNeverTakesIt() {
        val before = session(now - 1)
        PreApproval.grant(pc.deviceId, "senha")
        assertNull(PreApproval.take(before)?.passwordKey)
        // E não gastou a vez de quem vier depois.
        assertEquals("senha", PreApproval.take(session(now + 1))?.passwordKey)
    }

    @Test fun expiresAfterWindowAndRenewsOnceOnWakeDelivered() {
        PreApproval.grant(pc.deviceId, "senha")
        now += PreApproval.WINDOW_MS - 100
        PreApproval.wakeDelivered(pc.deviceId) // recomeça o prazo
        now += PreApproval.WINDOW_MS - 100
        assertTrue(PreApproval.pending(pc.deviceId))
        PreApproval.wakeDelivered(pc.deviceId) // segunda vez não renova
        now += 200
        assertFalse(PreApproval.pending(pc.deviceId))
        assertNull(PreApproval.take(session(now))?.passwordKey)
    }

    @Test fun revokeWithOldTokenKeepsNewerGrant() {
        val first = PreApproval.grant(pc.deviceId, "senha1")!!
        now += 1
        val second = PreApproval.grant(pc.deviceId, "senha2")!!
        assertTrue(second > first)
        PreApproval.revoke(pc.deviceId, first) // resposta atrasada do primeiro toque
        assertTrue(PreApproval.pending(pc.deviceId))
        PreApproval.revoke(pc.deviceId, second)
        assertFalse(PreApproval.pending(pc.deviceId))
    }

    @Test fun sameMillisecondGrantsGetDistinctTokens() {
        val a = PreApproval.grant(pc.deviceId, "x")!!
        val b = PreApproval.grant(pc.deviceId, "y")!!
        assertTrue(b > a)
        PreApproval.revoke(pc.deviceId, a)
        assertTrue(PreApproval.pending(pc.deviceId))
    }

    @Test fun grantRefusedWhileComputerRequestIsOpen() {
        val open = session(now)
        UnlockSession.active[open.id] = open
        assertNull(PreApproval.grant(pc.deviceId, "senha"))
        assertFalse(PreApproval.pending(pc.deviceId))
        assertEquals(open, PreApproval.openSession("aaaa-1"))
    }
}
