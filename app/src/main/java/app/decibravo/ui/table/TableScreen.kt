package app.decibravo.ui.table

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.decibravo.R
import app.decibravo.ui.common.OutlineButton
import app.decibravo.ui.common.TopBar
import app.decibravo.ui.theme.DigitFont
import app.decibravo.ui.theme.Palette

@Composable
fun TableScreen(
    competitionId: String,
    onBack: () -> Unit, onSettings: () -> Unit,
    onFloor: (participantId: String) -> Unit, onMeasure: (participantId: String) -> Unit, onResult: (participantId: String) -> Unit,
    vm: TableViewModel = viewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ui = s.ui
    val context = LocalContext.current
    val settingsLabel = stringResource(R.string.competition_settings)
    val copiedText = stringResource(R.string.table_copied)
    val digits = TextStyle(fontFeatureSettings = "tnum")

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TopBar(ui?.title ?: "", onBack) {
            IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = settingsLabel }) {
                Text("⚙", color = Palette.Text, fontSize = 26.sp)
            }
        }
        if (ui == null) return@Column
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text(ui.subtitle, color = Palette.Secondary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(ui.ranked, key = { "r" + it.participantId }) { r ->
                val overloadedLabel = stringResource(R.string.overloaded_description)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onResult(r.participantId) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.place.toString(), color = if (r.place == 1) Palette.Accent else Palette.Text, fontFamily = DigitFont, fontSize = 36.sp,
                        style = digits, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(r.name, color = Palette.Text, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.detail, color = Palette.Secondary, fontSize = 16.sp, maxLines = 1)
                    }
                    if (r.overloaded) {
                        Text("⚠", color = Palette.Warning, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = overloadedLabel })
                    }
                    Text(r.score, color = Palette.Text, fontFamily = DigitFont, fontSize = 36.sp, style = digits)
                }
                HorizontalDivider(color = Palette.Line)
            }
            if (ui.waiting.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.table_waiting), color = Palette.Secondary, fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(ui.waiting, key = { "w" + it.participantId }) { w ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable { if (s.floorMeasured) onMeasure(w.participantId) else onFloor(w.participantId) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("▶", color = Palette.Accent, fontSize = 22.sp)
                        Text(w.name, color = Palette.Text, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = Palette.Line)
                }
            }
            item {
                OutlineButton(
                    stringResource(R.string.table_copy),
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(ui.title, s.copy))
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
