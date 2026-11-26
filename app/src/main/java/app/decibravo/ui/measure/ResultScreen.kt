package app.decibravo.ui.measure

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.decibravo.ui.theme.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.scoring.fmtPlace
import app.decibravo.ui.common.OutlineButton

@Composable
fun ResultScreen(onBack: () -> Unit, onRedo: () -> Unit, vm: ResultViewModel = viewModel()) {
    val ui = vm.ui
    var confirm by remember { mutableStateOf(false) }
    if (ui == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    ResultBody(
        ui.name, ui.result, stringResource(R.string.result_place, fmtPlace(ui.place)),
        endReasonText(ui.result.reason, ui.quietEndSec), onBack,
    ) {
        OutlineButton(stringResource(R.string.measure_redo), onClick = onRedo)
        OutlineButton(stringResource(R.string.result_delete), onClick = { confirm = true })
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Bg,
            title = { Text(stringResource(R.string.result_delete_confirm, ui.name), color = Palette.Text) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.delete(); onBack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.result_delete), color = Palette.Warning) } },
            dismissButton = { TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel), color = Palette.Text) } },
        )
    }
}
