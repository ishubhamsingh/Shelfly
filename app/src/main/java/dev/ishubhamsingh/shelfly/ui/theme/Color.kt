package dev.ishubhamsingh.shelfly.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Light scheme — seeded from warm sage-green #3F6B26 ───────────────────────
private val primaryLight                = Color(0xFF3F6B26)
private val onPrimaryLight              = Color(0xFFFFFFFF)
private val primaryContainerLight       = Color(0xFFC0F0A1)
private val onPrimaryContainerLight     = Color(0xFF0E2300)

private val secondaryLight              = Color(0xFF56624B)
private val onSecondaryLight            = Color(0xFFFFFFFF)
private val secondaryContainerLight     = Color(0xFFDAE8C9)
private val onSecondaryContainerLight   = Color(0xFF141E0B)

// Tertiary is amber-leaning so it doubles as the "expiring soon" tone
private val tertiaryLight               = Color(0xFF7C5800)
private val onTertiaryLight             = Color(0xFFFFFFFF)
private val tertiaryContainerLight      = Color(0xFFFFDEA8)
private val onTertiaryContainerLight    = Color(0xFF271900)

private val errorLight                  = Color(0xFFBA1A1A)
private val onErrorLight                = Color(0xFFFFFFFF)
private val errorContainerLight         = Color(0xFFFFDAD6)
private val onErrorContainerLight       = Color(0xFF410002)

private val backgroundLight             = Color(0xFFFBFCF5)
private val onBackgroundLight           = Color(0xFF1A1C16)
private val surfaceLight                = Color(0xFFFBFCF5)
private val onSurfaceLight              = Color(0xFF1A1C16)
private val surfaceDimLight             = Color(0xFFDCDDD2)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight    = Color(0xFFF5F6EE)
private val surfaceContainerLight       = Color(0xFFEFF1E8)
private val surfaceContainerHighLight   = Color(0xFFE9EBE2)
private val surfaceContainerHighestLight= Color(0xFFE4E5DD)
private val surfaceVariantLight         = Color(0xFFE1E4D5)
private val onSurfaceVariantLight       = Color(0xFF44483D)
private val outlineLight                = Color(0xFF75796C)
private val outlineVariantLight         = Color(0xFFC5C8BA)
private val inverseSurfaceLight         = Color(0xFF2F312A)
private val inverseOnSurfaceLight       = Color(0xFFF1F2EA)

// ── Dark scheme ───────────────────────────────────────────────────────────────
private val primaryDark                 = Color(0xFFA5D588)
private val onPrimaryDark               = Color(0xFF193800)
private val primaryContainerDark        = Color(0xFF275111)
private val onPrimaryContainerDark      = Color(0xFFC0F0A1)

private val secondaryDark               = Color(0xFFBECBAE)
private val onSecondaryDark             = Color(0xFF293420)
private val secondaryContainerDark      = Color(0xFF3F4A35)
private val onSecondaryContainerDark    = Color(0xFFDAE8C9)

private val tertiaryDark                = Color(0xFFF0BF6E)
private val onTertiaryDark              = Color(0xFF422C00)
private val tertiaryContainerDark       = Color(0xFF5E4200)
private val onTertiaryContainerDark     = Color(0xFFFFDEA8)

private val errorDark                   = Color(0xFFFFB4AB)
private val onErrorDark                 = Color(0xFF690005)
private val errorContainerDark          = Color(0xFF93000A)
private val onErrorContainerDark        = Color(0xFFFFDAD6)

private val backgroundDark              = Color(0xFF11140C)
private val onBackgroundDark            = Color(0xFFE2E3D8)
private val surfaceDark                 = Color(0xFF11140C)
private val onSurfaceDark               = Color(0xFFE2E3D8)
private val surfaceDimDark              = Color(0xFF11140C)
private val surfaceContainerLowestDark  = Color(0xFF0C0F08)
private val surfaceContainerLowDark     = Color(0xFF1A1C14)
private val surfaceContainerDark        = Color(0xFF1E2018)
private val surfaceContainerHighDark    = Color(0xFF282B22)
private val surfaceContainerHighestDark = Color(0xFF33362D)
private val surfaceVariantDark          = Color(0xFF44483D)
private val onSurfaceVariantDark        = Color(0xFFC5C8BA)
private val outlineDark                 = Color(0xFF8F9286)
private val outlineVariantDark          = Color(0xFF44483D)
private val inverseSurfaceDark          = Color(0xFFE2E3D8)
private val inverseOnSurfaceDark        = Color(0xFF2F312A)

