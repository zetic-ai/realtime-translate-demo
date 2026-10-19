package ai.zetic.realtimetranslate

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Relais uses Bouygues Telecom Trilogy colors with native typography. */
val Accent = Color(0xFF25465F)
val Surface = Color.White
val SurfaceSubtle = Color(0xFFEFF2F8)
val DividerLine = Color(0xFFBBC6CD)
val TextPrimary = Color(0xFF25465F)
val TextSecondary = Color(0xCC25465F)
val Error = Color(0xFFD42D02)

/** `radius.message` and `radius.control`, shared by the main screen, the drawer, and the toast. */
val MessageShape: Shape = RoundedCornerShape(12.dp)
val ControlShape: Shape = RoundedCornerShape(12.dp)

/** `color.scrim`: the dim behind the settings drawer and the first-run consent card. */
val Scrim = Color.Black.copy(alpha = 0.16f)

/**
 * Speaker A uses Bouygues main blue, and speaker B uses the orange accent.
 * Color is always redundant with the speaker label and
 * the left/right alignment, never the only distinguisher.
 */
val AccentA = Color(0xFF25465F)
val DeepA = Color(0xFF25465F)
val TintA = Color(0xFFEFF2F8)
val BorderA = Color(0xFFBBC6CD)
val AccentB = Color(0xFFCC4E0A)
val DeepB = Color(0xFFCC4E0A)
val TintB = Color(0xFFE9EFF4)
val BorderB = Color(0xFFBBC6CD)

fun speakerAccent(speaker: Speaker) = if (speaker == Speaker.A) AccentA else AccentB
fun speakerDeep(speaker: Speaker) = if (speaker == Speaker.A) DeepA else DeepB
fun speakerTint(speaker: Speaker) = if (speaker == Speaker.A) TintA else TintB
fun speakerBorder(speaker: Speaker) = if (speaker == Speaker.A) BorderA else BorderB

@Composable
fun RealtimeTranslateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Surface,
            secondary = Accent,
            onSecondary = Surface,
            background = Surface,
            onBackground = TextPrimary,
            surface = Surface,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceSubtle,
            onSurfaceVariant = TextSecondary,
            outline = DividerLine,
            error = Error,
            onError = Surface,
            primaryContainer = SurfaceSubtle,
            onPrimaryContainer = TextPrimary,
            secondaryContainer = SurfaceSubtle,
            onSecondaryContainer = TextPrimary,
            tertiary = AccentB,
            onTertiary = Surface,
            tertiaryContainer = TintB,
            onTertiaryContainer = TextPrimary,
            errorContainer = SurfaceSubtle,
            onErrorContainer = Error,
            outlineVariant = DividerLine,
            surfaceTint = Surface,
            inverseSurface = TextPrimary,
            inverseOnSurface = Surface,
            inversePrimary = DividerLine,
            surfaceDim = TintB,
            surfaceBright = Surface,
            surfaceContainerLowest = Surface,
            surfaceContainerLow = SurfaceSubtle,
            surfaceContainer = SurfaceSubtle,
            surfaceContainerHigh = TintB,
            surfaceContainerHighest = TintB,
        ),
        content = content,
    )
}
