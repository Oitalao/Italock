package br.italo.italock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.italo.italock.ui.components.InnerTopBar
import br.italo.italock.ui.components.Panel
import br.italo.italock.ui.components.QuietButton
import br.italo.italock.ui.components.SectionTitle
import br.italo.italock.ui.components.SettingRow
import br.italo.italock.ui.components.ThinDivider
import br.italo.italock.ui.theme.Mono

/** Linhas de "Detalhes técnicos" do Sobre (versão, ids). */
data class TechInfo(val lines: List<Pair<String, String>>)

class AboutActions(
    val onBack: () -> Unit,
    val onLink: (String) -> Unit,
    val onExport: () -> Unit,
)

@Composable
fun AboutScreen(tech: TechInfo, a: AboutActions) {
    var technical by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar("Sobre", a.onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("Créditos")
            Panel {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                    Text("Baseado no PC Bio Unlock (hoje PulseUnlock), de MeisApps, licença GPL versão 3.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (SOURCE_URL.isEmpty()) "Código do programa: em breve no GitHub." else "Código do programa: GitHub.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row {
                        QuietButton("Licença", { a.onLink(LICENSE_URL) })
                        QuietButton("Projeto original", { a.onLink(ORIGINAL_URL) })
                        if (SOURCE_URL.isNotEmpty()) QuietButton("Código", { a.onLink(SOURCE_URL) })
                    }
                }
            }

            SectionTitle("Diagnóstico")
            Panel {
                SettingRow("Exportar registros", subtitle = "Arquivo de texto para enviar a quem for ajudar. Sem chaves nem senhas.",
                    onClick = a.onExport)
            }

            Spacer16()
            Panel {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = if (technical) "Recolher" else "Mostrar") { technical = !technical }
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp)
                        .semantics { stateDescription = if (technical) "aberto" else "fechado" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Detalhes técnicos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(if (technical) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (technical) {
                    ThinDivider()
                    Column(Modifier.padding(16.dp)) {
                        tech.lines.forEach { (label, value) ->
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = Mono, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer16() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))

const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
const val ORIGINAL_URL = "https://github.com/MeisApps/pcbu-desktop"
/** Endereço do código do Ítalock (vazio até ir para o GitHub): com ele vazio, o Sobre diz "em breve no GitHub". */
const val SOURCE_URL = "https://github.com/Oitalao/Italock"
