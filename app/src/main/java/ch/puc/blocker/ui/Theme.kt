package ch.puc.blocker.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Palette: lavender canvas, white cards, black actions, purple accent.
val Accent = Color(0xFF7C4DFF)
val AccentSoft = Color(0xFFEDE7FF)
val Danger = Color(0xFFE0457B)
internal val Green = Color(0xFF22A06B)
internal val Amber = Color(0xFFE8A200)

private val Light = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color.White,
    primaryContainer = AccentSoft, onPrimaryContainer = Color(0xFF2A1A66),
    secondary = Accent, onSecondary = Color.White,
    secondaryContainer = AccentSoft, onSecondaryContainer = Color(0xFF2A1A66),
    tertiaryContainer = Color(0xFFFDE8F0), onTertiaryContainer = Color(0xFF5A1830),
    background = Color(0xFFF4F3FA), onBackground = Color(0xFF111111),
    surface = Color(0xFFF4F3FA), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFECEAF4), onSurfaceVariant = Color(0xFF6B6878),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outlineVariant = Color(0xFFE6E3EF), error = Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF4F3FA), onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF2E2550), onPrimaryContainer = Color(0xFFE6DDFF),
    secondary = Color(0xFFB39DFF), onSecondary = Color(0xFF1A1033),
    secondaryContainer = Color(0xFF2E2550), onSecondaryContainer = Color(0xFFE6DDFF),
    tertiaryContainer = Color(0xFF4A2233), onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF121118), onBackground = Color(0xFFF4F3FA),
    surface = Color(0xFF121118), onSurface = Color(0xFFF4F3FA),
    surfaceVariant = Color(0xFF2A2833), onSurfaceVariant = Color(0xFFB0ADBC),
    surfaceContainerLowest = Color(0xFF1C1B24), surfaceContainerLow = Color(0xFF1C1B24),
    surfaceContainer = Color(0xFF1C1B24), surfaceContainerHigh = Color(0xFF1C1B24), surfaceContainerHighest = Color(0xFF1C1B24),
    outlineVariant = Color(0xFF2E2C38), error = Danger,
)

private val Serif = FontFamily.Serif
private val base = Typography()
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
)

@Composable
fun BlockerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
    ) { Surface(color = MaterialTheme.colorScheme.background, content = content) }
}

/** Soft pink-to-peach wash used by the hero card. */
@Composable
fun heroBrush(): Brush = if (isSystemInDarkTheme())
    Brush.linearGradient(listOf(Color(0xFF3A2440), Color(0xFF3D2E2A)))
else Brush.linearGradient(listOf(Color(0xFFFBD3EC), Color(0xFFFCE7E4), Color(0xFFFDE3C8)))

/** White rounded card with no elevation, as in the reference design. */
@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Surface(
    modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, content = content,
)

/** Black pill call-to-action. */
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

private val ProgressGradient = listOf(Color(0xFF9B7BFF), Color(0xFFE77FC6), Color(0xFFF7A77C))

/** Ring with a soft gradient sweep and rounded ends; animates on change. */
@Composable
fun GradientRing(fraction: Float, size: Dp, stroke: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            if (f > 0f) rotate(-90f) {
                drawArc(Brush.sweepGradient(ProgressGradient + ProgressGradient.first()), 0f, 360f * f, false, topLeft, arc,
                    style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Thick rounded bar with gradient fill; turns [alert] colour once the limit is reached. */
@Composable
fun SmoothBar(fraction: Float, modifier: Modifier = Modifier, alert: Color? = null, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))) {
        if (f > 0f) Box(
            Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.04f)).clip(RoundedCornerShape(50))
                .background(if (alert != null) SolidColor(alert) else Brush.horizontalGradient(ProgressGradient)),
        )
    }
}

/** The app logo in a rounded tile. */
@Composable
fun AppLogo(size: Dp = 44.dp) = Image(
    painterResource(R.drawable.app_logo), "Social Blocker", Modifier.size(size).clip(RoundedCornerShape(size / 4)),
)
