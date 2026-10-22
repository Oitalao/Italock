package br.italo.italock.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import br.italo.italock.data.AppPrefs
import br.italo.italock.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.italo.italock.R
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Cores da marca (docs/design.md). Sempre as mesmas, em qualquer Android: sem Material You, a identidade é própria.
 * Azul = marca; laranja = UMA ação principal por tela e o estado "destravado"; vermelho só para perigo.
 */
object Brand {
    val Blue = Color(0xFF003399)
    val Orange = Color(0xFFEC7000)
    val Red = Color(0xFFC62828)
}

/** Cores que o Material não tem nome para (estado, perigo, faixa experimental). */
@Immutable
data class ItalockColors(
    /** Fundo do botão principal (laranja) e texto sobre ele (escuro: branco no laranja não passa no contraste AA). */
    val action: Color,
    val onAction: Color,
    /** "Destravado": ícone e texto (o texto é mais escuro no tema claro, para ler bem no branco). */
    val unlockedIcon: Color,
    val unlockedText: Color,
    val lockedIcon: Color,
    val muted: Color,
    val danger: Color,
    val dangerText: Color,
    val onDanger: Color,
    val amberBg: Color,
    val amberText: Color,
)

/** Claro SUAVE (docs/design.md): nada de branco puro; confortável com brilho máximo. */
private val LightExtra = ItalockColors(
    action = Color(0xFFD86A00), onAction = Color(0xFFFFF8F0),
    unlockedIcon = Color(0xFFB85600), unlockedText = Color(0xFFB85600),
    lockedIcon = Color(0xFF1F3F99), muted = Color(0xFF7A8292),
    danger = Color(0xFFB3261E), dangerText = Color(0xFFB3261E), onDanger = Color(0xFFFFF8F0),
    amberBg = Color(0xFFEBDDB8), amberText = Color(0xFF4A3300),
)

private val DarkExtra = ItalockColors(
    action = Brand.Orange, onAction = Color(0xFF1A1206),
    unlockedIcon = Color(0xFFFF9A3D), unlockedText = Color(0xFFFFA552),
    lockedIcon = Color(0xFF7F9CF5), muted = Color(0xFF6F7890),
    danger = Brand.Red, dangerText = Color(0xFFFF8A80), onDanger = Color.White,
    amberBg = Color(0xFF3A2C06), amberText = Color(0xFFFFD98A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F3F99), onPrimary = Color(0xFFF1F2F4),
    primaryContainer = Color(0xFFD5DCEE), onPrimaryContainer = Color(0xFF0F2466),
    secondary = Color(0xFFB85600), onSecondary = Color(0xFFFFF8F0),
    tertiary = Color(0xFFB85600), onTertiary = Color(0xFFFFF8F0),
    background = Color(0xFFE3E6EB), onBackground = Color(0xFF1D2330),
    surface = Color(0xFFECEEF1), onSurface = Color(0xFF1D2330),
    surfaceVariant = Color(0xFFE6E9ED), onSurfaceVariant = Color(0xFF4F5868),
    surfaceContainerLowest = Color(0xFFECEEF1), surfaceContainerLow = Color(0xFFECEEF1),
    surfaceContainer = Color(0xFFECEEF1), surfaceContainerHigh = Color(0xFFF1F2F4), surfaceContainerHighest = Color(0xFFF1F2F4),
    outline = Color(0xFF9AA1AD), outlineVariant = Color(0xFFCBD0D8),
    error = Color(0xFFB3261E), onError = Color(0xFFFFF8F0),
    inverseSurface = Color(0xFF1E2435), inverseOnSurface = Color(0xFFE6E8EE), inversePrimary = Color(0xFF7F9CF5),
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7F9CF5), onPrimary = Color(0xFF0E1320),
    primaryContainer = Color(0xFF22306B), onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Brand.Orange, onSecondary = Color(0xFF1A1206),
    tertiary = Brand.Orange, onTertiary = Color(0xFF1A1206),
    background = Color(0xFF0E1320), onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF171D2E), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1F2638), onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceContainerLowest = Color(0xFF0E1320), surfaceContainerLow = Color(0xFF171D2E),
    surfaceContainer = Color(0xFF171D2E), surfaceContainerHigh = Color(0xFF1C2336), surfaceContainerHighest = Color(0xFF232B40),
    outline = Color(0xFF444E68), outlineVariant = Color(0xFF262E44),
    error = Color(0xFFFF8A80), onError = Color(0xFF3B0000),
    inverseSurface = Color(0xFFE6E8EE), inverseOnSurface = Color(0xFF171D2E), inversePrimary = Brand.Blue,
    scrim = Color.Black,
)

