package br.italo.italock.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** Cores do tema escuro (docs/design.md). O relógio é sempre escuro. */
object Ink {
    val bg = Color(0xFF0E1320)
    val surface = Color(0xFF171D2E)
    val text = Color(0xFFE6E8EE)
    val text2 = Color(0xFF9AA3B5)
    val orange = Color(0xFFEC7000)
    val onOrange = Color(0xFF1A1206)
    val blue = Color(0xFF7F9CF5)
    val danger = Color(0xFFC62828)
}

private val colors = Colors(
    primary = Ink.orange,
    primaryVariant = Ink.orange,
    onPrimary = Ink.onOrange,
    secondary = Ink.blue,
    secondaryVariant = Ink.blue,
    onSecondary = Ink.bg,
    background = Ink.bg,
    onBackground = Ink.text,
    surface = Ink.surface,
    onSurface = Ink.text,
    onSurfaceVariant = Ink.text2,
    error = Ink.danger,
    onError = Ink.text,
)

@Composable
fun ItalockTheme(content: @Composable () -> Unit) {
    // Direção do texto pelo idioma escolhido no celular (árabe da direita para a esquerda).
    val tag = WearLang.tag(LocalContext.current)
    val locale = if (tag == WearLang.SYSTEM) java.util.Locale.getDefault() else java.util.Locale.forLanguageTag(tag)
    val rtl = android.text.TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colors = colors) {
            Box(Modifier.fillMaxSize().background(Ink.bg)) { content() }
        }
    }
}

/** Botão largo. primary=true só na ação principal (laranja). */
@Composable
fun WideButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Chip(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        colors = if (primary) {
            ChipDefaults.primaryChipColors(backgroundColor = Ink.orange, contentColor = Ink.onOrange)
        } else {
            ChipDefaults.secondaryChipColors(backgroundColor = Ink.surface, contentColor = Ink.text)
        },
    )
}

@Composable
fun CenterText(text: String, modifier: Modifier = Modifier, secondary: Boolean = false) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = if (secondary) Ink.text2 else Ink.text,
        style = if (secondary) MaterialTheme.typography.body2 else MaterialTheme.typography.title3,
    )
}
