package app.decibravo.ui.floor

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.ui.common.OutlineButton
import app.decibravo.ui.common.PrimaryButton
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.common.WithMicPermission
import app.decibravo.ui.theme.Palette

private fun db(v: Int) = v.toString().replace('-', '−')

/** onKept(thenParticipant): викликається після збереження фону. */
@Composable
fun FloorScreen(onBack: () -> Unit, onKept: (thenParticipant: String?) -> Unit, vm: FloorViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onActivityStopped() }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(stringResource(R.string.floor_screen_title), onBack)
        WithMicPermission {
            val s by vm.state.collectAsStateWithLifecycle()
            val view = LocalView.current
            val counting = s is FloorState.Counting
            DisposableEffect(counting) {
                view.keepScreenOn = counting
                onDispose { view.keepScreenOn = false }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(stringResource(R.string.floor_hint), color = Palette.Text, fontSize = 20.sp)
                Spacer(Modifier.height(24.dp))
                when (val st = s) {
                    FloorState.Idle -> PrimaryButton(stringResource(R.string.floor_measure_button), onClick = vm::measure)
                    is FloorState.Counting ->
                        Text(stringResource(R.string.mic_floor_counting, st.secondsLeft), color = Palette.Secondary, fontSize = 22.sp)
                    is FloorState.Done -> {
                        Text(stringResource(R.string.mic_floor_result, db(st.db)), color = Palette.Text, fontSize = 28.sp)
                        if (st.noisy) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.mic_floor_noisy), color = Palette.Warning, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(24.dp))
                        PrimaryButton(stringResource(R.string.floor_keep), onClick = { vm.keep(); onKept(vm.thenParticipant) })
                        Spacer(Modifier.height(12.dp))
                        OutlineButton(stringResource(R.string.floor_redo), onClick = vm::remeasure)
                    }
                    FloorState.Error -> {
                        Text(stringResource(R.string.mic_error), color = Palette.Warning, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(stringResource(R.string.floor_measure_button), onClick = vm::measure)
                    }
                }
            }
        }
    }
}
