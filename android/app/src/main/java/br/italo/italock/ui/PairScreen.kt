package br.italo.italock.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.italo.italock.ui.components.InnerTopBar
import br.italo.italock.ui.components.PrimaryButton
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.components.SecondaryButton
import br.italo.italock.ui.theme.Italock
import br.italo.italock.ui.theme.Mono

/** Andamento do pareamento: null = parado; texto = etapa atual. */
data class PairProgress(val running: Boolean = false, val message: String = "", val error: Boolean = false, val canRetrySave: Boolean = false)

@Composable
fun PairScreen(
    progress: PairProgress,
    bluetoothMissing: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onCode: (String) -> Unit,
    onAllowBluetooth: () -> Unit,
    onRetrySave: () -> Unit = {},
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar("Parear computador", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Step(1, "No computador", "Abra o Ítalock e escolha parear celular: aparece um QR code.")
            Step(2, "Mesma rede ou Bluetooth", "Os dois na mesma rede, ou ligados pelo cabo USB. Para Bluetooth, pareie antes o celular no computador.") {
                if (bluetoothMissing) QuietButton("Permitir Bluetooth", onAllowBluetooth, modifier = Modifier.padding(top = 4.dp))
            }
            Step(3, "Ler o QR code", "Aponte a câmera para o QR code ou digite o código mostrado.") {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Ler QR code", onScan, enabled = !progress.running, modifier = Modifier.fillMaxWidth())
                    if (!typing) {
                        QuietButton("Digitar o código", { typing = true }, enabled = !progress.running)
                    } else {
                        OutlinedTextField(
                            value = code, onValueChange = { code = it.take(400) },
                            label = { Text("Código do computador") }, singleLine = true,
                            textStyle = TextStyle(fontFamily = Mono),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SecondaryButton("Parear com o código", { onCode(code) }, enabled = code.isNotBlank() && !progress.running,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (progress.message.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progress.running) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(progress.message, style = MaterialTheme.typography.bodyMedium,
                        color = if (progress.error) Italock.colors.dangerText else MaterialTheme.colorScheme.onSurface)
                }
                // O computador já guardou o pareamento: dá para confirmar de novo sem ler outro QR code.
                if (progress.canRetrySave) SecondaryButton("Confirmar de novo", onRetrySave, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String, extra: @Composable () -> Unit = {}) {
    Row {
        Box(
            Modifier.size(28.dp).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            extra()
        }
    }
}
