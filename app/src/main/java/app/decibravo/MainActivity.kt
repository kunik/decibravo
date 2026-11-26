package app.decibravo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.decibravo.ui.AppNav
import app.decibravo.ui.theme.DeciBravoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DeciBravoTheme { AppNav() } }
    }
}
