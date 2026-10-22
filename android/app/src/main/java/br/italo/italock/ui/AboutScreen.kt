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
import br.italo.italock.R
import androidx.compose.ui.res.stringResource

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
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { InnerTopBar(stringResource(R.string.about_title), a.onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle(stringResource(R.string.about_section_credits))
            Panel {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                    Text(stringResource(R.string.about_based_on), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(if (SOURCE_URL.isEmpty()) R.string.about_source_soon else R.string.about_source),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row {
                        QuietButton(stringResource(R.string.about_license), { a.onLink(LICENSE_URL) })
                        QuietButton(stringResource(R.string.about_original), { a.onLink(ORIGINAL_URL) })
                        if (SOURCE_URL.isNotEmpty()) QuietButton(stringResource(R.string.about_code), { a.onLink(SOURCE_URL) })
                    }
                }
            }

            SectionTitle(stringResource(R.string.about_section_diag))
            Panel {
                SettingRow(stringResource(R.string.about_export), subtitle = stringResource(R.string.about_export_sub),
                    onClick = a.onExport)
            }

            Spacer16()
            val techState = stringResource(if (technical) R.string.common_expanded else R.string.common_collapsed)
            Panel {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = stringResource(if (technical) R.string.common_collapse else R.string.common_show)) { technical = !technical }
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp)
                        .semantics { stateDescription = techState },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.common_technical_details), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
