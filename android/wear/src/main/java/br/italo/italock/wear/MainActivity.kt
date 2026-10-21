package br.italo.italock.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Lista dos computadores com o botão certo para cada um, igual ao tile. */
class MainActivity : ComponentActivity() {

    private var phoneMissing by mutableStateOf(false)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PcStore.load(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ItalockTheme {
                val pcs by PcStore.pcs.collectAsState()
                PcList(pcs.orEmpty(), phoneMissing) { pc -> openAction(this, pc) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val sent = Phone.send(this@MainActivity, Proto.PCS_GET, JSONObject())
            phoneMissing = !sent
        }
    }
}

fun openAction(ctx: Context, pc: Pc) {
    ctx.startActivity(
        Intent(ctx, ActionActivity::class.java)
            .putExtra(ActionActivity.EXTRA_ID, pc.id)
            .putExtra(ActionActivity.EXTRA_ACTION, pc.action),
    )
}

@Composable
private fun PcList(pcs: List<Pc>, phoneMissing: Boolean, onAction: (Pc) -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (phoneMissing) {
            item {
                CenterText(
                    "Celular não encontrado. Abra o Ítalock no celular e ligue Relógio Wear OS nos recursos experimentais.",
                    secondary = true,
                )
            }
        }
        if (pcs.isEmpty()) {
            if (!phoneMissing) item { CenterText("Abra o Ítalock no celular", secondary = true) }
        } else {
            items(pcs, key = { it.id }) { pc -> PcRow(pc, onAction) }
        }
    }
}

@Composable
private fun PcRow(pc: Pc, onAction: (Pc) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (pc.state == "unlocked") R.drawable.ic_lock_open else R.drawable.ic_lock),
                contentDescription = pc.stateLabel,
                tint = if (pc.state == "unlocked") Ink.orange else Ink.blue,
                modifier = Modifier.size(18.dp),
            )
            Text(
                pc.name,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.title3,
                color = Ink.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(pc.stateLabel, style = MaterialTheme.typography.caption1, color = Ink.text2)
        WideButton(pc.actionLabel, primary = pc.action == "unlock", onClick = { onAction(pc) })
    }
}