/** Cantos: 12 dp nas superfícies, 10 dp nos botões. */
val ButtonShape = RoundedCornerShape(10.dp)
private val ItalockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Números, IPs e endereços de placa. */
val Mono = FontFamily.Monospace

/**
 * Fonte própria (Inter, licença OFL, só os caracteres do português): o app fica igual em qualquer celular,
 * mesmo quando a fonte do sistema é outra (a da Motorola, por exemplo, é serifada).
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

private fun TextStyle.inter(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) =
    copy(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

private val ItalockType = Typography().run {
    Typography(
        headlineSmall = headlineSmall.inter(24, 32, FontWeight.SemiBold, -0.2),
        titleLarge = titleLarge.inter(22, 28, FontWeight.SemiBold, -0.2),
        titleMedium = titleMedium.inter(17, 24, FontWeight.SemiBold),
        titleSmall = titleSmall.inter(15, 20, FontWeight.Medium),
        bodyLarge = bodyLarge.inter(16, 22),
        bodyMedium = bodyMedium.inter(14, 20),
        bodySmall = bodySmall.inter(13, 18),
        labelLarge = labelLarge.inter(15, 20, FontWeight.SemiBold),
        labelMedium = labelMedium.inter(12, 16, FontWeight.SemiBold, 0.3),
        labelSmall = labelSmall.inter(11, 16, FontWeight.Medium, 0.4),
    )
}

val LocalItalockColors = staticCompositionLocalOf { LightExtra }

object Italock {
    val colors: ItalockColors @Composable get() = LocalItalockColors.current
}

/**
 * Aparência escolhida (Configurações → Aparência). Estado global: mudar vale na hora em todas as telas abertas.
 * Carregada das preferências na primeira tela que abrir.
 */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.DARK)
    private var loaded = false

    fun load(context: Context) {
        if (!loaded) { mode = AppPrefs(context).theme; loaded = true }
    }

    fun set(context: Context, m: ThemeMode) {
        AppPrefs(context).theme = m
        mode = m
        loaded = true
        // O widget desenha com as cores da escolha.
        br.italo.italock.widget.PcWidgetProvider.updateAll(context)
    }

    /** Resolve o modo fora do Compose (widget, janela antes do setContent). */
    fun isDark(context: Context, m: ThemeMode = AppPrefs(context).theme): Boolean = when (m) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

/** Fundo da janela de cada modo (evita o clarão antes do Compose desenhar). */
fun windowBackgroundFor(dark: Boolean): Int = if (dark) 0xFF0E1320.toInt() else 0xFFE3E6EB.toInt()

/** [forceDark]: telas sempre escuras. [opaqueWindow] = false nas telas transparentes (só diálogo por cima). */
@Composable
fun ItalockTheme(forceDark: Boolean = false, opaqueWindow: Boolean = true, content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    ThemeState.load(context)
    val systemDark = isSystemInDarkTheme()
    val dark = forceDark || when (ThemeState.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    if (!view.isInEditMode) {
        // Ícones das barras e fundo da janela acompanham o tema (a atividade não é recriada ao trocar).
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (opaqueWindow) window.setBackgroundDrawable(ColorDrawable(windowBackgroundFor(dark)))
        }
    }
    // Direção do texto pelo idioma escolhido no app (árabe da direita para a esquerda), não pelo do sistema.
    val rtl = android.text.TextUtils.getLayoutDirectionFromLocale(br.italo.italock.i18n.L10n.currentLocale()) == android.view.View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        LocalItalockColors provides if (dark) DarkExtra else LightExtra,
        androidx.compose.ui.platform.LocalLayoutDirection provides
            if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, shapes = ItalockShapes, typography = ItalockType, content = content)
    }
}
