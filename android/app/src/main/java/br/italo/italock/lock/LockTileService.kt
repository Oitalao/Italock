package br.italo.italock.lock

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import br.italo.italock.R
import br.italo.italock.data.PairingStore
import br.italo.italock.i18n.L10n
import br.italo.italock.ui.QuickUnlockActivity
import kotlin.concurrent.thread

/**
 * Atalho na barra rápida (Android 7+) para o PC escolhido no app (padrão: o primeiro da lista), conforme o cadeado:
 * travado → toque destrava (com o celular destravado e biometria, salvo o modo perigoso); destravado → toque trava.
 * Toque longo abre as Configurações do app na escolha do PC do atalho. Só consulta o PC quando a barra rápida é aberta (nada em segundo plano).
 */
@RequiresApi(24)
class LockTileService : TileService() {
    @Volatile private var locked: Boolean? = null
    /** O estado atual veio cifrado do agente (verificado)? */
    @Volatile private var verified = false
    /** Pedido em andamento (consulta ou travar): toques são ignorados até a resposta. */
    @Volatile private var busy = false

    override fun onStartListening() {
        locked = null
        verified = false
        val pc = PairingStore(this).tilePc()
        if (pc == null) {
            val none = PairingStore(this).tileDeviceId == PairingStore.TILE_NONE
            show(Tile.STATE_UNAVAILABLE, "Ítalock", L10n.s(if (none) R.string.tile_no_pc else R.string.tile_not_paired), R.drawable.ic_lock)
            return
        }
        val name = if (br.italo.italock.data.AppPrefs(this).hideNameNow(this)) "Ítalock" else pc.displayName
        show(Tile.STATE_INACTIVE, name, L10n.s(R.string.tile_querying), R.drawable.ic_lock)
        busy = true
        thread(isDaemon = true) {
            val r = runCatching { LockClient.status(pc) }.getOrNull()
            verified = r?.verified == true
            locked = when (r?.status) {
                LockClient.LockStatus.LOCKED -> true
                LockClient.LockStatus.UNLOCKED -> false
                else -> null
            }
            busy = false
            val unverified = r != null && !r.verified && locked != null
            when (locked) {
                true -> show(Tile.STATE_INACTIVE, name,
                    L10n.s(if (unverified) R.string.tile_locked_unverified else R.string.tile_locked), R.drawable.ic_lock)
                false -> show(Tile.STATE_ACTIVE, name,
                    L10n.s(if (unverified) R.string.tile_unlocked_unverified else R.string.tile_unlocked), R.drawable.ic_lock_open)
                null -> show(Tile.STATE_INACTIVE, name, L10n.s(R.string.tile_no_answer), R.drawable.ic_lock)
            }
        }
    }

    override fun onClick() {
        if (busy) return
        val pc = PairingStore(this).tilePc() ?: return
        if (locked == true) {
            // Destravar abre uma tela transparente que exige o celular destravado e confirma.
            val unverified = !verified
            val open = { openQuick(pc.deviceId, QuickUnlockActivity.ACTION_UNLOCK, unverified) }
            if (isLocked) unlockAndRun { open() } else open()
            return
        }
        if (br.italo.italock.data.AppPrefs(this).confirmLock) {
            // "Pedir confirmação antes de travar": a pergunta aparece na tela transparente.
            val open = { openQuick(pc.deviceId, QuickUnlockActivity.ACTION_LOCK, false) }
            if (isLocked) unlockAndRun { open() } else open()
            return
        }
        busy = true
        show(Tile.STATE_INACTIVE, pc.displayName, L10n.s(R.string.tile_locking), R.drawable.ic_lock)
        thread {
            val r = LockClient.lockOutcome(pc)
            if (r.ok) {
                br.italo.italock.data.Events.record(this, pc.deviceId, br.italo.italock.data.EventKind.LOCKED, br.italo.italock.data.Origin.TILE, r.via)
                br.italo.italock.service.UnlockedNotice.cancel(this, pc.deviceId)
                br.italo.italock.widget.WidgetStatus.refreshLater(this, pc.deviceId, 1_500)
                locked = true
                show(Tile.STATE_INACTIVE, pc.displayName, L10n.s(R.string.tile_locked), R.drawable.ic_lock)
            } else {
                show(Tile.STATE_INACTIVE, pc.displayName, L10n.s(R.string.tile_failed), R.drawable.ic_lock)
            }
            busy = false
            br.italo.italock.ui.Haptics.done(this, r.ok)
            val msg = r.message(pc)
            android.os.Handler(mainLooper).post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Android 7–13 só têm a versão com Intent
    private fun openQuick(deviceId: String, action: String, unverified: Boolean) {
        val intent = Intent(this, QuickUnlockActivity::class.java)
            .putExtra(QuickUnlockActivity.EXTRA_DEVICE, deviceId)
            .putExtra(QuickUnlockActivity.EXTRA_ACTION, action)
            .putExtra(QuickUnlockActivity.EXTRA_UNVERIFIED, unverified)
            .putExtra(QuickUnlockActivity.EXTRA_ORIGIN, br.italo.italock.data.Origin.TILE.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }

    private fun show(state: Int, label: String, subtitle: String, icon: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = label
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
        else tile.contentDescription = "$label, $subtitle"
        tile.icon = Icon.createWithResource(this, icon)
        tile.updateTile()
    }

}

/** Pede ao sistema para redesenhar o atalho (ex.: depois de escolher outro PC). Seguro em qualquer versão. */
object TileRefresher {
    fun refresh(context: Context) {
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { TileService.requestListeningState(context, ComponentName(context, LockTileService::class.java)) }
        }
    }
}
