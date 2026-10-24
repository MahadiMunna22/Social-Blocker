package ch.puc.blocker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import ch.puc.blocker.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Accent colours. Every one uses the same frosted look (backdrop, glass cards and bar); only the accent changes.
 * The order keeps older saved ordinals meaningful: Classic was purple, iOS blue, Material You → pink, Calm green, Graphite orange.
 */
enum class AppTheme(val label: String, val dark: Color, val light: Color) {
    PURPLE("Purple", Color(0xFFA78BFA), Color(0xFF7C4DFF)),
    BLUE("Blue", Color(0xFF0A84FF), Color(0xFF007AFF)),
    PINK("Pink", Color(0xFFFF5C93), Color(0xFFD63D73)),
    GREEN("Green", Color(0xFF4CC38A), Color(0xFF1E9E63)),
    ORANGE("Orange", Color(0xFFF26B1D), Color(0xFFE85D0F)),
}

enum class AppearanceMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

internal val Danger = Color(0xFFE0457B)
internal val Green = Color(0xFF22A06B)
internal val Amber = Color(0xFFE8A200)

/** What a theme decides beyond Material's colour scheme. */
private class Look(val scheme: ColorScheme, val accent: Color, val accentSoft: Color, val dark: Boolean)

private val LocalLook = staticCompositionLocalOf<Look> { error("BlockerTheme missing") }

/** Theme accent for links, highlights and the selected chart bar. */
val Accent: Color @Composable get() = LocalLook.current.accent
val AccentSoft: Color @Composable get() = LocalLook.current.accentSoft
/** Dark variant in use (setting or system), for the few places that tune alpha by mode. */
val isDarkLook: Boolean @Composable get() = LocalLook.current.dark

// Graphite canvas, frosted grey cards with a light rim, accent-coloured actions (after the "Hume" smart home design).
private fun look(theme: AppTheme, dark: Boolean): Look {
    val a = if (dark) theme.dark else theme.light
    return if (dark) {
        val bg = Color(0xFF1E1E20)
        val soft = lerp(bg, a, 0.28f)
        Look(
            darkColorScheme(
                primary = a, onPrimary = Color.White,
                primaryContainer = soft, onPrimaryContainer = lerp(a, Color.White, 0.75f),
                secondary = a, onSecondary = Color.White,
                secondaryContainer = soft, onSecondaryContainer = lerp(a, Color.White, 0.75f),
                tertiaryContainer = Color(0xFF4D1F1F), onTertiaryContainer = Color(0xFFFFDAD6),
                background = bg, onBackground = Color(0xFFF2F2F3),
                surface = bg, onSurface = Color(0xFFF2F2F3),
                surfaceVariant = Color(0xFF38383B), onSurfaceVariant = Color(0xFFA3A3A8),
                surfaceContainerLowest = Color(0xFF2B2B2E), surfaceContainerLow = Color(0xFF2B2B2E),
                surfaceContainer = Color(0xFF2B2B2E), surfaceContainerHigh = Color(0xFF333336), surfaceContainerHighest = Color(0xFF333336),
                outlineVariant = Color(0xFF3E3E42), error = Color(0xFFFF5A4E),
            ),
            accent = a, accentSoft = soft, dark = true,
        )
    } else {
        val soft = lerp(Color.White, a, 0.15f)
        Look(
            lightColorScheme(
                primary = a, onPrimary = Color.White,
                primaryContainer = soft, onPrimaryContainer = lerp(a, Color.Black, 0.6f),
                secondary = a, onSecondary = Color.White,
                secondaryContainer = soft, onSecondaryContainer = lerp(a, Color.Black, 0.6f),
                tertiaryContainer = Color(0xFFFFE0DC), onTertiaryContainer = Color(0xFF5F0B05),
                background = Color(0xFFE9E9EB), onBackground = Color(0xFF1C1C1E),
                surface = Color(0xFFE9E9EB), onSurface = Color(0xFF1C1C1E),
                surfaceVariant = Color(0xFFDADADD), onSurfaceVariant = Color(0xFF66666B),
                surfaceContainerLowest = Color(0xFFF7F7F8), surfaceContainerLow = Color(0xFFF7F7F8),
                surfaceContainer = Color(0xFFF7F7F8), surfaceContainerHigh = Color(0xFFF7F7F8), surfaceContainerHighest = Color(0xFFF7F7F8),
                outlineVariant = Color(0xFFD3D3D6), error = Color(0xFFE5392C),
            ),
            accent = a, accentSoft = soft, dark = false,
        )
    }
}

private val base = Typography()
private val typography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun BlockerTheme(theme: AppTheme = AppTheme.ORANGE, mode: AppearanceMode = AppearanceMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { AppearanceMode.SYSTEM -> isSystemInDarkTheme(); AppearanceMode.LIGHT -> false; AppearanceMode.DARK -> true }
    val look = look(theme, dark)
    val r = CardRadius
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(
            colorScheme = look.scheme,
            typography = typography,
            shapes = Shapes(small = RoundedCornerShape(r / 2), medium = RoundedCornerShape(r * 5 / 6), large = RoundedCornerShape(r)),
        ) {
            Surface(color = look.scheme.background, content = content)
        }
    }
}

private val CardRadius = 24.dp

/** The bright rim that makes glass read as glass. */
@Composable
fun glassBorder(): BorderStroke = BorderStroke(
    1.dp, Brush.verticalGradient(
        if (LocalLook.current.dark) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
        else listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f)),
    ),
)

/** Fill for the hero cards: the same frost as other cards. */
@Composable
fun heroBrush(): Brush = SolidColor(frost())

/** Frosted card with a light rim and no elevation. */
@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Surface(
    modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius), color = frost(), border = glassBorder(), content = content,
)

/** See-through card fill for the frosted look. */
@Composable
private fun frost() = if (LocalLook.current.dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.6f)

/** Pill call-to-action in the theme's primary colour. */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, trailing: String? = null, onClick: () -> Unit) = Button(
    onClick = onClick, enabled = enabled, modifier = modifier,
    shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.SemiBold)
        if (trailing != null) { Spacer(Modifier.width(10.dp)); Text(trailing) }
    }
}

/** Small rounded status label, e.g. "Blocked". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) = Text(
    text,
    modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
    color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
)

/** Ring in the solid accent colour with rounded ends; animates on change. */
@Composable
fun ProgressRing(fraction: Float, size: Dp, stroke: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val accent = LocalLook.current.accent
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            if (f > 0f) rotate(-90f) {
                drawArc(accent, 0f, 360f * f, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Thick rounded bar in the accent colour; turns [alert] colour once the limit is reached. */
@Composable
fun SmoothBar(fraction: Float, modifier: Modifier = Modifier, alert: Color? = null, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    val accent = LocalLook.current.accent
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))) {
        if (f > 0f) Box(
            Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.04f)).clip(RoundedCornerShape(50))
                .background(alert ?: accent),
        )
    }
}

/** The app logo in a rounded tile. */
@Composable
fun AppLogo(size: Dp = 44.dp) = Image(
    painterResource(R.drawable.app_logo), "Social Blocker", Modifier.size(size).clip(RoundedCornerShape(size / 4)),
)
