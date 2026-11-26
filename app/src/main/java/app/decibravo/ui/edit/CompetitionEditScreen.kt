package app.decibravo.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.data.Participant
import app.decibravo.data.parseParticipantNames
import app.decibravo.ui.common.OutlineButton
import app.decibravo.ui.common.PrimaryButton
import app.decibravo.ui.common.Stepper
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun seconds(v: Double): String = (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')) + " с"

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
    focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Line,
    focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.Secondary,
    cursorColor = Palette.Accent,
    focusedPlaceholderColor = Palette.Secondary, unfocusedPlaceholderColor = Palette.Secondary,
)

@Composable
fun CompetitionEditScreen(
    onBack: () -> Unit,
    onSaved: (id: String, isNew: Boolean) -> Unit,
    onMeasureFloor: (id: String) -> Unit,
    onDeleted: () -> Unit,
    vm: CompetitionEditViewModel = viewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val isNew = s.id == null
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Participant?>(null) }
    var removeTarget by remember { mutableStateOf<Participant?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    val form = s.form

    Column(Modifier.fillMaxSize().background(Palette.Bg).imePadding()) {
        TopBar(stringResource(if (isNew) R.string.competition_new else R.string.competition_settings), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name, onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.field_name)) }, singleLine = true,
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            if (s.locked) {
                Text(stringResource(R.string.participants_count_title, s.participants.size), color = Palette.Text, fontSize = 18.sp)
                s.participants.forEach { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = Palette.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { renameTarget = p }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.action_rename), color = Palette.Accent)
                        }
                        TextButton(onClick = { if (p.measurementId != null) removeTarget = p else vm.remove(p.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.action_remove), color = Palette.Warning)
                        }
                    }
                    HorizontalDivider(color = Palette.Line)
                }
                OutlineButton(stringResource(R.string.add_participants), onClick = { addDialog = true })
            } else {
                OutlinedTextField(
                    value = form.participantsText, onValueChange = { v -> vm.update { it.copy(participantsText = v) } },
                    label = { Text(stringResource(R.string.participants_count_title, parseParticipantNames(form.participantsText).size)) },
                    placeholder = { Text(stringResource(R.string.participants_hint)) },
                    minLines = 5, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(stringResource(R.string.stepper_max_title), color = Palette.Secondary, fontSize = 16.sp)
            Stepper(
                "${form.maxDurationSec} с", { vm.update { it.minusMax() } }, { vm.update { it.plusMax() } },
                "−", "+", enabled = !s.locked,
            )
            Text(stringResource(R.string.stepper_quiet_title), color = Palette.Secondary, fontSize = 16.sp)
            Stepper(
                seconds(form.quietEndSec), { vm.update { it.minusQuiet() } }, { vm.update { it.plusQuiet() } },
                "−", "+", enabled = !s.locked,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.floor_title), color = Palette.Text, fontSize = 18.sp)
                    val at = s.floorMeasuredAt
                    val text = if (at != null) stringResource(R.string.floor_measured_at, SimpleDateFormat("HH:mm", Locale.US).format(Date(at)))
                    else stringResource(R.string.floor_not_measured)
                    Text(text, color = Palette.Secondary, fontSize = 16.sp)
                }
                TextButton(
                    onClick = { vm.save()?.let(onMeasureFloor) },
                    enabled = !s.locked && form.canSave, modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.floor_measure), color = if (!s.locked && form.canSave) Palette.Accent else Palette.Secondary, fontSize = 18.sp) }
            }
            if (s.locked) Text(stringResource(R.string.locked_note), color = Palette.Warning, fontSize = 16.sp)

            if (!isNew) {
                OutlineButton(stringResource(R.string.competition_delete), onClick = { deleteDialog = true })
            }
            Spacer(Modifier.height(4.dp))
        }
        PrimaryButton(
            stringResource(if (isNew) R.string.action_create else R.string.action_save),
            onClick = { val wasNew = isNew; vm.save()?.let { onSaved(it, wasNew) } },
            enabled = form.canSave, modifier = Modifier.padding(16.dp),
        )
    }

    if (addDialog) TextDialog(
        title = stringResource(R.string.add_participants), initial = "", multiline = true,
        hint = stringResource(R.string.participants_hint),
        onDismiss = { addDialog = false }, onOk = { vm.addParticipants(it); addDialog = false },
    )
    renameTarget?.let { p ->
        TextDialog(
            title = stringResource(R.string.action_rename), initial = p.name, multiline = false, hint = null,
            onDismiss = { renameTarget = null }, onOk = { vm.rename(p.id, it); renameTarget = null },
        )
    }
    removeTarget?.let { p ->
        ConfirmDialog(
            text = stringResource(R.string.remove_participant_confirm, p.name),
            onDismiss = { removeTarget = null }, onOk = { vm.remove(p.id); removeTarget = null },
        )
    }
    if (deleteDialog) ConfirmDialog(
        text = stringResource(R.string.competition_delete_confirm),
        onDismiss = { deleteDialog = false }, onOk = { deleteDialog = false; vm.deleteCompetition(); onDeleted() },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, multiline: Boolean, hint: String?, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Bg,
        title = { Text(title, color = Palette.Text) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = !multiline, minLines = if (multiline) 5 else 1,
                placeholder = hint?.let { { Text(it) } }, colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onOk(text) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_ok), color = Palette.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel), color = Palette.Text) } },
    )
}

@Composable
private fun ConfirmDialog(text: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Bg,
        text = { Text(text, color = Palette.Text, fontSize = 18.sp) },
        confirmButton = { TextButton(onClick = onOk, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_ok), color = Palette.Warning) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel), color = Palette.Text) } },
    )
}
