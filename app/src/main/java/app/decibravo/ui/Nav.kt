package app.decibravo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.decibravo.ui.calibration.CalibrationScreen
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.competitions.CompetitionListScreen
import app.decibravo.ui.edit.CompetitionEditScreen
import app.decibravo.ui.floor.FloorScreen
import app.decibravo.ui.measure.MeasureScreen
import app.decibravo.ui.measure.ResultScreen
import app.decibravo.ui.table.TableScreen
import app.decibravo.ui.theme.Palette

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

    const val TABLE_PATTERN = "competition/{id}"
    const val EDIT_PATTERN = "competition/{id}/edit"
    const val FLOOR_PATTERN = "competition/{id}/floor?then={then}"
    const val MEASURE_PATTERN = "competition/{id}/measure/{participantId}"
    const val RESULT_PATTERN = "competition/{id}/result/{participantId}"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.COMPETITIONS) {
        composable(Routes.COMPETITIONS) {
            CompetitionListScreen(
                onOpen = { nav.navigate(Routes.table(it)) },
                onNew = { nav.navigate(Routes.NEW) },
                onCalibration = { nav.navigate(Routes.CALIBRATION) },
            )
        }
        composable(Routes.CALIBRATION) { CalibrationScreen(onBack = back) }
        composable(Routes.NEW) {
            CompetitionEditScreen(
                onBack = back,
                onSaved = { id, _ -> nav.navigate(Routes.table(id)) { popUpTo(Routes.COMPETITIONS) } },
                onMeasureFloor = { nav.navigate(Routes.floor(it)) },
                onDeleted = { nav.popBackStack(Routes.COMPETITIONS, false) },
            )
        }
        composable(Routes.EDIT_PATTERN, listOf(navArgument("id") { type = NavType.StringType })) {
            CompetitionEditScreen(
                onBack = back,
                onSaved = { _, _ -> nav.popBackStack() },
                onMeasureFloor = { nav.navigate(Routes.floor(it)) },
                onDeleted = { nav.popBackStack(Routes.COMPETITIONS, false) },
            )
        }
        composable(Routes.TABLE_PATTERN, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            TableScreen(
                competitionId = id,
                onBack = back,
                onSettings = { nav.navigate(Routes.edit(id)) },
                onFloor = { nav.navigate(Routes.floor(id, it)) },
                onMeasure = { nav.navigate(Routes.measure(id, it)) },
                onResult = { nav.navigate(Routes.result(id, it)) },
            )
        }
        composable(
            Routes.FLOOR_PATTERN,
            listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("then") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            FloorScreen(
                onBack = back,
                onKept = { then ->
                    if (then != null) nav.navigate(Routes.measure(id, then)) { popUpTo(Routes.FLOOR_PATTERN) { inclusive = true } }
                    else nav.popBackStack()
                },
            )
        }
        composable(Routes.MEASURE_PATTERN) {
            val toTable: () -> Unit = { if (!nav.popBackStack(Routes.TABLE_PATTERN, false)) nav.popBackStack() }
            MeasureScreen(onExit = toTable)
        }
        composable(Routes.RESULT_PATTERN) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            val pid = entry.arguments?.getString("participantId") ?: ""
            ResultScreen(onBack = back, onRedo = { nav.navigate(Routes.measure(id, pid)) })
        }
    }
}
