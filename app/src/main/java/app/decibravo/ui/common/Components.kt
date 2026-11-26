package app.decibravo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.decibravo.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.decibravo.scoring.scoreScale
import app.decibravo.ui.theme.DigitFont
import app.decibravo.ui.theme.Palette

private val ButtonShape = RoundedCornerShape(8.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 68.dp, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Accent, contentColor = Palette.Bg,
            disabledContainerColor = Palette.Line, disabledContentColor = Palette.Secondary,
        ),
        modifier = modifier.fillMaxWidth().height(height),
    ) { Text(text, fontSize = if (height >= 90.dp) 30.sp else 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 64.dp, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(2.dp, if (enabled) Palette.Text else Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text, disabledContentColor = Palette.Secondary),
        modifier = modifier.fillMaxWidth().height(height),
    ) { Text(text, fontSize = if (height >= 90.dp) 30.sp else 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Stepper(
    valueText: String, onMinus: () -> Unit, onPlus: () -> Unit,
    minusLabel: String, plusLabel: String, enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = onMinus, enabled = enabled, shape = ButtonShape,
            border = BorderStroke(2.dp, if (enabled) Palette.Text else Palette.Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
            modifier = Modifier.size(56.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text(minusLabel, fontSize = 24.sp) }
        Text(valueText, color = Palette.Text, fontFamily = DigitFont, fontSize = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onPlus, enabled = enabled, shape = ButtonShape,
            border = BorderStroke(2.dp, if (enabled) Palette.Text else Palette.Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
            modifier = Modifier.size(56.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text(plusLabel, fontSize = 24.sp) }
    }
}

/**
 * Бал найбільшим шрифтом, що вміщається: три цифри займають майже всю ширину, для довших шрифт зменшується.
 * fill = false дає фіксований розмір (для допоміжних значень).
 */
@Composable
fun ScoreNumber(text: String, color: Color = Palette.Text, modifier: Modifier = Modifier, fill: Boolean = true) {
    val style = TextStyle(fontFamily = DigitFont, fontFeatureSettings = "tnum")
    if (!fill) {
        Text(
            text = text, color = color, fontFamily = DigitFont, fontSize = (160f * scoreScale(text)).sp,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip, style = style, modifier = modifier.fillMaxWidth(),
        )
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val probe = measurer.measure("000", style.copy(fontSize = 100.sp), maxLines = 1).size.width.coerceAtLeast(1)
        val threeDigitsPx = constraints.maxWidth * 0.92f
        // probe — ширина «000» у пікселях за розміру 100 sp, тож відношення дає розмір одразу в sp
        val threeDigitsSp = (100f * threeDigitsPx / probe).coerceAtMost(300f)
        val fontSize = (threeDigitsSp * scoreScale(text)).sp
        Text(
            text = text, color = color, fontFamily = DigitFont, fontSize = fontSize,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip, style = style, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** -1f = сіра крапка; 0…1 = жовтий стовпчик. */
@Composable
fun LevelBars(bars: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        if (bars.isEmpty()) return@Canvas
        val slot = size.width / bars.size
        val w = slot * 0.6f
        for ((i, v) in bars.withIndex()) {
            val x = i * slot + (slot - w) / 2f
            if (v < 0f) {
                drawRect(Palette.Secondary, Offset(x, size.height - w), Size(w, w))
            } else {
                val h = maxOf(w, v.coerceIn(0f, 1f) * size.height)
                drawRect(Palette.Accent, Offset(x, size.height - h), Size(w, h))
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, showMark: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val backLabel = stringResource(R.string.action_back)
            androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).width(56.dp).semantics { contentDescription = backLabel }) {
                Text("‹", color = Palette.Text, fontSize = 32.sp)
            }
        } else {
            Box(Modifier.width(12.dp))
        }
        if (showMark) {
            Image(painterResource(R.drawable.ic_mark), contentDescription = stringResource(R.string.mark_description), modifier = Modifier.size(32.dp))
            Box(Modifier.width(12.dp))
        }
        Text(title, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
