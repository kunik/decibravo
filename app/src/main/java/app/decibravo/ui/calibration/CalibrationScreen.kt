package app.decibravo.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.ui.common.LevelBars
import app.decibravo.ui.common.OutlineButton
import app.decibravo.ui.common.PrimaryButton
import app.decibravo.ui.common.ScoreNumber
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.common.WithMicPermission
import app.decibravo.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(onBack: (() -> Unit)?, vm: CalibrationViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(stringResource(R.string.calibration_title), onBack)
        WithMicPermission {
            MicCheckBlock(vm)
        }
    }
}

private fun db(v: Double) = v.roundToInt().toString().replace('-', '−')

@Composable
private fun MicCheckBlock(vm: CalibrationViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(s.listening) {
        view.keepScreenOn = s.listening
        onDispose { view.keepScreenOn = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(stringResource(R.string.mic_check_title), color = Palette.Text, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (s.unprocessed) R.string.mic_source_unprocessed else R.string.mic_source_fallback),
            color = Palette.Secondary, fontSize = 16.sp,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            stringResource(if (s.listening) R.string.mic_stop else R.string.mic_listen),
            onClick = vm::toggleListening,
        )
        if (s.error) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.mic_error), color = Palette.Warning, fontSize = 16.sp)
        }
        if (s.listening) {
            Spacer(Modifier.height(16.dp))
            ScoreNumber(db(s.levelDb), modifier = Modifier.padding(vertical = 0.dp))
            Text(stringResource(R.string.mic_level_unit), color = Palette.Secondary, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mic_peak, db(s.peakDb)), color = Palette.Text, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            LevelBars(s.bars)
        }
        Spacer(Modifier.height(24.dp))
        OutlineButton(
            stringResource(R.string.mic_measure_floor),
            onClick = vm::measureFloor,
            enabled = s.floor !is FloorCheck.Counting,
        )
        Spacer(Modifier.height(12.dp))
        when (val f = s.floor) {
            FloorCheck.Idle -> {}
            is FloorCheck.Counting -> Text(stringResource(R.string.mic_floor_counting, f.secondsLeft), color = Palette.Secondary, fontSize = 18.sp)
            is FloorCheck.Done -> {
                Text(stringResource(R.string.mic_floor_result, db(f.db.toDouble())), color = Palette.Text, fontSize = 18.sp)
                if (f.noisy) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.mic_floor_noisy), color = Palette.Warning, fontSize = 16.sp)
                }
            }
        }
    }
}
