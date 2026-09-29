package com.mamadrones.gcs.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamadrones.gcs.domain.model.ThemeMode

object MamaColors {
    val Dark = darkColorScheme(
        primary = Color(0xFF65B4FF), onPrimary = Color(0xFF00213B),
        primaryContainer = Color(0xFF203C56), onPrimaryContainer = Color(0xFFD4E9FF),
        secondary = Color(0xFF84DDB4), secondaryContainer = Color(0xFF164432),
        background = Color(0xFF0C1219), onBackground = Color(0xFFE5EDF6),
        surface = Color(0xFF151E28), onSurface = Color(0xFFE5EDF6),
        surfaceTint = Color(0xFF65B4FF), surfaceContainer = Color(0xFF192330),
        surfaceContainerLow = Color(0xFF151E28), surfaceContainerHigh = Color(0xFF202C39),
        surfaceContainerHighest = Color(0xFF273546), surfaceContainerLowest = Color(0xFF080E15),
        surfaceVariant = Color(0xFF202C39), onSurfaceVariant = Color(0xFFADBCCD),
        outline = Color(0xFF607387), outlineVariant = Color(0xFF314153),
        error = Color(0xFFFFB4B4), errorContainer = Color(0xFF53282D), onErrorContainer = Color(0xFFFFDBDC)
    )
    val Light = lightColorScheme(
        primary = Color(0xFF075D9F), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8EAFF), onPrimaryContainer = Color(0xFF073454),
        secondary = Color(0xFF176746), secondaryContainer = Color(0xFFBAEED7),
        background = Color(0xFFEFF3F7), onBackground = Color(0xFF152331),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF152331),
        surfaceTint = Color(0xFF075D9F), surfaceContainer = Color(0xFFE7EDF4),
        surfaceContainerLow = Color(0xFFF5F8FC), surfaceContainerHigh = Color(0xFFE0E8F1),
        surfaceContainerHighest = Color(0xFFD7E1EC), surfaceContainerLowest = Color.White,
        surfaceVariant = Color(0xFFE0E8F1), onSurfaceVariant = Color(0xFF41566A),
        outline = Color(0xFF65798D), outlineVariant = Color(0xFFBCCBD8),
        error = Color(0xFFA32132), errorContainer = Color(0xFFFFDADD), onErrorContainer = Color(0xFF701728)
    )
}

object MamaSpacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val touchTarget = 48.dp
}
val MamaShapes = Shapes(
    small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(16.dp)
)
val MamaTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp)
)

@Composable
fun MamaGcsTheme(theme: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) MamaColors.Dark else MamaColors.Light, typography = MamaTypography, shapes = MamaShapes, content = content)
}
