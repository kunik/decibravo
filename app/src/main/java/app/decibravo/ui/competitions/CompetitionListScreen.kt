package app.decibravo.ui.competitions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.ui.common.PrimaryButton
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.edit.summaryLine
import app.decibravo.ui.theme.Palette

@Composable
fun CompetitionListScreen(
    onOpen: (String) -> Unit, onNew: () -> Unit, onCalibration: () -> Unit,
    vm: CompetitionListViewModel = viewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val calibrationLabel = stringResource(R.string.calibration_title)

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(stringResource(R.string.competitions_title), onBack = null) {
            IconButton(onClick = onCalibration, modifier = Modifier.semantics { contentDescription = calibrationLabel }) {
                Text("⚙", color = Palette.Text, fontSize = 26.sp)
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.competitions_empty), color = Palette.Secondary, fontSize = 18.sp,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(items, key = { it.id }) { c ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onOpen(c.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(c.name, color = Palette.Text, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(summaryLine(c), color = Palette.Secondary, fontSize = 16.sp)
                    }
                    HorizontalDivider(color = Palette.Line)
                }
            }
        }
        PrimaryButton(stringResource(R.string.competition_new), onClick = onNew, modifier = Modifier.padding(16.dp))
    }
}