// ── Color scheme objects (passed to MaterialTheme) ────────────────────────────
val ShelflyLightColors = lightColorScheme(
    primary                 = primaryLight,
    onPrimary               = onPrimaryLight,
    primaryContainer        = primaryContainerLight,
    onPrimaryContainer      = onPrimaryContainerLight,
    secondary               = secondaryLight,
    onSecondary             = onSecondaryLight,
    secondaryContainer      = secondaryContainerLight,
    onSecondaryContainer    = onSecondaryContainerLight,
    tertiary                = tertiaryLight,
    onTertiary              = onTertiaryLight,
    tertiaryContainer       = tertiaryContainerLight,
    onTertiaryContainer     = onTertiaryContainerLight,
    error                   = errorLight,
    onError                 = onErrorLight,
    errorContainer          = errorContainerLight,
    onErrorContainer        = onErrorContainerLight,
    background              = backgroundLight,
    onBackground            = onBackgroundLight,
    surface                 = surfaceLight,
    onSurface               = onSurfaceLight,
    surfaceDim              = surfaceDimLight,
    surfaceContainerLowest  = surfaceContainerLowestLight,
    surfaceContainerLow     = surfaceContainerLowLight,
    surfaceContainer        = surfaceContainerLight,
    surfaceContainerHigh    = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
    surfaceVariant          = surfaceVariantLight,
    onSurfaceVariant        = onSurfaceVariantLight,
    outline                 = outlineLight,
    outlineVariant          = outlineVariantLight,
    inverseSurface          = inverseSurfaceLight,
    inverseOnSurface        = inverseOnSurfaceLight,
)

val ShelflyDarkColors = darkColorScheme(
    primary                 = primaryDark,
    onPrimary               = onPrimaryDark,
    primaryContainer        = primaryContainerDark,
    onPrimaryContainer      = onPrimaryContainerDark,
    secondary               = secondaryDark,
    onSecondary             = onSecondaryDark,
    secondaryContainer      = secondaryContainerDark,
    onSecondaryContainer    = onSecondaryContainerDark,
    tertiary                = tertiaryDark,
    onTertiary              = onTertiaryDark,
    tertiaryContainer       = tertiaryContainerDark,
    onTertiaryContainer     = onTertiaryContainerDark,
    error                   = errorDark,
    onError                 = onErrorDark,
    errorContainer          = errorContainerDark,
    onErrorContainer        = onErrorContainerDark,
    background              = backgroundDark,
    onBackground            = onBackgroundDark,
    surface                 = surfaceDark,
    onSurface               = onSurfaceDark,
    surfaceDim              = surfaceDimDark,
    surfaceContainerLowest  = surfaceContainerLowestDark,
    surfaceContainerLow     = surfaceContainerLowDark,
    surfaceContainer        = surfaceContainerDark,
    surfaceContainerHigh    = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
    surfaceVariant          = surfaceVariantDark,
    onSurfaceVariant        = onSurfaceVariantDark,
    outline                 = outlineDark,
    outlineVariant          = outlineVariantDark,
    inverseSurface          = inverseSurfaceDark,
    inverseOnSurface        = inverseOnSurfaceDark,
)

// ── Semantic status palette ───────────────────────────────────────────────────
// Not standard M3 roles; exposed via CompositionLocal alongside the M3 theme so
// the meaning stays stable even when dynamic color overrides the M3 roles.
@Immutable
data class ShelflyStatusColors(
    val goodContainer: Color,
    val onGoodContainer: Color,
    val soonContainer: Color,
    val onSoonContainer: Color,
    val expiredContainer: Color,
    val onExpiredContainer: Color,
    val consumedContainer: Color,
    val onConsumedContainer: Color,
    // Solid-fill colors for progress bar indicators — tuned for thin bar on surface, not text contrast
    val goodIndicator: Color,
    val soonIndicator: Color,
    val expiredIndicator: Color,
)

val LightStatusColors = ShelflyStatusColors(
    goodContainer       = Color(0xFFD9F0C8),
    onGoodContainer     = Color(0xFF1E4007),
    soonContainer       = Color(0xFFFFE3B5),
    onSoonContainer     = Color(0xFF4A3000),
    expiredContainer    = Color(0xFFFFDAD6),
    onExpiredContainer  = Color(0xFF7A0011),
    consumedContainer   = Color(0xFFE4E5DD),
    onConsumedContainer = Color(0xFF5C5F55),
    goodIndicator       = Color(0xFF2E7D32), // Green 800
    soonIndicator       = Color(0xFFF57C00), // Orange 700
    expiredIndicator    = Color(0xFFC62828), // Red 800
)

val DarkStatusColors = ShelflyStatusColors(
    goodContainer       = Color(0xFF2C4F15),
    onGoodContainer     = Color(0xFFC0F0A1),
    soonContainer       = Color(0xFF5E4200),
    onSoonContainer     = Color(0xFFFFDEA8),
    expiredContainer    = Color(0xFF7A0011),
    onExpiredContainer  = Color(0xFFFFDAD6),
    consumedContainer   = Color(0xFF33362D),
    onConsumedContainer = Color(0xFFC5C8BA),
    goodIndicator       = Color(0xFF66BB6A), // Green 400
    soonIndicator       = Color(0xFFFFA726), // Orange 400
    expiredIndicator    = Color(0xFFEF5350), // Red 400
)

val LocalShelflyStatusColors = staticCompositionLocalOf { LightStatusColors }
