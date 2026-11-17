package com.financeapp.nativeapp.presentation.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════
// Paleta de Cores Premium — FinanceApp Dark Theme
// ═══════════════════════════════════════════════════════════

// Identidade premium (vermelho profundo)
val Primary = Color(0xFFF22B42)         // Vermelho vibrante
val PrimaryVariant = Color(0xFF9B1227)  // Vermelho profundo
val OnPrimary = Color.White

// Cores Secundárias
val Secondary = Color(0xFFFF8B90)       // Rosa suave
val OnSecondary = Color.Black

// Cores de Superfície (Dark mode)
val Background = Color(0xFF0C0D12)       // Preto azulado profundo
val Surface = Color(0xFF14151B)          // Superfície escura
val SurfaceVariant = Color(0xFF20212A)   // Cards / Superfície elevada
val SurfaceContainer = Color(0xFF191A22) // Container
val OnBackground = Color(0xFFF5F4F6)     // Texto principal
val OnSurface = Color(0xFFF5F4F6)        // Texto sobre superfície
val OnSurfaceVariant = Color(0xFF9CA3AF) // Texto secundário

// Cores Semânticas — Receita / Despesa
val IncomeGreen = Color(0xFF10B981)      // Verde esmeralda
val IncomeGreenDark = Color(0xFF059669)  // Verde escuro
val IncomeGreenBg = Color(0xFF10B981).copy(alpha = 0.12f)

val ExpenseRed = Color(0xFFEF4444)       // Vermelho
val ExpenseRedDark = Color(0xFFDC2626)   // Vermelho escuro
val ExpenseRedBg = Color(0xFFEF4444).copy(alpha = 0.12f)

// Cores de Status de Sincronização
val SyncPending = Color(0xFFFFA726)      // Laranja
val SyncSyncing = Color(0xFF42A5F5)      // Azul
val SyncSynced = Color(0xFF66BB6A)       // Verde
val OfflineRed = Color(0xFFE57373)       // Vermelho suave

// Gradientes auxiliares
val GradientStart = Color(0xFFF22B42)    // Vermelho
val GradientEnd = Color(0xFFEA3146)      // Carmim

// Outline / Bordas
val OutlineColor = Color(0xFF3D343D)
val DividerColor = Color(0xFF302830)

// ═══════════════════════════════════════════════════════════
// Color Scheme Material 3 — Dark
// ═══════════════════════════════════════════════════════════

private val FinanceDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF401924),
    onPrimaryContainer = Color(0xFFFFBBC3),
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Color(0xFF392025),
    onSecondaryContainer = Color(0xFFFFD6D9),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color.Black,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineColor,
    outlineVariant = DividerColor,
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFFB4AB),
    inverseSurface = Color(0xFFF5F4F6),
    inverseOnSurface = Color(0xFF14151B),
    inversePrimary = PrimaryVariant,
    surfaceTint = Primary,
)

// Tema claro: fundo branco, superfícies suaves e vermelho acessível como destaque.
private val FinanceLightColorScheme = lightColorScheme(
    primary = Color(0xFFC41E37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E5),
    onPrimaryContainer = Color(0xFF771326),
    secondary = Color(0xFF9F253A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7EA),
    onSecondaryContainer = Color(0xFF7C2435),
    tertiary = Color(0xFF855A00),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF191A20),
    surface = Color.White,
    onSurface = Color(0xFF191A20),
    surfaceVariant = Color(0xFFF7F3F4),
    onSurfaceVariant = Color(0xFF5C5660),
    outline = Color(0xFFD8C9CE),
    outlineVariant = Color(0xFFE9DDE1),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF29262A),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFFFFB3BF),
    surfaceTint = Color(0xFFC41E37)
)

// ═══════════════════════════════════════════════════════════
// Tipografia
// ═══════════════════════════════════════════════════════════

val FinanceTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ═══════════════════════════════════════════════════════════
// Shapes (Arredondamentos Premium)
// ═══════════════════════════════════════════════════════════

val FinanceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ═══════════════════════════════════════════════════════════
// Composable Theme
// ═══════════════════════════════════════════════════════════

@Composable
fun FinanceAppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FinanceDarkColorScheme else FinanceLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinanceTypography,
        shapes = FinanceShapes,
        content = content
    )
}
