package br.italo.italock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.italo.italock.ui.components.AmberBanner
import br.italo.italock.ui.components.InnerTopBar
import br.italo.italock.ui.components.Panel
import br.italo.italock.ui.components.SwitchRow
import br.italo.italock.ui.components.ThinDivider
import br.italo.italock.ui.theme.ItalockIcons
import br.italo.italock.R
import androidx.compose.ui.res.stringResource

@Composable
fun ExperimentalScreen(
    flags: ExperimentalFlags,
    wearAvailable: Boolean,
    onBack: () -> Unit,
    onFeature: (ExpFeature, Boolean) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar(stringResource(R.string.exp_title), onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Faixa fixa: fica fora da rolagem.
            AmberBanner(stringResource(R.string.exp_banner))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
                Panel {
                    SwitchRow(
                        stringResource(R.string.exp_nearby),
                        stringResource(R.string.exp_nearby_sub),
                        flags.nearby, { onFeature(ExpFeature.NEARBY, it) }, icon = ItalockIcons.Bluetooth,
                    )
                    ThinDivider()
                    SwitchRow(
                        stringResource(R.string.exp_notification),
                        stringResource(R.string.exp_notification_sub),
                        flags.notification, { onFeature(ExpFeature.NOTIFICATION, it) }, icon = ItalockIcons.Notify,
                    )
                    ThinDivider()
                    SwitchRow(
                        stringResource(R.string.exp_watch),
                        stringResource(R.string.exp_watch_sub),
                        flags.watch, { onFeature(ExpFeature.WATCH, it) }, icon = ItalockIcons.LockClock,
                    )
                    if (wearAvailable) {
                        ThinDivider()
                        SwitchRow(
                            stringResource(R.string.exp_wear),
                            stringResource(R.string.exp_wear_sub),
                            flags.wear, { onFeature(ExpFeature.WEAR, it) }, icon = ItalockIcons.LockClock,
                        )
                    }
                }
                Text(
                    stringResource(R.string.exp_footer),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
