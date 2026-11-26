package app.decibravo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.decibravo.ui.AppNav
import app.decibravo.ui.theme.DeciBravoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Один механізм для всіх екранів: чорні системні панелі, вміст усередині safeDrawing (рядок стану, навігація, виріз, клавіатура).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Black.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Black.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent { DeciBravoTheme { Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) { AppNav() } } }
    }
}
