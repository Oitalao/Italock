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

@Composable
fun ExperimentalScreen(
    flags: ExperimentalFlags,
    wearAvailable: Boolean,
    onBack: () -> Unit,
    onFeature: (ExpFeature, Boolean) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar("Recursos experimentais", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Faixa fixa: fica fora da rolagem.
            AmberBanner("Experimental. Pode não funcionar direito, mudar ou sumir em versões futuras. Use por sua conta.")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
                Panel {
                    SwitchRow(
                        "Destravar automático quando perto",
                        "Por Bluetooth ou na mesma rede, sem tocar no celular (nunca pelo Tailscale). " +
                            "Risco: alguém ao lado do computador destrava.",
                        flags.nearby, { onFeature(ExpFeature.NEARBY, it) }, icon = ItalockIcons.Bluetooth,
                    )
                    ThinDivider()
                    SwitchRow(
                        "Destravar pela notificação",
                        "Botão Destravar na notificação, sem biometria. " +
                            "Risco: quem pegar o celular desbloqueado destrava.",
                        flags.notification, { onFeature(ExpFeature.NOTIFICATION, it) }, icon = ItalockIcons.Notify,
                    )
                    ThinDivider()
                    SwitchRow(
                        "Relógio Amazfit (chamada falsa)",
                        "O pedido aparece no relógio como uma chamada: ignorar destrava, recusar nega. " +
                            "Fora do pulso, a ação é bloqueada. Precisa do Gadgetbridge Voz pareado ao relógio. " +
                            "Risco: quem estiver com seu relógio no pulso destrava.",
                        flags.watch, { onFeature(ExpFeature.WATCH, it) }, icon = ItalockIcons.LockClock,
                    )
                    if (wearAvailable) {
                        ThinDivider()
                        SwitchRow(
                            "Relógio Wear OS",
                            "O pedido aparece no relógio (Galaxy Watch, Pixel Watch e outros) com Destravar e Recusar; o tile trava, " +
                                "destrava e liga. Precisa do app Ítalock no relógio e do bloqueio de tela ligado nele. " +
                                "Longe do celular, passa pela internet do Google. Risco: quem estiver com seu relógio desbloqueado destrava.",
                            flags.wear, { onFeature(ExpFeature.WEAR, it) }, icon = ItalockIcons.LockClock,
                        )
                    }
                }
                Text(
                    "Depois de ligar, escolha os computadores nos detalhes de cada um. Desligar aqui desliga em todos.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
