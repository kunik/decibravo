package app.decibravo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object Palette {
    val Bg = Color(0xFF000000)
    val Text = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFA8A8A8)
    val Line = Color(0xFF333333)
    val Accent = Color(0xFFFFD60A)
    val Warning = Color(0xFFFF9F0A)
}

/** Єдине місце, де задано шрифт цифр. Тимчасово системний вузький жирний; пізніше замінити на Barlow Condensed Bold. */
val DigitFont: FontFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Bold))

@Composable
fun DeciBravoTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Accent,
        onPrimary = Palette.Bg,
        background = Palette.Bg,
        onBackground = Palette.Text,
        surface = Palette.Bg,
        onSurface = Palette.Text,
        outline = Palette.Line,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
