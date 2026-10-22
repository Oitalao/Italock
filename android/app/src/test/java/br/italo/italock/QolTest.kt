package br.italo.italock

import br.italo.italock.data.EventKind
import br.italo.italock.data.HistoryEvent
import br.italo.italock.data.HistoryStore
import br.italo.italock.data.Origin
import br.italo.italock.data.PairedPc
import br.italo.italock.data.TagRule
import br.italo.italock.data.TagRule.Step
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.net.Frame
import br.italo.italock.ui.AppShortcuts
import br.italo.italock.ui.QuickUnlockActivity
import br.italo.italock.ui.lockAllSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class QolTest {
    /** Textos vêm dos XML de res/values (português, o padrão). */
    @Before fun strings() = TestStrings.install()

    private fun pc(id: String, name: String) = PairedPc(
        deviceId = id, encKey = "k", passwordKeyEnc = "", passwordKeyIv = "", pcName = name, pcOs = "", userName = "",
        pairingMethod = "UDP", pcIp = "192.168.0.2", unlockServerPort = 43296, macAddresses = emptyList(),
    )

    // ------------------------------------------------------------ tag NFC: regra

    @Test fun tagBothOnTogglesByLockState() {
        assertEquals(Step.ASK_STATE, TagRule.first(lock = true, unlock = true))
        assertEquals(Step.UNLOCK, TagRule.afterState(true, true, LockStatus.LOCKED))
        assertEquals(Step.LOCK, TagRule.afterState(true, true, LockStatus.UNLOCKED))
        // Sem resposta, relógio fora de hora, erro: não faz nada (a tela explica).
        assertEquals(Step.NOTHING, TagRule.afterState(true, true, LockStatus.NO_ANSWER))
        assertEquals(Step.NOTHING, TagRule.afterState(true, true, LockStatus.CLOCK_OFF))
        assertEquals(Step.NOTHING, TagRule.afterState(true, true, null))
    }

    @Test fun tagOnlyOneDoesOnlyThat() {
        assertEquals(Step.UNLOCK, TagRule.first(lock = false, unlock = true))
        assertEquals(Step.LOCK, TagRule.first(lock = true, unlock = false))
        // Mesmo que o cadeado diga o contrário, só um ligado nunca faz a outra coisa.
        assertEquals(Step.NOTHING, TagRule.afterState(lock = true, unlock = false, status = LockStatus.LOCKED))
        assertEquals(Step.NOTHING, TagRule.afterState(lock = false, unlock = true, status = LockStatus.UNLOCKED))
    }

    @Test fun tagNoneDoesNothing() {
        assertEquals(Step.NOTHING, TagRule.first(lock = false, unlock = false))
        assertEquals(Step.NOTHING, TagRule.afterState(false, false, LockStatus.LOCKED))
        assertEquals("Desligada", TagRule.summary(false, false))
    }

    // ------------------------------------------------------------ tag NFC: migração

    @Test fun tagMigratesOldSetting() {
        assertEquals(true to true, TagRule.read(JSONObject().put("tagAction", "toggle")))
        assertEquals(false to true, TagRule.read(JSONObject().put("tagAction", "unlock")))
        assertEquals(true to false, TagRule.read(JSONObject().put("tagAction", "lock")))
        // Pareamento de antes da opção existir: era "alternar".
        assertEquals(true to true, TagRule.read(JSONObject()))
        assertEquals(true to true, TagRule.read(JSONObject().put("tagAction", "desconhecido")))
    }

    @Test fun tagNewSettingWinsOverOld() {
        val o = JSONObject().put("tagAction", "toggle").put("tagLock", false).put("tagUnlock", false)
        assertEquals(false to false, TagRule.read(o))
        assertEquals(true to false, TagRule.read(JSONObject().put("tagLock", true).put("tagUnlock", false)))
    }

    @Test fun tagSurvivesSaveAndLoad() {
        // Gravado no formato novo (sem tagAction) e relido igual.
        val saved = JSONObject().put("tagLock", false).put("tagUnlock", true)
        val copy = pc("a", "A").copy(tagLock = false, tagUnlock = true)
        assertEquals(copy.tagLock to copy.tagUnlock, TagRule.read(saved))
        assertEquals("Só destrava", TagRule.summary(copy.tagLock, copy.tagUnlock))
    }

    // ------------------------------------------------------------ histórico

    @Test fun historyKeepsNewestFirstAndAtMostTwenty() {
        var list = emptyList<HistoryEvent>()
        for (i in 1..25) list = HistoryStore.append(list, HistoryEvent(i.toLong(), EventKind.LOCKED, Origin.APP, "rede local"))
        assertEquals(HistoryStore.MAX, list.size)
        assertEquals(25L, list.first().at)
        assertEquals(6L, list.last().at)
    }

    @Test fun historyRoundTripAndIgnoresGarbage() {
        val list = listOf(
            HistoryEvent(2, EventKind.UNLOCKED, Origin.SHORTCUT, "Tailscale"),
            HistoryEvent(1, EventKind.EXPIRED, Origin.PC, "Bluetooth"),
        )
        assertEquals(list, HistoryStore.decode(HistoryStore.encode(list)))
        assertEquals(emptyList<HistoryEvent>(), HistoryStore.decode("isto não é json"))
        assertEquals(emptyList<HistoryEvent>(), HistoryStore.decode(null))
        // Evento de uma versão futura (tipo desconhecido) é pulado; o resto fica.
        val mixed = "[{\"at\":5,\"k\":\"novo\",\"o\":\"app\",\"v\":\"\"},{\"at\":4,\"k\":\"denied\",\"o\":\"watch\",\"v\":\"rede local\"}]"
        assertEquals(listOf(HistoryEvent(4, EventKind.DENIED, Origin.WATCH, "rede local")), HistoryStore.decode(mixed))
    }

    @Test fun originsAndLabelsHaveNoDashes() {
        (Origin.entries.map { it.label } + EventKind.entries.map { it.label }).forEach {
            assertFalse("traço em \"$it\"", it.contains('-') || it.contains('–') || it.contains('—'))
        }
        assertTrue(Origin.SHORTCUT.fromPhone)
        assertFalse(Origin.PC.fromPhone)
        assertFalse(Origin.AUTO.fromPhone)
    }

    // ------------------------------------------------------------ atalhos do ícone

    @Test fun shortcutsCoverFirstComputersUpToFour() {
        val three = listOf(pc("A1", "Notebook"), pc("B2", "Servidor"), pc("C3", "Sala"))
        val plan = AppShortcuts.plan(three)
        assertEquals(4, plan.size)
        assertEquals(listOf("Destravar Notebook", "Travar Notebook", "Destravar Servidor", "Travar Servidor"), plan.map { it.label })
        assertEquals(QuickUnlockActivity.ACTION_UNLOCK, plan[0].action)
        assertEquals(QuickUnlockActivity.ACTION_LOCK, plan[1].action)
        // Ids estáveis por computador e ação (renomear não cria atalho novo).
        assertEquals(plan[0].id, AppShortcuts.plan(listOf(pc("a1", "Outro nome")))[0].id)
        assertEquals(2, AppShortcuts.plan(three.take(1)).size)
        assertEquals(0, AppShortcuts.plan(emptyList()).size)
        assertEquals(3, AppShortcuts.plan(three, max = 3).size)
    }

    @Test fun shortcutUsesNickname() {
        assertEquals("Travar Mesa", AppShortcuts.plan(listOf(pc("x", "DESKTOP-123").copy(nickname = "Mesa")))[1].label)
    }

    // ------------------------------------------------------------ travar todos

    @Test fun lockAllSummaryTexts() {
        assertEquals("Todos os 3 travados", lockAllSummary(3, emptyList()))
        assertEquals("2 de 3 travados. Não travou: Sala", lockAllSummary(3, listOf("Sala")))
        assertEquals("1 de 3 travados. Não travaram: Sala, Servidor", lockAllSummary(3, listOf("Sala", "Servidor")))
        assertEquals("Nenhum travou: A, B", lockAllSummary(2, listOf("A", "B")))
    }

    // ------------------------------------------------------------ frame: limite de lixo

    @Test fun frameToleratesSomeJunkButNotTooMuch() {
        val frame = ByteArrayOutputStream().also { Frame.write(it, Frame.UNLOCK_REQUEST, "{}".toByteArray()) }.toByteArray()
        val ok = Frame.read(ByteArrayInputStream(ByteArray(1000) { 0x11.toByte() } + frame))
        assertEquals(Frame.UNLOCK_REQUEST, ok.id)
        try {
            Frame.read(ByteArrayInputStream(ByteArray(Frame.MAX_JUNK + 100) { 0x11.toByte() } + frame))
            fail("devia recusar lixo demais")
        } catch (_: Frame.TooMuchJunk) {
        }
    }
}
