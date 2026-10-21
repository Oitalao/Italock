package br.italo.italock

import br.italo.italock.crypto.PcbuCrypto
import br.italo.italock.data.PairedPc
import br.italo.italock.data.PairingStore
import br.italo.italock.lock.LockClient
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.lock.WakeOnLan
import br.italo.italock.net.NetInfo
import br.italo.italock.service.WatchControl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetAndRulesTest {
    private val pc = PairedPc(
        deviceId = "id1", encKey = PcbuCrypto.randomString(64), passwordKeyEnc = "", passwordKeyIv = "", pcName = "PC", pcOs = "",
        userName = "", pairingMethod = "UDP", pcIp = "192.168.0.10", pcIps = listOf("100.100.1.2", "192.168.0.10", "203.0.113.5"),
        unlockServerPort = 43296, macAddresses = emptyList(),
    )

    @Test fun addressClasses() {
        assertTrue(NetInfo.isTailscale("100.64.0.1"))
        assertTrue(NetInfo.isTailscale("100.127.255.255"))
        assertFalse(NetInfo.isTailscale("100.128.0.1"))
        assertFalse(NetInfo.isTailscale("100.63.0.1"))
        assertTrue(NetInfo.isLocal("192.168.1.1"))
        assertTrue(NetInfo.isLocal("172.16.0.1"))
        assertFalse(NetInfo.isLocal("172.32.0.1"))
        assertTrue(NetInfo.isLocal("169.254.3.4"))
        assertFalse(NetInfo.isLocal("8.8.8.8"))
        assertFalse(NetInfo.isLocal("192.168.1"))
        assertFalse(NetInfo.isLocal("192.168.1.300"))
    }

    @Test fun viaRules() {
        assertEquals(NetInfo.VIA_LAN, NetInfo.viaOf("192.168.0.10", "192.168.0.20", localIsVpn = false))
        assertEquals(NetInfo.VIA_TAILSCALE, NetInfo.viaOf("100.100.1.2", "100.90.0.1", localIsVpn = true))
        // IP privado que chegou pela VPN (rota de sub rede do Tailscale): NÃO é perto.
        assertEquals(NetInfo.VIA_TAILSCALE, NetInfo.viaOf("192.168.0.10", "100.90.0.1", localIsVpn = true))
        assertEquals(NetInfo.VIA_INTERNET, NetInfo.viaOf("192.168.0.10", "10.8.0.2", localIsVpn = true))
        assertEquals(NetInfo.VIA_INTERNET, NetInfo.viaOf("203.0.113.5", "192.168.0.20", localIsVpn = false))
    }

    @Test fun viaLabelNeverShowsDashes() {
        assertEquals("rede local", NetInfo.viaLabel("192.168.0.10"))
        assertEquals("Tailscale", NetInfo.viaLabel("100.100.1.2"))
        assertEquals("internet", NetInfo.viaLabel("203.0.113.5"))
        assertEquals("Bluetooth", NetInfo.viaLabel("Bluetooth"))
        assertEquals("rede local", NetInfo.viaLabel("rede local"))
    }

    @Test fun allowedPathsFilterHostsAndRequests() {
        assertEquals(listOf("100.100.1.2", "192.168.0.10", "203.0.113.5"), pc.hosts)
        val noTs = pc.copy(allowTailscale = false)
        assertEquals(listOf("192.168.0.10"), noTs.hosts)
        assertFalse(noTs.allows(NetInfo.VIA_TAILSCALE))
        assertFalse(noTs.allows(NetInfo.VIA_INTERNET))
        assertTrue(noTs.allows(NetInfo.VIA_LAN))
        val noLan = pc.copy(allowLan = false, allowBluetooth = false)
        assertEquals(listOf("100.100.1.2", "203.0.113.5"), noLan.hosts)
        assertFalse(noLan.allows(NetInfo.VIA_BLUETOOTH))
        assertTrue(pc.copy(allowLan = false, allowTailscale = false).hosts.isEmpty())
    }

    @Test fun tileChoiceAllowsNone() {
        val other = pc.copy(deviceId = "id2")
        assertEquals("id1", PairingStore.tilePcOf(listOf(pc, other), "")?.deviceId)
        assertEquals("id2", PairingStore.tilePcOf(listOf(pc, other), "ID2")?.deviceId)
        assertNull(PairingStore.tilePcOf(listOf(pc), PairingStore.TILE_NONE))
    }

    @Test fun sealedResponseIsVerified() {
        val inner = JSONObject().put("error", "").put("locked", true).put("nonce", "abc")
        val raw = JSONObject().put("sealed", PcbuCrypto.toHex(PcbuCrypto.encryptPacket(inner.toString().toByteArray(), pc.encKey)))
        val (json, verified) = LockClient.openResponse(raw, pc.encKey, "abc", sealedSeen = false)
        assertTrue(verified)
        assertEquals(LockStatus.LOCKED, LockClient.interpretStatus(json, verified, "PC").status)
    }

    @Test fun sealedResponseWithoutNonceStillVerified() {
        val inner = JSONObject().put("error", "").put("locked", false)
        val raw = JSONObject().put("sealed", PcbuCrypto.toHex(PcbuCrypto.encryptPacket(inner.toString().toByteArray(), pc.encKey)))
        val (json, verified) = LockClient.openResponse(raw, pc.encKey, "abc", sealedSeen = true)
        assertTrue(verified)
        assertEquals(LockStatus.UNLOCKED, LockClient.interpretStatus(json, verified, "PC").status)
    }

    @Test fun forgedOrReplayedSealRejected() {
        val inner = JSONObject().put("error", "").put("locked", true).put("nonce", "outro")
        val wrongNonce = JSONObject().put("sealed", PcbuCrypto.toHex(PcbuCrypto.encryptPacket(inner.toString().toByteArray(), pc.encKey)))
        val (a, va) = LockClient.openResponse(wrongNonce, pc.encKey, "abc", sealedSeen = false)
        assertFalse(va)
        assertEquals(LockStatus.ERROR, LockClient.interpretStatus(a, va, "PC").status)
        val wrongKey = JSONObject().put("sealed", PcbuCrypto.toHex(PcbuCrypto.encryptPacket(inner.toString().toByteArray(), "outra chave")))
        assertEquals(LockClient.BAD_SEAL, LockClient.openResponse(wrongKey, pc.encKey, "outro", false).first.optString("error"))
        val garbage = JSONObject().put("sealed", "ZZ")
        assertEquals(LockClient.BAD_SEAL, LockClient.openResponse(garbage, pc.encKey, "abc", false).first.optString("error"))
    }

    @Test fun plainResponseFromOldAgentIsUnverifiedButShown() {
        val raw = JSONObject().put("error", "").put("locked", true)
        val (json, verified) = LockClient.openResponse(raw, pc.encKey, "abc", sealedSeen = false)
        assertFalse(verified)
        val s = LockClient.interpretStatus(json, verified, "PC")
        assertEquals(LockStatus.LOCKED, s.status)
        assertFalse(s.verified)
    }

    @Test fun plainResponseAfterSealedSeenIsRejected() {
        val raw = JSONObject().put("error", "").put("locked", false)
        val (json, verified) = LockClient.openResponse(raw, pc.encKey, "abc", sealedSeen = true)
        assertFalse(verified)
        assertEquals(LockClient.UNSEALED, json.optString("error"))
        assertEquals(LockStatus.ERROR, LockClient.interpretStatus(json, verified, "PC").status)
    }

    @Test fun agentErrorsAreNotNoAnswer() {
        fun st(err: String) = LockClient.interpretStatus(JSONObject().put("error", err), true, "PC").status
        assertEquals(LockStatus.CLOCK_OFF, st("TIME_ERROR"))
        assertEquals(LockStatus.NOT_PAIRED, st("NOT_PAIRED"))
        assertEquals(LockStatus.NOT_PAIRED, st("DATA_ERROR"))
        assertEquals(LockStatus.ERROR, st("STATE_UNKNOWN"))
        assertEquals(LockStatus.ERROR, st("BUSY"))
        assertEquals(LockStatus.ERROR, LockClient.interpretStatus(JSONObject().put("error", ""), true, "PC").status)
    }

    @Test fun errorMessagesHaveNoDashesOrRawCodes() {
        val codes = listOf("NOT_PAIRED", "TIME_ERROR", "DATA_ERROR", "BAD_ACTION", "BAD_PACKET", "STATE_UNKNOWN", "NOT_WAITING",
            "LOCK_FAILED", "REPLAY", "BUSY", LockClient.BAD_SEAL, LockClient.UNSEALED, "XYZ_NEW", LockClient.NO_PATH)
        for (c in codes) {
            val m = LockClient.describeError("Notebook", "lock", c, true)
            assertFalse("$c: $m", m.contains('—') || m.contains('–') || m.contains(" - "))
            assertFalse("$c: $m", m.contains(c) && c.contains('_'))
        }
        assertEquals("O computador não está na tela de bloqueio.", LockClient.describeError("PC", "wake", "NOT_WAITING", true))
    }

    @Test fun macParsing() {
        assertNotNull(WakeOnLan.parseMac("AA:BB:CC:DD:EE:FF"))
        assertNotNull(WakeOnLan.parseMac("aa-bb-cc-dd-ee-ff"))
        assertNull(WakeOnLan.parseMac("AA:BB:CC:DD:EE"))
        assertNull(WakeOnLan.parseMac("GG:BB:CC:DD:EE:FF"))
        assertNull(WakeOnLan.parseMac(""))
    }

    @Test fun zeppSourceCheck() {
        assertTrue(WatchControl.isWatchPackage("com.huami.watch.hmwatchmanager"))
        assertFalse(WatchControl.isWatchPackage("com.android.bluetooth"))
        assertFalse(WatchControl.isWatchPackage("com.spotify.music"))
        assertFalse(WatchControl.isWatchPackage(null))
    }

    @Test fun agentReplyOlderThanSixtySecondsIsRefused() {
        val old = java.nio.ByteBuffer.allocate(8).putLong(System.currentTimeMillis() - 90_000).array()
        val blob = PcbuCrypto.encrypt(old + JSONObject().put("error", "").put("nonce", "abc").toString().toByteArray(), pc.encKey)
        val r = LockClient.openResponse(JSONObject().put("sealed", PcbuCrypto.toHex(blob)), pc.encKey, "abc", sealedSeen = false)
        assertEquals(LockClient.BAD_SEAL, r.first.optString("error"))
        assertFalse(r.second)
    }

    @Test fun everyAgentErrorHasItsOwnMessage() {
        val generic = LockClient.describeError("PC", "x", "QUALQUER_OUTRO", true)
        for (code in listOf("NOT_LOGGED_IN", "LOCK_DISABLED", "PATH_DISABLED", "UNSUPPORTED", "NOT_ALLOWED", "LAUNCH_FAILED",
            "BAD_NONCE", "NOT_SEALED", "BAD_CMD")) {
            val m = LockClient.describeError("PC", "x", code, true)
            assertFalse(code, m == generic)
            assertFalse(code, m.contains('-'))
        }
    }
}
