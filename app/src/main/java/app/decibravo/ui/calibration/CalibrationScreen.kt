package app.decibravo.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.decibravo.ui.theme.DigitFont
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onActivityStopped() }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(stringResource(R.string.calibration_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            WithMicPermission(compact = true) {
                MicCheckBlock(vm)
            }
            CalibrationBlock(vm)
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

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
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
            ScoreNumber(db(s.levelDb), fill = false)
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

private fun dec(v: Double) = String.format(java.util.Locale.US, "%.1f", v).replace('.', ',').replace('-', '−')

@Composable
private fun CalSlider(
    title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(valueText, color = Palette.Accent, fontFamily = DigitFont, fontSize = 28.sp, style = TextStyle(fontFeatureSettings = "tnum"))
    }
    Slider(
        value = value, onValueChange = onChange, valueRange = range, steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Line,
            activeTickColor = Palette.Bg, inactiveTickColor = Palette.Secondary,
        ),
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun CalibrationBlock(vm: CalibrationViewModel) {
    val c by vm.cal.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<PreviewRow?>(null) }
    val digits = TextStyle(fontFeatureSettings = "tnum")

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.calibration_title), color = Palette.Text, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        CalSlider(
            stringResource(R.string.cal_ref), stringResource(R.string.cal_ref_value, dec(c.draft.refDbfs)),
            c.draft.refDbfs.toFloat(), -60f..0f, 0,
        ) { v -> vm.setDraft { it.copy(refDbfs = (Math.round(v * 10) / 10.0)) } }
        CalSlider(
            stringResource(R.string.cal_gamma), dec(c.draft.gamma),
            c.draft.gamma.toFloat(), 0.5f..2.0f, 14,
        ) { v -> vm.setDraft { it.copy(gamma = Math.round(v * 10) / 10.0) } }
        CalSlider(
            stringResource(R.string.cal_threshold), stringResource(R.string.cal_threshold_value, c.draft.thresholdDb.roundToInt()),
            c.draft.thresholdDb.toFloat(), 3f..20f, 16,
        ) { v -> vm.setDraft { it.copy(thresholdDb = v.roundToInt().toDouble()) } }

        Spacer(Modifier.height(16.dp))
        if (c.samples.isEmpty()) {
            Text(stringResource(R.string.cal_no_samples), color = Palette.Secondary, fontSize = 18.sp)
        } else {
            Text(stringResource(R.string.cal_sample_title), color = Palette.Secondary, fontSize = 16.sp)
            c.samples.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.select(s.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.name, color = if (s.id == c.sampleId) Palette.Accent else Palette.Text, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Palette.Line)
            c.rows.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { target = r }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.place.toString(), color = if (r.place == 1) Palette.Accent else Palette.Text, fontFamily = DigitFont, fontSize = 32.sp,
                        style = digits, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(r.name, color = Palette.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.duration} с · сила ${r.strength}", color = Palette.Secondary, fontSize = 14.sp, maxLines = 1)
                    }
                    Text(r.score, color = Palette.Text, fontFamily = DigitFont, fontSize = 32.sp, style = digits)
                }
                HorizontalDivider(color = Palette.Line)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cal_preview_note), color = Palette.Secondary, fontSize = 14.sp)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.action_save), onClick = vm::save)
        if (c.saved) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cal_saved), color = Palette.Secondary, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlineButton(stringResource(R.string.cal_reset), onClick = vm::reset)
        Spacer(Modifier.height(24.dp))
    }

    target?.let { row ->
        TargetDialog(
            onDismiss = { target = null },
            onApply = { score -> vm.fitScale(row.participantId, score).also { if (it) target = null } },
        )
    }
}

@Composable
private fun TargetDialog(onDismiss: () -> Unit, onApply: (Double) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Bg,
        title = { Text(stringResource(R.string.cal_target_title), color = Palette.Text) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; failed = false }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                        focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Line, cursorColor = Palette.Accent,
                    ),
                )
                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.cal_target_fail), color = Palette.Warning, fontSize = 16.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = text.replace(',', '.').toDoubleOrNull()
                    failed = v == null || !onApply(v)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.action_ok), color = Palette.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel), color = Palette.Text)
            }
        },
    )
}
