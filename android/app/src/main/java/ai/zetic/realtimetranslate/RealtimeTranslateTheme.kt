package ai.zetic.realtimetranslate

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Trilogy typefaces, shared with the iOS app. Read Medium supplies emphasized body text. */
val BouyguesRead = FontFamily(
    Font(R.font.bouygues_read_regular, FontWeight.Normal),
    Font(R.font.bouygues_read_medium, FontWeight.Medium),
)
val BouyguesSpeak = FontFamily(Font(R.font.bouygues_speak, FontWeight.Bold))
private val NativeType = Typography()
private val TrilogyType = Typography(
    displayLarge = NativeType.displayLarge.copy(fontFamily = BouyguesSpeak),
    displayMedium = NativeType.displayMedium.copy(fontFamily = BouyguesSpeak),
    displaySmall = NativeType.displaySmall.copy(fontFamily = BouyguesSpeak),
    headlineLarge = NativeType.headlineLarge.copy(fontFamily = BouyguesSpeak),
    headlineMedium = NativeType.headlineMedium.copy(fontFamily = BouyguesSpeak),
    headlineSmall = NativeType.headlineSmall.copy(fontFamily = BouyguesSpeak),
    titleLarge = NativeType.titleLarge.copy(fontFamily = BouyguesSpeak),
    titleMedium = NativeType.titleMedium.copy(fontFamily = BouyguesRead),
    titleSmall = NativeType.titleSmall.copy(fontFamily = BouyguesRead),
    bodyLarge = NativeType.bodyLarge.copy(fontFamily = BouyguesRead),
    bodyMedium = NativeType.bodyMedium.copy(fontFamily = BouyguesRead),
    bodySmall = NativeType.bodySmall.copy(fontFamily = BouyguesRead),
    labelLarge = NativeType.labelLarge.copy(fontFamily = BouyguesRead),
    labelMedium = NativeType.labelMedium.copy(fontFamily = BouyguesRead),
    labelSmall = NativeType.labelSmall.copy(fontFamily = BouyguesRead),
)

/** Native adaptation: 8-point controls, 16-point bubbles, 48-point functional actions. */
val ActionHeight = 48.dp

val Accent = Color(0xFF25465F)
val Surface = Color.White
val SurfaceSubtle = Color(0xFFEFF2F8)
val DividerLine = Color(0xFFBBC6CD)
val TextPrimary = Color(0xFF25465F)
val TextSecondary = Color(0xCC25465F)
val Error = Color(0xFFD42D02)

/** `radius.message` and `radius.control`, shared by the main screen, the drawer, and the toast. */
val MessageShape: Shape = RoundedCornerShape(16.dp)
val ControlShape: Shape = RoundedCornerShape(8.dp)

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
        typography = TrilogyType,
        content = { ProvideTextStyle(MaterialTheme.typography.bodyLarge, content = content) },
    )
}
