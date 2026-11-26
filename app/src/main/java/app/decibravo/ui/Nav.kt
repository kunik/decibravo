package app.decibravo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.decibravo.ui.calibration.CalibrationScreen

object Routes {
    const val COMPETITIONS = "competitions"
    const val CALIBRATION = "calibration"
    const val NEW = "competition/new"
    fun table(id: String) = "competition/$id"
    fun edit(id: String) = "competition/$id/edit"
    fun floor(id: String, thenParticipant: String? = null): String =
        "competition/$id/floor" + (thenParticipant?.let { "?then=$it" } ?: "")
    fun measure(id: String, participantId: String) = "competition/$id/measure/$participantId"
    fun result(id: String, participantId: String) = "competition/$id/result/$participantId"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    // Тимчасово стартовий екран — калібрування; у Task 8 стартовим стане список змагань.
    NavHost(navController = nav, startDestination = Routes.CALIBRATION) {
        composable(Routes.CALIBRATION) { CalibrationScreen(onBack = null) }
    }
}
