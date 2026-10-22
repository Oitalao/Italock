package br.italo.italock.ui.components

import androidx.compose.ui.res.stringResource
import br.italo.italock.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.italo.italock.lock.LockClient.LockStatus
import br.italo.italock.ui.theme.ButtonShape
import br.italo.italock.ui.theme.Italock
import br.italo.italock.ui.theme.ItalockIcons

/** Barra superior das telas internas: voltar + título. Mesma cor do fundo (sem faixa colorida, sem sombra). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterTitleBar(title: String) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** A ação principal da tela (laranja). Uma por tela. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Italock.colors.action, contentColor = Italock.colors.onAction),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

/** Ação comum (azul, contorno fino). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

/** Botão de texto; vermelho quando é perigo (remover). */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
    TextButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = ButtonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) Italock.colors.dangerText else MaterialTheme.colorScheme.primary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

/** Título de seção: texto pequeno, forte, na cor da marca, acima do painel. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(br.italo.italock.i18n.L10n.currentLocale()), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp),
    )
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    HorizontalDivider(modifier.padding(start = if (inset) 16.dp else 0.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Ícone da linha: quadradinho arredondado com fundo tingido (azul da marca; vermelho quando é perigo). */
@Composable
fun RowIcon(icon: ImageVector, modifier: Modifier = Modifier, danger: Boolean = false, alpha: Float = 1f) {
    val tint = if (danger) Italock.colors.dangerText else MaterialTheme.colorScheme.primary
    Box(
        modifier.size(36.dp).background(tint.copy(alpha = 0.14f * alpha), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(20.dp)) }
}

/** Título + explicação de uma linha de ajuste. */
@Composable
private fun RowTexts(title: String, subtitle: String?, titleColor: Color, subColor: Color, bold: Boolean = false) {
    Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subColor,
        modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun Chevron() =
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * Linha de ajuste: ícone opcional, título, explicação e algo à direita.
 * Sem [trailing] e com [onClick], mostra a seta ">" (deixa claro que abre uma escolha).
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { RowIcon(icon); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            RowTexts(title, subtitle, if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor,
                MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        when {
            trailing != null -> trailing()
            onClick != null -> Chevron()
        }
    }
}

/**
 * Linha com interruptor: a linha inteira liga/desliga (alvo grande).
 * [dependent]: opção que só vale com a de cima ligada; fica recuada, ligada à de cima por um fio.
 * Indisponível ([enabled] false): tudo bem apagado, cadeado no lugar do interruptor e [disabledReason] numa etiqueta;
 * tocar explica o que falta em vez de não fazer nada.
 */
@Composable
fun SwitchRow(
    title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, danger: Boolean = false, enabled: Boolean = true,
    dependent: Boolean = false, icon: ImageVector? = null, disabledReason: String? = null,
) {
    val c = Italock.colors
    val context = LocalContext.current
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val unavailableDescription = stringResource(R.string.a11y_unavailable, title, disabledReason ?: "").trim()
    val titleColor = (if (danger) c.dangerText else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha)
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Switch, onClickLabel = if (enabled) null else stringResource(R.string.a11y_why_unavailable)) {
                if (enabled) onChange(!checked)
                else if (disabledReason != null) Toast.makeText(context, disabledReason, Toast.LENGTH_LONG).show()
            }
            .semantics { if (!enabled) contentDescription = unavailableDescription }
            .heightIn(min = 60.dp)
            // Fio embaixo do centro do ícone da opção de cima; o texto alinha com o texto dela.
            .padding(start = if (dependent) 33.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dependent) Box(
            Modifier.padding(vertical = 8.dp).width(2.dp).heightIn(min = 40.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.8f else 0.4f), MaterialTheme.shapes.extraSmall),
        )
        Row(Modifier.weight(1f).padding(start = if (dependent) 31.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { RowIcon(icon, danger = danger, alpha = if (enabled) 1f else 0.45f); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                RowTexts(title, if (enabled || disabledReason == null) subtitle else null, titleColor, subColor, bold = danger)
                if (!enabled && disabledReason != null) Row(
                    Modifier.padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(disabledReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            if (enabled) Switch(
                checked = checked, onCheckedChange = null,
                colors = if (danger) SwitchDefaults.colors(checkedTrackColor = c.danger, checkedThumbColor = Color.White)
                else SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
            ) else Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                // Sem interruptor: um interruptor apagado parece só "desligado"; o cadeado diz "não dá para mexer agora".
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Opacidade de item desativado (a mesma do Material). */
const val DISABLED_ALPHA = 0.38f

/** Linha que abre outra tela: ícone opcional, título, explicação, etiqueta opcional e a seta ">". */
@Composable
fun NavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, badge: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { RowIcon(icon); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            RowTexts(title, subtitle, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) { Spacer(Modifier.width(8.dp)); badge() }
        Spacer(Modifier.width(8.dp))
        Chevron()
    }
}

/** Etiqueta pequena (ex.: "BETA", "2 ligados"). */
@Composable
fun Tag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = fg,
        modifier = modifier.background(bg, MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Superfície (clara suave ou escura) com cantos de 12 dp, sem sombra: agrupa linhas de uma lista. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.shapes.large),
    ) { content() }
}

/**
 * Aviso de algo que falta (permissão): texto de uma linha ou duas e um botão que abre o ajuste certo.
 * Some sozinho quando o problema é resolvido.
 */
@Composable
fun FixRow(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Italock.colors.action, MaterialTheme.shapes.small))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        QuietButton(action, onClick)
    }
}

