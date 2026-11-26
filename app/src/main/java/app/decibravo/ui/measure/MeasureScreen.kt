package app.decibravo.ui.measure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.audio.MeasureState
import app.decibravo.scoring.Result
import app.decibravo.scoring.fmtScore
import app.decibravo.scoring.fmtSeconds
import app.decibravo.scoring.fmtStrength
import app.decibravo.ui.common.LevelBars
import app.decibravo.ui.common.OutlineButton
import app.decibravo.ui.common.PrimaryButton
import app.decibravo.ui.common.ScoreNumber
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.common.WithMicPermission
import app.decibravo.ui.theme.DigitFont
import app.decibravo.ui.theme.Palette


/** Спільна розмітка результату: екран заміру в стані Done і екран результату. */
@Composable
internal fun ResultBody(
    name: String, result: Result, placeLabel: String, endText: String, onBack: (() -> Unit)?,
    buttons: @Composable () -> Unit,
) {
    val digits = TextStyle(fontFeatureSettings = "tnum")
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(name, onBack) {
            Text(placeLabel, color = Palette.Accent, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            ScoreNumber(fmtScore(result.score))
            Text(stringResource(R.string.measure_score_label), color = Palette.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(fmtSeconds(result.durationSec) + " с", stringResource(R.string.measure_duration_label), digits)
                Stat(fmtStrength(result.strength), stringResource(R.string.measure_strength_label), digits)
            }
            Spacer(Modifier.height(16.dp))
            Text(endText, color = Palette.Text, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (result.overloaded) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.measure_overloaded), color = Palette.Warning, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
    }
}

@Composable
private fun Stat(value: String, label: String, digits: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Palette.Text, fontFamily = DigitFont, fontSize = 44.sp, style = digits)
        Text(label, color = Palette.Secondary, fontSize = 16.sp)
    }
}

@Composable
fun MeasureScreen(onExit: () -> Unit, vm: MeasureViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val view = LocalView.current
    var confirmDiscard by remember { mutableStateOf(false) }
    val running = ui.state is MeasureState.Running

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val activity = LocalContext.current as? android.app.Activity
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (activity?.isChangingConfigurations != true) vm.onActivityStopped() }
    LaunchedEffect(ui.accepted) { if (ui.accepted) onExit() }

    val handleBack: () -> Unit = {
        when (ui.state) {
            is MeasureState.Running -> { vm.cancel(); onExit() }
            is MeasureState.Done -> confirmDiscard = true
            else -> onExit()
        }
    }
    BackHandler(onBack = handleBack)

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        WithMicPermission {
            when (val st = ui.state) {
                is MeasureState.Done -> ResultBody(
                    ui.participantName, st.result, ui.place?.let { placeText(it) } ?: "",
                    endReasonText(st.result.reason, ui.quietEndSec), handleBack,
                ) {
                    PrimaryButton(stringResource(R.string.measure_accept), onClick = vm::accept, height = 72.dp, enabled = !ui.accepted)
                    OutlineButton(stringResource(R.string.measure_redo), onClick = vm::cancel, enabled = !ui.accepted)
                }
                is MeasureState.Failed -> Column(Modifier.fillMaxSize()) {
                    TopBar(ui.participantName, handleBack)
                    Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.measure_failed), color = Palette.Warning, fontSize = 20.sp)
                    }
                    Box(Modifier.padding(16.dp)) { PrimaryButton(stringResource(R.string.measure_back_to_table), onClick = onExit) }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    TopBar(ui.participantName, handleBack)
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        val score = (st as? MeasureState.Running)?.takeIf { !it.waiting }?.score
                        ScoreNumber(
                            if (score != null) fmtScore(score) else "0",
                            color = if (st is MeasureState.Ready) Palette.Secondary else Palette.Text,
                        )
                        Text(stringResource(R.string.measure_score_label), color = Palette.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(24.dp))
                        when {
                            st is MeasureState.Ready ->
                                Text(readyHint(ui.maxDurationSec, ui.quietEndSec), color = Palette.Secondary, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                            st is MeasureState.Running && st.waiting ->
                                Text(stringResource(R.string.measure_waiting), color = Palette.Text, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            st is MeasureState.Running -> {
                                LevelBars(st.bars, Modifier.padding(horizontal = 16.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.measure_progress, fmtSeconds(st.elapsedSec), ui.maxDurationSec), color = Palette.Secondary, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Box(Modifier.padding(16.dp)) {
                        if (running) OutlineButton(stringResource(R.string.measure_stop), onClick = vm::stop, height = 96.dp)
                        else PrimaryButton(stringResource(R.string.measure_start), onClick = vm::start, height = 96.dp)
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Palette.Bg,
            title = { Text(stringResource(R.string.measure_discard_title), color = Palette.Text) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.measure_discard), color = Palette.Warning) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel), color = Palette.Text) } },
        )
    }
}