/** Faixa fixa da tela experimental. */
@Composable
fun AmberBanner(text: String, modifier: Modifier = Modifier) {
    val c = Italock.colors
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = c.amberText,
        modifier = modifier.fillMaxWidth().background(c.amberBg).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Faixa vermelha do modo sem confirmação. */
@Composable
fun DangerStrip(text: String = stringResource(R.string.danger_strip), modifier: Modifier = Modifier) {
    val c = Italock.colors
    Text(
        text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.onDanger,
        modifier = modifier.background(c.danger, MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Ícone + texto do estado do computador. null = ainda consultando. */
data class StatusLook(val icon: ImageVector, val tint: Color, val label: String, val textColor: Color)

@Composable
fun statusLook(status: LockStatus?): StatusLook {
    val c = Italock.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (status) {
        LockStatus.LOCKED -> StatusLook(Icons.Outlined.Lock, c.lockedIcon, stringResource(R.string.status_locked), MaterialTheme.colorScheme.onSurface)
        LockStatus.UNLOCKED -> StatusLook(ItalockIcons.LockOpen, c.unlockedIcon, stringResource(R.string.status_unlocked), c.unlockedText)
        LockStatus.NO_ANSWER -> StatusLook(ItalockIcons.NoAnswer, c.muted, stringResource(R.string.status_no_answer), muted)
        LockStatus.CLOCK_OFF -> StatusLook(ItalockIcons.Clock, c.dangerText, stringResource(R.string.status_clock_off), c.dangerText)
        LockStatus.NOT_PAIRED -> StatusLook(ItalockIcons.NoAnswer, c.dangerText, stringResource(R.string.status_not_paired), c.dangerText)
        LockStatus.ERROR -> StatusLook(ItalockIcons.NoAnswer, c.dangerText, stringResource(R.string.status_error), c.dangerText)
        null -> StatusLook(Icons.Outlined.Lock, c.muted.copy(alpha = 0.5f), stringResource(R.string.status_querying), muted)
    }
}

@Composable
fun StatusIcon(status: LockStatus?, modifier: Modifier = Modifier, size: Int = 24) {
    val look = statusLook(status)
    Icon(look.icon, contentDescription = null, tint = look.tint, modifier = modifier.size(size.dp).semantics { contentDescription = look.label })
}

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.padding(top = dp.dp))

@Composable
fun Steps(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
